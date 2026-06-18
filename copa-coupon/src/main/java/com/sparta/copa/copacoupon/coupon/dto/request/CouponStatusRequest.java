package com.sparta.copa.copacoupon.coupon.dto.request;

import com.sparta.copa.copacoupon.common.enums.CouponStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class CouponStatusRequest {

  @NotNull
  private CouponStatus status;
}
