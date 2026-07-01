package com.sparta.copa.copapayment.payment.gateway.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 카카오 결제준비 응답. tid와 결제창 리다이렉트 URL을 담는다.
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class KakaoReadyResponse {

  private String tid;
  private String nextRedirectPcUrl;
  private String nextRedirectMobileUrl;
  private String nextRedirectAppUrl;
  private String createdAt;

  // 결제창 리다이렉트 URL. 프론트 환경에 맞춰 PC/모바일 중 사용할 값을 고른다(여기선 PC 우선).
  public String redirectUrl() {
    return nextRedirectPcUrl != null ? nextRedirectPcUrl : nextRedirectMobileUrl;
  }
}