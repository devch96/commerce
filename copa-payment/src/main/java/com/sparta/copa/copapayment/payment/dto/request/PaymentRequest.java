package com.sparta.copa.copapayment.payment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 주문 Saga가 재고 예약 성공 후 호출하는 결제 요청.
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentRequest {

  @NotNull
  private Long orderId;

  @NotNull
  private Long userId;

  @NotNull
  @Positive
  private BigDecimal amount;
}
