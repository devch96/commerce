package com.sparta.copa.copapayment.payment.gateway.kakao;

import com.sparta.copa.copapayment.config.KakaoFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "kakaoPaymentClient",
    url = "https://open-api.kakaopay.com",
    configuration = KakaoFeignConfig.class
)
public interface KakaoPaymentClient {

  @PostMapping("/v1/payment/ready")
  KakaoReadyResponse ready(@RequestBody KakaoReadyRequest request);

  @PostMapping("/v1/payment/approve")
  KakaoApproveResponse approvePayment(@RequestBody KakaoApproveRequest request);

  @PostMapping("/v1/payment/cancel")
  void cancel(@RequestBody KakaoCancelRequest request);
}
