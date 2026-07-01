package com.sparta.copa.copapayment.payment.gateway.kakao;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 카카오 결제취소(/v1/payment/cancel) 요청. tid로 승인 건을 지정해 취소한다.
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class KakaoCancelRequest {

  private String cid;
  private String tid;
  private Long cancelAmount;
  private Long cancelTaxFreeAmount;
}