package com.sparta.copa.copaorder.order.domain;

import com.sparta.copa.copaorder.common.enums.OrderStatus;
import com.sparta.copa.copaorder.common.enums.PgProvider;
import com.sparta.copa.copaorder.common.exception.BusinessException;
import com.sparta.copa.copaorder.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 주문 집계 루트. 품목은 .clauderules에 따라 컬렉션 대신 OrderItem(@ManyToOne 단방향) + 레포지토리로 다룬다.
 * 금액은 통화 규약대로 BigDecimal.
 */
@Entity
@Getter
@Table(name = "orders")
@EntityListeners(AuditingEntityListener.class)
@DynamicInsert
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 외부 노출용 주문번호(ORD-yyyyMMdd-XXXXXX). 순차 PK 노출로 인한 열거를 막고,
  // 클라이언트 API·서비스 간 Saga 참조(재고·쿠폰·결제)는 모두 이 값을 쓴다. PK는 내부 FK 전용.
  @Column(name = "order_no", nullable = false, unique = true, length = 30)
  private String orderNo;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  // 옵션 할인 반영 합계(품목 price 스냅샷의 합).
  @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal totalAmount;

  // 쿠폰 등 추가 할인(현재는 0, 프로모션 서비스 연동 시 채움).
  @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal discountAmount;

  @Column(name = "refunded_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal refundedAmount;

  // 적용 쿠폰(프로모션 서비스 소관, 현재는 자리만).
  @Column(name = "coupon_id")
  private Long couponId;

  // 주문 생성(Phase 1) 시 선택한 PG. Phase 2 confirm은 이 값과 대조해 교차 PG 호출을 차단한다.
  // (마이그레이션 이전 주문은 null일 수 있어 nullable)
  @Enumerated(EnumType.STRING)
  @Column(name = "pg_provider", length = 20)
  private PgProvider pgProvider;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private OrderStatus status;

  @CreatedDate
  @Column(updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  private LocalDateTime updatedAt;

  @Builder
  private Order(String orderNo, Long userId, BigDecimal totalAmount, BigDecimal discountAmount,
      Long couponId, PgProvider pgProvider, OrderStatus status) {
    this.orderNo = orderNo;
    this.userId = userId;
    this.totalAmount = totalAmount;
    this.discountAmount = discountAmount;
    this.refundedAmount = BigDecimal.ZERO;
    this.couponId = couponId;
    this.pgProvider = pgProvider;
    this.status = status;
  }

  public static Order place(String orderNo, Long userId, BigDecimal totalAmount,
      BigDecimal discountAmount, Long couponId, PgProvider pgProvider) {
    return Order.builder()
        .orderNo(orderNo)
        .userId(userId)
        .totalAmount(totalAmount)
        .discountAmount(discountAmount == null ? BigDecimal.ZERO : discountAmount)
        .couponId(couponId)
        .pgProvider(pgProvider)
        .status(OrderStatus.PENDING_PAYMENT)
        .build();
  }

  // 실제 결제 대상 금액.
  public BigDecimal payableAmount() {
    return totalAmount.subtract(discountAmount).max(BigDecimal.ZERO);
  }

  public boolean isOwnedBy(Long userId) {
    return this.userId != null && this.userId.equals(userId);
  }

  // 쿠폰 선점 후 확정된 할인액 반영(결제 전, PENDING_PAYMENT에서만).
  public void applyCouponDiscount(BigDecimal discount) {
    if (status != OrderStatus.PENDING_PAYMENT) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }
    BigDecimal resolved = discount == null ? BigDecimal.ZERO : discount;
    // 쿠폰 서비스 응답을 신뢰 경계로 보고 0 ≤ 할인 ≤ 주문총액을 강제(음수/총액 초과 할인으로 인한 0원·음수 결제 차단).
    if (resolved.signum() < 0 || resolved.compareTo(totalAmount) > 0) {
      throw new BusinessException(ErrorCode.COUPON_NOT_APPLICABLE);
    }
    this.discountAmount = resolved;
  }

  // 결제 성공 확정.
  public void markPaymentCompleted() {
    if (status != OrderStatus.PENDING_PAYMENT) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }
    this.status = OrderStatus.PAYMENT_COMPLETED;
  }

  // 예약/결제 실패 또는 사용자 취소 → 취소(보상은 서비스가 수행).
  public void cancel() {
    this.status = OrderStatus.CANCELLED;
  }

  // 어드민 배송 상태 전이(PAYMENT_COMPLETED→SHIPPING_PENDING→IN_TRANSIT→DELIVERED 정방향만).
  public void changeShippingStatus(OrderStatus next) {
    if (!next.isShippingFlow() || !isForwardShipping(next)) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }
    this.status = next;
  }

  private boolean isForwardShipping(OrderStatus next) {
    return switch (next) {
      case SHIPPING_PENDING -> status == OrderStatus.PAYMENT_COMPLETED;
      case IN_TRANSIT -> status == OrderStatus.SHIPPING_PENDING;
      case DELIVERED -> status == OrderStatus.IN_TRANSIT;
      default -> false;
    };
  }
}
