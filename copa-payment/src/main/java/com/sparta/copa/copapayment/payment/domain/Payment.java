package com.sparta.copa.copapayment.payment.domain;

import com.sparta.copa.copapayment.common.enums.PaymentStatus;
import com.sparta.copa.copapayment.common.exception.BusinessException;
import com.sparta.copa.copapayment.common.exception.ErrorCode;
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
 * 주문 1건당 결제 1건(orderId 유니크 → 멱등성 기준). 가상 PG 승인/취소 결과를 보관한다.
 */
@Entity
@Getter
@Table(name = "payments")
@EntityListeners(AuditingEntityListener.class)
@DynamicInsert
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_id", nullable = false, unique = true)
  private Long orderId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentStatus status;

  // PG 거래 식별자(승인 시 발급).
  @Column(name = "pg_transaction_id", length = 100)
  private String pgTransactionId;

  // 누적 환불액(부분 환불 대비).
  @Column(name = "refunded_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal refundedAmount;

  @CreatedDate
  @Column(updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  private LocalDateTime updatedAt;

  @Builder
  private Payment(Long orderId, Long userId, BigDecimal amount, PaymentStatus status) {
    this.orderId = orderId;
    this.userId = userId;
    this.amount = amount;
    this.status = status;
    this.refundedAmount = BigDecimal.ZERO;
  }

  public static Payment request(Long orderId, Long userId, BigDecimal amount) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new BusinessException(ErrorCode.INVALID_AMOUNT);
    }
    return Payment.builder()
        .orderId(orderId)
        .userId(userId)
        .amount(amount)
        .status(PaymentStatus.REQUESTED)
        .build();
  }

  public void approve(String pgTransactionId) {
    this.status = PaymentStatus.APPROVED;
    this.pgTransactionId = pgTransactionId;
  }

  public void fail() {
    this.status = PaymentStatus.FAILED;
  }

  // 결제 취소(보상): 전액 환불 처리하고 CANCELLED로 둔다.
  public void cancel() {
    if (status != PaymentStatus.APPROVED) {
      throw new BusinessException(ErrorCode.INVALID_PAYMENT_STATE);
    }
    this.refundedAmount = this.amount;
    this.status = PaymentStatus.CANCELLED;
  }

  public boolean isApproved() {
    return status == PaymentStatus.APPROVED;
  }
}
