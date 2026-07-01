package com.sparta.copa.copaorder.order.service;

import com.sparta.copa.copaorder.common.enums.OrderStatus;
import com.sparta.copa.copaorder.common.enums.PgProvider;
import com.sparta.copa.copaorder.common.exception.BusinessException;
import com.sparta.copa.copaorder.common.exception.ErrorCode;
import com.sparta.copa.copaorder.order.client.CouponClient;
import com.sparta.copa.copaorder.order.client.InventoryClient;
import com.sparta.copa.copaorder.order.client.PaymentClient;
import com.sparta.copa.copaorder.order.client.ProductClient;
import com.sparta.copa.copaorder.order.client.dto.OptionPriceView;
import com.sparta.copa.copaorder.order.client.dto.PaymentView;
import com.sparta.copa.copaorder.order.client.dto.PgReadyView;
import com.sparta.copa.copaorder.order.client.dto.ReserveLine;
import com.sparta.copa.copaorder.order.domain.Order;
import com.sparta.copa.copaorder.order.dto.request.ConfirmPaymentRequest;
import com.sparta.copa.copaorder.order.dto.request.CreateOrderRequest;
import com.sparta.copa.copaorder.order.dto.request.OrderLineRequest;
import com.sparta.copa.copaorder.order.dto.response.OrderCheckoutResponse;
import com.sparta.copa.copaorder.order.dto.response.OrderResponse;
import com.sparta.copa.copaorder.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 주문 Saga 오케스트레이터(동기, PG 결제창 방식). 결제가 리다이렉트를 사이에 두므로 2단계로 나뉜다.
 * <pre>
 * Phase 1 createOrder : 가격 스냅샷 → 주문(PENDING_PAYMENT) → 쿠폰·재고 예약 → (카카오)ready → 결제창 오픈 정보 반환
 * Phase 2 confirmPayment : 결제 승인 → (성공)재고 확정·쿠폰 사용·완료 / (실패)보상·취소
 * </pre>
 * DB 변경은 OrderCommandService(트랜잭션), 조회는 OrderQueryService(읽기)에 위임한다(self-invocation 회피).
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
  private final CouponClient couponClient;

  // 결제 승인 후 재고 확정 재시도 횟수(confirm은 멱등이라 안전하게 반복 가능). 정식 백오프/서킷브레이커는 추후 Resilience4j.
  private static final int CONFIRM_MAX_ATTEMPTS = 3;

  // ===== Phase 1: 주문 생성 + 예약 (+카카오 ready). 결제창 오픈 정보 반환 =====
  public OrderCheckoutResponse createOrder(Long userId, CreateOrderRequest request) {
    // 1. 상품 서비스로 옵션별 현재가를 받아 주문 시점 가격을 스냅샷.
    List<PricedLine> lines = new ArrayList<>();
    for (OrderLineRequest item : request.getItems()) {
      OptionPriceView priced = productClient.getOptionPrice(item.getProductId(), item.getOptionKey());
      lines.add(new PricedLine(item.getProductId(), normalize(item.getOptionKey()),
          item.getQuantity(), priced.getFinalPrice()));
    }

    // 2. 주문 생성(PENDING_PAYMENT). 이후 예약·ready는 보상 가능한 외부 호출.
    Order order = commandService.createPlacedOrder(userId, lines, request.getCouponId());
    Long orderId = order.getId();
    Long couponId = request.getCouponId();

    boolean couponReserved = false;
    boolean reserved = false;
    try {
      // 쿠폰 선점(검증 + 할인 계산). 옵션 할인 반영가(주문 총액) 기준으로 할인액을 받아 주문에 반영.
      BigDecimal discount = BigDecimal.ZERO;
      if (couponId != null) {
        discount = couponClient.reserve(couponId, userId, orderId, order.getTotalAmount());
        couponReserved = true;
        commandService.applyCouponDiscount(orderId, discount);
      }

      inventoryClient.reserve(orderId, toReserveLines(lines));
      reserved = true;

      BigDecimal payable = order.getTotalAmount().subtract(discount).max(BigDecimal.ZERO);
      String orderName = resolveOrderName(request, lines);

      // 카카오는 결제창 진입 전 서버 ready가 필요(tid·리다이렉트 URL 발급). 토스는 프론트 SDK가 처리.
      String redirectUrl = null;
      if (request.getPgProvider() == PgProvider.KAKAO) {
        PgReadyView ready = paymentClient.kakaoReady(orderId, userId, toWon(payable), orderName);
        redirectUrl = ready.getRedirectUrl();
      }
      return OrderCheckoutResponse.of(orderId, payable, orderName, request.getPgProvider(), redirectUrl);

    } catch (BusinessException e) {
      compensate(orderId, couponReserved, reserved, e);
      throw e;
    }
  }

  // ===== Phase 2: 결제 확정. 프론트가 PG 리다이렉트 후 토큰을 담아 호출 =====
  public OrderResponse confirmPayment(Long userId, Long orderId, ConfirmPaymentRequest request) {
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    if (!order.isOwnedBy(userId)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
    if (order.getStatus() == OrderStatus.PAYMENT_COMPLETED) {
      return queryService.getOwnedOrder(orderId, userId); // 멱등
    }
    if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }

    boolean couponReserved = order.getCouponId() != null;
    // 결제 금액은 서버가 저장한 payable을 신뢰 원천으로 사용(클라 금액 미신뢰). 위조 결제는 PG가 금액 불일치로 거절.
    Long payable = toWon(order.payableAmount());

    PaymentView payment;
    try {
      payment = switch (request.getPgProvider()) {
        case TOSS -> paymentClient.tossConfirm(orderId, userId, payable, request.getPaymentKey());
        case KAKAO -> paymentClient.kakaoConfirm(orderId, userId, request.getPgToken());
      };
    } catch (BusinessException e) {
      compensate(orderId, couponReserved, true, e);
      throw e;
    }

    if (!payment.isApproved()) {
      BusinessException failed = new BusinessException(ErrorCode.PAYMENT_FAILED);
      compensate(orderId, couponReserved, true, failed);
      throw failed;
    }

    // 결제 승인(캡처) 완료. 이후 재고 확정·쿠폰 사용·주문 완료는 되돌리지 않고 전진(roll-forward)으로 완결.
    completePaidOrder(orderId, couponReserved);
    return queryService.getOwnedOrder(orderId, userId);
  }

  // failUrl 처리: 사용자가 결제창에서 취소/실패로 돌아옴 → 예약 해제 + 주문 취소.
  public OrderResponse failPayment(Long userId, Long orderId) {
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    if (!order.isOwnedBy(userId)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
    if (order.getStatus() == OrderStatus.CANCELLED) {
      return queryService.getOwnedOrder(orderId, userId); // 멱등
    }
    if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }
    compensate(orderId, order.getCouponId() != null, true,
        new BusinessException(ErrorCode.PAYMENT_FAILED));
    return queryService.getOwnedOrder(orderId, userId);
  }

  // 결제 승인 전 실패 보상: 쿠폰·재고를 해제하고 주문은 취소로 마감(best-effort + 멱등). 결제는 미승인이라 환불 없음.
  private void compensate(Long orderId, boolean couponReserved, boolean reserved,
      BusinessException cause) {
    if (reserved) {
      safe(() -> inventoryClient.release(orderId), orderId, "재고 예약 해제");
    }
    if (couponReserved) {
      safe(() -> couponClient.release(orderId), orderId, "쿠폰 선점 해제");
    }
    safe(() -> commandService.markCancelled(orderId, "주문 실패: " + cause.getErrorCode().name()),
        orderId, "주문 취소");
  }

  // 결제 승인 후 마감(roll-forward): 재고 확정 + 쿠폰 사용(멱등 재시도) → 주문 완료. 실패해도 환불/해제하지 않고
  // 후속 복구가 재처리하도록 PENDING_PAYMENT로 남긴 채 실패를 드러낸다(결제는 이미 캡처됨).
  private void completePaidOrder(Long orderId, boolean couponUsed) {
    try {
      confirmWithRetry(orderId);
      if (couponUsed) {
        couponClient.confirm(orderId);
      }
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
    // 환불이 성공해야 취소가 성립하므로 결제 취소는 하드 콜. 이후 재고·쿠폰 복원·주문 마감은 멱등이라 best-effort.
    paymentClient.cancel(orderId);
    safe(() -> inventoryClient.restore(orderId), orderId, "재고 복원");
    if (order.getCouponId() != null) {
      safe(() -> couponClient.restore(orderId), orderId, "쿠폰 복원");
    }
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

  // 결제창 표시용 주문명. 지정이 없으면 품목 수 기반 기본값(카카오 item_name은 비어 있으면 안 됨).
  private String resolveOrderName(CreateOrderRequest request, List<PricedLine> lines) {
    if (request.getOrderName() != null && !request.getOrderName().isBlank()) {
      return request.getOrderName();
    }
    return lines.size() == 1 ? "주문 상품 1건" : "주문 상품 외 " + lines.size() + "건";
  }

  // BigDecimal(원) → PG 전달용 Long(원). 통화 규약상 소수는 없다.
  private Long toWon(BigDecimal amount) {
    return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
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