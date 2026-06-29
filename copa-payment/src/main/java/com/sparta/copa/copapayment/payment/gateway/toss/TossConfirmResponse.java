package com.sparta.copa.copapayment.payment.gateway.toss;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TossConfirmResponse {
  private String paymentKey;
  private String orderId;
  private String orderName;
  private String method;
  private Long totalAmount;
  private String status;
  private String approvedAt;
}
