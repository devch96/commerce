package com.sparta.copa.copacoupon.coupon.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * 주문 Saga의 쿠폰 선점 요청(내부 API). orderAmount=옵션 할인 반영 라인 합계.
 */
@Getter
public class CouponReserveRequest {

  @NotNull
  private Long userCouponId;

  @NotNull
  private Long userId;

  @NotNull
  private Long orderId;

  @NotNull
  @PositiveOrZero
  private BigDecimal orderAmount;
}
