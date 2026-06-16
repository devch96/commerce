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

    boolean reserved = false;
    boolean paid = false;
    try {
      // 3. 재고 예약(결제 전). 부족하면 OUT_OF_STOCK → 보상.
      inventoryClient.reserve(orderId, toReserveLines(lines));
      reserved = true;

      // 4. 결제. 승인 실패면 PAYMENT_FAILED.
      PaymentView payment = paymentClient.pay(orderId, userId, order.payableAmount());
      if (!payment.isApproved()) {
        throw new BusinessException(ErrorCode.PAYMENT_FAILED);
      }
      paid = true;

      // 5. 재고 확정 + 주문 완료.
      inventoryClient.confirm(orderId);
      commandService.markPaymentCompleted(orderId);
    } catch (BusinessException e) {
      compensate(orderId, reserved, paid, e);
      throw e;
    }
    return queryService.getOwnedOrder(orderId, userId);
  }

  // 실패 보상: 결제했으면 결제 취소, 예약했으면 해제, 주문은 취소로 마감(모두 best-effort + 멱등).
  private void compensate(Long orderId, boolean reserved, boolean paid, BusinessException cause) {
    if (paid) {
      safe(() -> paymentClient.cancel(orderId), orderId, "결제 취소");
    }
    if (reserved) {
      safe(() -> inventoryClient.release(orderId), orderId, "재고 예약 해제");
    }
    safe(() -> commandService.markCancelled(orderId, "주문 실패: " + cause.getErrorCode().name()),
        orderId, "주문 취소");
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
    paymentClient.cancel(orderId);
    inventoryClient.restore(orderId);
    commandService.markCancelled(orderId, "사용자 취소");
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
