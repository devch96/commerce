package com.sparta.copa.copacoupon.coupon.dto.response;

import com.sparta.copa.copacoupon.common.enums.CouponType;
import com.sparta.copa.copacoupon.common.enums.UserCouponStatus;
import com.sparta.copa.copacoupon.coupon.domain.Coupon;
import com.sparta.copa.copacoupon.coupon.domain.UserCoupon;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserCouponResponse {

  private final Long userCouponId;
  private final Long couponId;
  private final String couponName;
  private final CouponType type;
  private final BigDecimal value;
  private final BigDecimal maxDiscount;
  private final BigDecimal minOrderAmount;
  private final UserCouponStatus status;
  private final LocalDateTime expiresAt;
  private final LocalDateTime issuedAt;

  public static UserCouponResponse from(UserCoupon userCoupon) {
    Coupon coupon = userCoupon.getCoupon();
    return UserCouponResponse.builder()
        .userCouponId(userCoupon.getId())
        .couponId(coupon.getId())
        .couponName(coupon.getName())
        .type(coupon.getType())
        .value(coupon.getValue())
        .maxDiscount(coupon.getMaxDiscount())
        .minOrderAmount(coupon.getMinOrderAmount())
        .status(userCoupon.getStatus())
        .expiresAt(userCoupon.getExpiresAt())
        .issuedAt(userCoupon.getIssuedAt())
        .build();
  }
}
