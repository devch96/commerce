package com.sparta.copa.copaorder.order.service;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 주문 생성 시점에 상품 서비스로 가격을 스냅샷한 품목 라인.
@Getter
@RequiredArgsConstructor
public class PricedLine {

  private final Long productId;
  private final String optionKey;
  private final int quantity;
  private final BigDecimal price;
}
