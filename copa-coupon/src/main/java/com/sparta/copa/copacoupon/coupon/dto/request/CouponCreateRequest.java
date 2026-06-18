package com.sparta.copa.copacoupon.coupon.dto.request;

import com.sparta.copa.copacoupon.common.enums.CouponType;
import com.sparta.copa.copacoupon.common.enums.ExpirationType;
import com.sparta.copa.copacoupon.common.enums.TargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * 쿠폰 정의 생성(ADMIN). 타입별/유효기간 방식별 필수 필드 정합성은 Coupon 도메인에서 최종 검증한다.
 */
@Getter
@Builder
public class CouponCreateRequest {

  @NotBlank
  private String name;

  @NotNull
  private CouponType type;

  @NotNull
  @Positive
  private BigDecimal value;

  private BigDecimal maxDiscount;

  @PositiveOrZero
  private BigDecimal minOrderAmount;

  @NotNull
  private ExpirationType expirationType;

  private Integer validDays;

  private LocalDateTime startDate;

  private LocalDateTime endDate;

  private Integer totalQuantity;

  private TargetType targetType;
}
