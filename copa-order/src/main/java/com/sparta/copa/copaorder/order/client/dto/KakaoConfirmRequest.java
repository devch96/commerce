package com.sparta.copa.copaorder.order.client.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 카카오 결제 승인 요청(리다이렉트 후). 금액은 결제 서비스가 ready 때 저장한 값을 신뢰한다.
@Getter
@RequiredArgsConstructor
public class KakaoConfirmRequest {

  private final String orderId;
  private final String pgToken;
}