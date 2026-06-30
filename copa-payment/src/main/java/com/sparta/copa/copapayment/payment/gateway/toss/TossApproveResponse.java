package com.sparta.copa.copapayment.payment.gateway.toss;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TossApproveResponse {
  private String paymentKey;
  private String orderId;
  private String orderName;
  private String method;
  private Long totalAmount;
  private String status;
  private String approvedAt;
}
