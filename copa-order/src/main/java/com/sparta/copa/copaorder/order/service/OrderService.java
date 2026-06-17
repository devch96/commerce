package com.sparta.copa.copaorder.order.service;

import com.sparta.copa.copaorder.common.enums.OrderStatus;
import com.sparta.copa.copaorder.common.exception.BusinessException;
import com.sparta.copa.copaorder.common.exception.ErrorCode;
import com.sparta.copa.copaorder.order.client.InventoryClient;
import com.sparta.copa.copaorder.order.client.PaymentClient;
import com.sparta.copa.copaorder.order.client.ProductClient;
import com.sparta.copa.copaorder.order.client.dto.OptionPriceView;
import com.sparta.copa.copaorder.order.client.dto.PaymentView;
import com.sparta.copa.copaorder.order.client.dto.ReserveLine;
import com.sparta.copa.copaorder.order.domain.Order;
import com.sparta.copa.copaorder.order.dto.request.CreateOrderRequest;
import com.sparta.copa.copaorder.order.dto.request.OrderLineRequest;
import com.sparta.copa.copaorder.order.dto.response.OrderResponse;
import com.sparta.copa.copaorder.order.repository.OrderRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 주문 Saga 오케스트레이터(동기). 외부 호출(상품·재고·결제)을 순서대로 수행하고,
 * DB 변경은 OrderCommandService(트랜잭션), 조회는 OrderQueryService(읽기 트랜잭션)에 위임한다.
 * 트랜잭션 메서드를 별도 빈으로 둬 self-invocation으로 인한 트랜잭션 미적용을 피한다.
 *
 * <pre>가격 스냅샷 → 주문 생성 → 재고 예약 → 결제 → (성공)재고 확정·완료 / (실패)보상·취소</pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

  private final OrderCommandService commandService;
  private final OrderQueryService queryService;
  private final OrderRepository orderRepository;
  private final ProductClient productClient;
  private final InventoryClient inventoryClient;
  private final PaymentClient paymentClient;

  // 결제 승인 후 재고 확정 재시도 횟수(confirm은 멱등이라 안전하게 반복 가능). 정식 백오프/서킷브레이커는 추후 Resilience4j.
  private static final int CONFIRM_MAX_ATTEMPTS = 3;

  public OrderResponse createOrder(Long userId, CreateOrderRequest request) {
    // 1. 상품 서비스로 옵션별 현재가를 받아 주문 시점 가격을 스냅샷.
    List<PricedLine> lines = new ArrayList<>();
    for (OrderLineRequest item : request.getItems()) {
      OptionPriceView priced = productClient.getOptionPrice(item.getProductId(), item.getOptionKey());
      lines.add(new PricedLine(item.getProductId(), normalize(item.getOptionKey()),
          item.getQuantity(), priced.getFinalPrice()));
    }

    // 2. 주문 생성(ORDER_PLACED). 이후 단계는 보상 가능한 외부 호출.
    Order order = commandService.createPlacedOrder(userId, lines, request.getCouponId());
    Long orderId = order.getId();

    // 3~4. 결제 승인 전까지가 "보상(roll-back) 가능" 구간. 여기서 실패하면 예약 해제 + 주문 취소.
    boolean reserved = false;
    try {
      inventoryClient.reserve(orderId, toReserveLines(lines));
      reserved = true;

      PaymentView payment = paymentClient.pay(orderId, userId, order.payableAmount());
      if (!payment.isApproved()) {
        throw new BusinessException(ErrorCode.PAYMENT_FAILED);
      }
    } catch (BusinessException e) {
      compensate(orderId, reserved, e);
      throw e;
    }

    // 5. 결제 승인(캡처) 완료. 이후 재고 확정·주문 완료는 되돌리지 않고 전진(roll-forward)으로 완결한다.
    //    이미 받은 결제를 보상(환불·예약해제)으로 되돌리면 결제·재고 정합이 깨지므로 재시도로 마감한다.
    completePaidOrder(orderId);
    return queryService.getOwnedOrder(orderId, userId);
  }

  // 결제 승인 전 실패 보상: 예약했으면 해제, 주문은 취소로 마감(best-effort + 멱등). 결제는 미승인이라 환불 없음.
  private void compensate(Long orderId, boolean reserved, BusinessException cause) {
    if (reserved) {
      safe(() -> inventoryClient.release(orderId), orderId, "재고 예약 해제");
    }
    safe(() -> commandService.markCancelled(orderId, "주문 실패: " + cause.getErrorCode().name()),
        orderId, "주문 취소");
  }

  // 결제 승인 후 마감(roll-forward): 재고 확정(멱등 재시도) → 주문 완료. 실패해도 환불/해제하지 않고
  // 후속 복구가 재처리하도록 ORDER_PLACED로 남긴 채 실패를 드러낸다(결제는 이미 캡처됨).
  private void completePaidOrder(Long orderId) {
    try {
      confirmWithRetry(orderId);
      commandService.markPaymentCompleted(orderId);
    } catch (RuntimeException e) {
      log.error("결제 완료 후 주문 확정 실패 — 복구 필요 orderId={}", orderId, e);
      throw new BusinessException(ErrorCode.ORDER_COMPLETION_FAILED);
    }
  }

  private void confirmWithRetry(Long orderId) {
    RuntimeException last = null;
    for (int attempt = 1; attempt <= CONFIRM_MAX_ATTEMPTS; attempt++) {
      try {
        inventoryClient.confirm(orderId);
        return;
      } catch (RuntimeException e) {
        last = e;
        log.warn("재고 확정 재시도 {}/{} orderId={}", attempt, CONFIRM_MAX_ATTEMPTS, orderId);
      }
    }
    throw last;
  }

  public OrderResponse getOrder(Long orderId, Long userId) {
    return queryService.getOwnedOrder(orderId, userId);
  }

  public List<OrderResponse> getMyOrders(Long userId, OrderStatus status) {
    return queryService.getMyOrders(userId, status);
  }

  // 사용자 취소(배송 시작 전). 결제 환불 + 확정 재고 복원 + 주문 취소.
  public OrderResponse cancelOrder(Long orderId, Long userId) {
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    if (!order.isOwnedBy(userId)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
    if (!order.getStatus().isCancellableByUser()) {
      throw new BusinessException(ErrorCode.ORDER_NOT_CANCELLABLE);
    }
    // 환불이 성공해야 취소가 성립하므로 결제 취소는 하드 콜. 이후 재고 복원·주문 마감은 멱등이라 best-effort.
    paymentClient.cancel(orderId);
    safe(() -> inventoryClient.restore(orderId), orderId, "재고 복원");
    safe(() -> commandService.markCancelled(orderId, "사용자 취소"), orderId, "주문 취소");
    return queryService.getOwnedOrder(orderId, userId);
  }

  // 어드민 배송 상태 변경.
  public OrderResponse changeStatus(Long orderId, OrderStatus next) {
    commandService.changeShippingStatus(orderId, next);
    return queryService.getOrderResponse(orderId);
  }

  private List<ReserveLine> toReserveLines(List<PricedLine> lines) {
    List<ReserveLine> result = new ArrayList<>();
    for (PricedLine line : lines) {
      result.add(new ReserveLine(line.getProductId(), line.getOptionKey(), line.getQuantity()));
    }
    return result;
  }

  private void safe(Runnable action, Long orderId, String step) {
    try {
      action.run();
    } catch (RuntimeException e) {
      // 보상 실패는 로깅만(재고 TTL 스윕·재처리가 후속 안전망). 원래 실패 원인을 가리지 않는다.
      log.error("보상 실패({}) orderId={}", step, orderId, e);
    }
  }

  private String normalize(String optionKey) {
    return (optionKey == null || optionKey.isBlank()) ? "" : optionKey;
  }
}
