package com.sparta.copa.copapayment.payment.gateway.toss;

import com.sparta.copa.copapayment.config.TossFeignConfig;
import com.sparta.copa.copapayment.payment.dto.request.PaymentRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
    name = "tossPaymentClient",
    url = "https://api.tosspayments.com",
    configuration = TossFeignConfig.class
)
public interface TossPaymentClient {

  @PostMapping("/v1/payments/confirm")
  TossConfirmResponse confirmPayment(
      @RequestBody TossConfirmRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey // 멱등성 키 추가 (주문 ID 활용)
  );
}
