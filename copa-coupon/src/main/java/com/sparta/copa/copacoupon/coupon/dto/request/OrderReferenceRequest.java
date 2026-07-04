package com.sparta.copa.copacoupon.coupon.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

/**
 * 주문 단위 멱등 처리(confirm/release)용 참조. orderId 기준으로 선점된 쿠폰을 찾는다.
 */
@Getter
public class OrderReferenceRequest {

  @NotBlank
  private String orderId;
}
