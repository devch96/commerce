package com.sparta.copa.copaorder.order.client.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 카카오 결제 준비 요청(tid 발급 + 결제창 URL). 금액은 원(₩) 단위 Long.
@Getter
@RequiredArgsConstructor
public class KakaoReadyRequest {

  private final String orderId;
  private final Long amount;
  private final String itemName;
}