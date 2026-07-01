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
  private String orderId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  // 원(₩) 단위 정수 금액. DB 컬럼은 DECIMAL(19,2)이지만 소수는 쓰지 않는다.
  @Column(nullable = false)
  private Long amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentStatus status;

  // 결제를 처리한 PG(취소 시 어느 게이트웨이로 라우팅할지 결정).
  @Enumerated(EnumType.STRING)
  @Column(name = "pg_provider", length = 20)
  private PgProvider pgProvider;

  // PG 거래 식별자(승인 시 발급).
  @Column(name = "pg_transaction_id", length = 100)
  private String pgTransactionId;

  // 카카오 결제준비(ready)에서 발급되는 거래 ID. 승인·취소 시 사용.
  @Column(name = "tid", length = 100)
  private String tid;

  // 누적 환불액(부분 환불 대비).
  @Column(name = "refunded_amount", nullable = false)
  private Long refundedAmount;

  @CreatedDate
  @Column(updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  private LocalDateTime updatedAt;

  @Builder
  private Payment(String orderId, Long userId, Long amount, PaymentStatus status,
      PgProvider pgProvider) {
    this.orderId = orderId;
    this.userId = userId;
    this.amount = amount;
    this.status = status;
    this.pgProvider = pgProvider;
    this.refundedAmount = 0L;
  }

  public static Payment request(String orderId, Long userId, Long amount, PgProvider pgProvider) {
    if (amount == null || amount <= 0) {
      throw new BusinessException(ErrorCode.INVALID_AMOUNT);
    }
    return Payment.builder()
        .orderId(orderId)
        .userId(userId)
        .amount(amount)
        .pgProvider(pgProvider)
        .status(PaymentStatus.REQUESTED)
        .build();
  }

  // 카카오 ready 응답의 tid 저장.
  public void assignTid(String tid) {
    this.tid = tid;
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
