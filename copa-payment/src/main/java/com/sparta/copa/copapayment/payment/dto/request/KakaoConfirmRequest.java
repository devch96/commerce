package com.sparta.copa.copapayment.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 카카오 승인 요청. ready에서 저장한 tid·금액을 서버가 조회하므로 클라이언트는 pgToken만 넘긴다.
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KakaoConfirmRequest {

  @NotBlank
  private String orderId;

  // 카카오 결제창 승인 후 리다이렉트로 전달되는 pg_token.
  @NotBlank
  private String pgToken;
}