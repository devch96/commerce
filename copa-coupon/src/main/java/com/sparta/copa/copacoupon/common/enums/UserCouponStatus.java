package com.sparta.copa.copacoupon.common.enums;

/**
 * 발급된 사용자 쿠폰의 생명주기. ISSUED=사용 가능, RESERVED=주문에 선점됨, USED=사용 확정.
 * 주문 Saga: reserve(ISSUED→RESERVED) → use(RESERVED→USED) / release(RESERVED→ISSUED, 보상).
 */
public enum UserCouponStatus {
  ISSUED,
  RESERVED,
  USED
}
