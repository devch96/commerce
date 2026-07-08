package com.sparta.copa.copaorder.order.client.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// orderId(orderNo) 하나로 대상을 지정하는 내부 API 공통 요청(재고·쿠폰의 confirm/release/restore).
@Getter
@RequiredArgsConstructor
public class OrderRefRequest {

  private final String orderId;
}