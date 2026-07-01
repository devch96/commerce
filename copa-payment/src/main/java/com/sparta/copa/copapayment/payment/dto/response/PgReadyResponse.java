package com.sparta.copa.copapayment.payment.dto.response;

import lombok.Getter;

// 결제 준비 결과(카카오). 프론트가 이 redirectUrl로 사용자를 카카오 결제창에 진입시킨다.
@Getter
public class PgReadyResponse {

  private final String tid;
  private final String redirectUrl;

  private PgReadyResponse(String tid, String redirectUrl) {
    this.tid = tid;
    this.redirectUrl = redirectUrl;
  }

  public static PgReadyResponse of(String tid, String redirectUrl) {
    return new PgReadyResponse(tid, redirectUrl);
  }
}