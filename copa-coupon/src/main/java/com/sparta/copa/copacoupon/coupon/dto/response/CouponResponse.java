package com.sparta.copa.copacoupon.coupon.dto.response;

import com.sparta.copa.copacoupon.common.enums.CouponStatus;
import com.sparta.copa.copacoupon.common.enums.CouponType;
import com.sparta.copa.copacoupon.common.enums.ExpirationType;
import com.sparta.copa.copacoupon.common.enums.TargetType;
import com.sparta.copa.copacoupon.coupon.domain.Coupon;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CouponResponse {

  private final Long id;
  private final String name;
  private final CouponType type;
  private final BigDecimal value;
  private final BigDecimal maxDiscount;
  private final BigDecimal minOrderAmount;
  private final ExpirationType expirationType;
  private final Integer validDays;
  private final LocalDateTime startDate;
  private final LocalDateTime endDate;
  private final Integer totalQuantity;
  private final Integer issuedQuantity;
  private final TargetType targetType;
  private final CouponStatus status;

  public static CouponResponse from(Coupon coupon) {
    return CouponResponse.builder()
        .id(coupon.getId())
        .name(coupon.getName())
        .type(coupon.getType())
        .value(coupon.getValue())
        .maxDiscount(coupon.getMaxDiscount())
        .minOrderAmount(coupon.getMinOrderAmount())
        .expirationType(coupon.getExpirationType())
        .validDays(coupon.getValidDays())
        .startDate(coupon.getStartDate())
        .endDate(coupon.getEndDate())
        .totalQuantity(coupon.getTotalQuantity())
        .issuedQuantity(coupon.getIssuedQuantity())
        .targetType(coupon.getTargetType())
        .status(coupon.getStatus())
        .build();
  }
}
