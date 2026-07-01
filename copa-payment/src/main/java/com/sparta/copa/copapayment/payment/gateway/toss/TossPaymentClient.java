package com.sparta.copa.copapayment.payment.gateway.toss;

import com.sparta.copa.copapayment.config.TossFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
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
  TossApproveResponse confirmPayment(
      @RequestBody TossApproveRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey // 멱등성 키 추가 (주문 ID 활용)
  );

  @PostMapping("/v1/payments/{paymentKey}/cancel")
  TossApproveResponse cancelPayment(
      @PathVariable("paymentKey") String paymentKey,
      @RequestBody TossCancelRequest request
  );
}
