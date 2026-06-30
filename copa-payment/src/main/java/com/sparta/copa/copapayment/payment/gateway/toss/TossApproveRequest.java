package com.sparta.copa.copapayment.payment.gateway.toss;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TossApproveRequest {
  private String paymentKey;
  private String orderId;
  private Long amount;

}
