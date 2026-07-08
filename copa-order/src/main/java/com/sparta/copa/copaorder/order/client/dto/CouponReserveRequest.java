package com.sparta.copa.copaorder.order.client.dto;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 쿠폰 선점 요청(검증 + 할인 계산). orderAmount는 옵션 할인 적용 후 주문 총액.
@Getter
@RequiredArgsConstructor
public class CouponReserveRequest {

  private final Long userCouponId;
  private final Long userId;
  private final String orderId;
  private final BigDecimal orderAmount;
}