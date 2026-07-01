package com.sparta.copa.copaorder.order.client.feign;

import com.sparta.copa.copaorder.order.client.dto.ApiEnvelope;
import com.sparta.copa.copaorder.order.client.dto.PaymentView;
import com.sparta.copa.copaorder.order.client.dto.PgReadyView;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

// 결제 서비스 내부 API(OpenFeign). 게이트웨이를 거치지 않으므로 X-User-Id를 직접 전달한다.
@FeignClient(name = "payment", url = "${copa.clients.payment}")
public interface PaymentFeignClient {

  // 카카오 준비(결제창 진입 전).
  @PostMapping("/internal/payments/kakao/ready")
  ApiEnvelope<PgReadyView> kakaoReady(@RequestHeader("X-User-Id") Long userId,
      @RequestBody Map<String, Object> body);

  // 카카오 승인(리다이렉트 후).
  @PostMapping("/internal/payments/kakao/confirm")
  ApiEnvelope<PaymentView> kakaoConfirm(@RequestHeader("X-User-Id") Long userId,
      @RequestBody Map<String, Object> body);

  // 토스 승인(리다이렉트 후).
  @PostMapping("/internal/payments/toss/confirm")
  ApiEnvelope<PaymentView> tossConfirm(@RequestHeader("X-User-Id") Long userId,
      @RequestBody Map<String, Object> body);

  @PostMapping("/internal/payments/{orderId}/cancel")
  void cancel(@PathVariable("orderId") Long orderId);
}