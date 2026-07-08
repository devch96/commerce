package com.sparta.copa.copaorder.order.client.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 토스 결제 승인 요청(리다이렉트 후). amount는 서버가 계산한 payable(위변조 차단).
@Getter
@RequiredArgsConstructor
public class TossConfirmRequest {

  private final String orderId;
  private final Long amount;
  private final String paymentKey;
}