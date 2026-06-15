package com.sparta.copa.copaproduct.product.domain;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 특정 옵션 경로(optionKey)에 대한 해석 결과 — 선언적 재고와 (할인 반영) 가격.
 * 주문이 가격을 스냅샷하거나 재고 서비스가 시드할 때 쓰는 값.
 */
@Getter
@RequiredArgsConstructor
public class OptionPrice {

  // 옵션 없는 상품은 빈 문자열("").
  private final String optionKey;
  // 선언적 초기 재고(권위 원천은 재고 서비스). 옵션 없는 상품은 상품의 stockQuantity.
  private final int stock;
  private final BigDecimal originalPrice;
  // 옵션 할인 적용가(할인 없으면 originalPrice와 동일).
  private final BigDecimal finalPrice;
}
