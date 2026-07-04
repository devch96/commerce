package com.sparta.copa.copaorder.order.dto.response;

import com.sparta.copa.copaorder.common.enums.PgProvider;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

/**
 * 주문 생성(Phase 1) 결과. 프론트가 이 정보로 PG 결제창을 연다.
 * 토스는 orderNo·payableAmount·orderName으로 SDK를 띄우고, 카카오는 redirectUrl로 이동시킨다.
 */
@Getter
@Builder
public class OrderCheckoutResponse {

  private final String orderNo;
  private final BigDecimal payableAmount;
  private final String orderName;
  private final PgProvider pgProvider;
  // 카카오 결제창 리다이렉트 URL(토스는 null).
  private final String redirectUrl;

  public static OrderCheckoutResponse of(String orderNo, BigDecimal payableAmount, String orderName,
      PgProvider pgProvider, String redirectUrl) {
    return OrderCheckoutResponse.builder()
        .orderNo(orderNo)
        .payableAmount(payableAmount)
        .orderName(orderName)
        .pgProvider(pgProvider)
        .redirectUrl(redirectUrl)
        .build();
  }
}