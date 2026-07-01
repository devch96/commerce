package com.sparta.copa.copapayment.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 토스 승인 요청. 토스는 별도 ready가 없어 confirm 시점에 결제 레코드를 생성한다(금액 검증 포함).
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TossConfirmRequest {

  @NotBlank
  private String orderId;

  // 토스 결제창 인증 후 전달되는 paymentKey.
  @NotBlank
  private String paymentKey;

  @NotNull
  @Positive
  private Long amount;
}