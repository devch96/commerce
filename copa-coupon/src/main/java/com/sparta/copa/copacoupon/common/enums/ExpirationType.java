package com.sparta.copa.copacoupon.common.enums;

/**
 * 유효기간 산정 방식.
 * CREATED_PLUS_DAYS=쿠폰 생성일+validDays, ISSUED_PLUS_DAYS=발급일+validDays, FIXED_RANGE=startDate~endDate 고정.
 */
public enum ExpirationType {
  CREATED_PLUS_DAYS,
  ISSUED_PLUS_DAYS,
  FIXED_RANGE
}
