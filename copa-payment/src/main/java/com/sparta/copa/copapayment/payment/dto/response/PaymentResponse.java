package com.sparta.copa.copapayment.payment.dto.response;

import com.sparta.copa.copapayment.common.enums.PaymentStatus;
import com.sparta.copa.copapayment.payment.domain.Payment;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentResponse {

  private final String orderId;
  private final Long userId;
  private final Long amount;
  private final PaymentStatus status;
  private final String pgTransactionId;
  private final Long refundedAmount;

  public static PaymentResponse from(Payment payment) {
    return PaymentResponse.builder()
        .orderId(payment.getOrderId())
        .userId(payment.getUserId())
        .amount(payment.getAmount())
        .status(payment.getStatus())
        .pgTransactionId(payment.getPgTransactionId())
        .refundedAmount(payment.getRefundedAmount())
        .build();
  }
}
