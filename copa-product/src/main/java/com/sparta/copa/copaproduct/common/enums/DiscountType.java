package com.sparta.copa.copaproduct.common.enums;

// 옵션 할인 종류. 설계 08의 fixed_amount/percentage에 대응.
public enum DiscountType {
  // 정액 할인: 가격에서 value(원)만큼 차감.
  AMOUNT,
  // 정률 할인: 가격에서 value(%)만큼 차감(0~100).
  RATE
}