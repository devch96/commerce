package com.sparta.copa.copaorder.common.enums;

import java.util.Set;

/**
 * 주문 상태머신(설계 06).
 * <pre>
 * ORDER_PLACED → PAYMENT_COMPLETED → SHIPPING_PENDING → IN_TRANSIT → DELIVERED
 *      └─(예약/결제 실패) CANCELLED
 *      취소/환불: CANCELLATION_REQUESTED→APPROVED, REFUND_REQUESTED→COMPLETED
 * </pre>
 */
public enum OrderStatus {
  ORDER_PLACED,
  // 주문 생성 후 결제 대기(재고·쿠폰 예약 완료, PG 결제창 진행 중). 결제 확정 시 PAYMENT_COMPLETED.
  PENDING_PAYMENT,
  PAYMENT_COMPLETED,
  SHIPPING_PENDING,
  IN_TRANSIT,
  DELIVERED,
  CANCELLED,
  CANCELLATION_REQUESTED,
  CANCELLATION_APPROVED,
  REFUND_REQUESTED,
  REFUND_COMPLETED;

  // 어드민 배송 진행 상태 전이(정방향만 허용).
  private static final Set<OrderStatus> SHIPPING_FLOW =
      Set.of(SHIPPING_PENDING, IN_TRANSIT, DELIVERED);

  public boolean isShippingFlow() {
    return SHIPPING_FLOW.contains(this);
  }

  // 배송 시작(IN_TRANSIT) 전이면 사용자가 취소 가능.
  public boolean isCancellableByUser() {
    return this == PAYMENT_COMPLETED || this == SHIPPING_PENDING;
  }
}
