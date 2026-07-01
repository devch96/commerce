package com.sparta.copa.copaorder.order.dto.request;

import com.sparta.copa.copaorder.common.enums.PgProvider;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결제 확정(Phase 2) 요청. PG 결제창 리다이렉트 후 프론트가 토큰을 담아 호출한다.
 * 결제 금액은 서버가 저장한 주문 payable을 신뢰 원천으로 쓰므로 클라이언트 금액은 받지 않는다.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConfirmPaymentRequest {

  @NotNull
  private PgProvider pgProvider;

  // 토스: 결제창 인증 후 전달되는 paymentKey.
  private String paymentKey;

  // 카카오: 결제창 승인 후 리다이렉트로 전달되는 pg_token.
  private String pgToken;
}