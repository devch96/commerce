package com.sparta.copa.copapayment.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 결제 준비(ready) 요청. 카카오처럼 결제창 진입 전 서버 준비가 필요한 PG에서 사용.
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PgReadyRequest {

  @NotBlank
  private String orderId;

  @NotNull
  @Positive
  private Long amount;

  @NotBlank
  private String itemName;
}