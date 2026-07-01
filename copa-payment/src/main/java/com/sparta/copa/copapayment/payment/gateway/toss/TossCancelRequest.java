package com.sparta.copa.copapayment.payment.gateway.toss;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 토스 결제취소(/v1/payments/{paymentKey}/cancel) 요청. 전액 취소 시 cancelReason만 보낸다.
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TossCancelRequest {

  private String cancelReason;
}