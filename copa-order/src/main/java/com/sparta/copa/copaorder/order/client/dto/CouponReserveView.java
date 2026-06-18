package com.sparta.copa.copaorder.order.client.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 쿠폰 서비스 선점 응답 페이로드. discountAmount를 주문 할인액으로 반영한다.
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class CouponReserveView {

  private Long userCouponId;
  private Long orderId;
  private BigDecimal discountAmount;
}
