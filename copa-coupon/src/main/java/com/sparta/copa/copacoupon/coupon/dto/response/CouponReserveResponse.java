package com.sparta.copa.copacoupon.coupon.dto.response;

import com.sparta.copa.copacoupon.coupon.domain.UserCoupon;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

/**
 * 쿠폰 선점 결과(내부 API). 주문은 discountAmount를 주문 할인액으로 반영한다.
 */
@Getter
@Builder
public class CouponReserveResponse {

  private final Long userCouponId;
  private final String orderId;
  private final BigDecimal discountAmount;

  public static CouponReserveResponse from(UserCoupon userCoupon) {
    return CouponReserveResponse.builder()
        .userCouponId(userCoupon.getId())
        .orderId(userCoupon.getReservedOrderId())
        .discountAmount(userCoupon.getDiscountAmount())
        .build();
  }
}
