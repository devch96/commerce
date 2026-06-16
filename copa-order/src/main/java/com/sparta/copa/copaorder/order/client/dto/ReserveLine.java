package com.sparta.copa.copaorder.order.client.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 재고 예약 요청 라인(직렬화는 게터 기반).
@Getter
@RequiredArgsConstructor
public class ReserveLine {

  private final Long productId;
  private final String optionKey;
  private final int quantity;
}
