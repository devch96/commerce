package com.sparta.copa.copaorder.order.client.feign;

import com.sparta.copa.copaorder.order.client.dto.ApiEnvelope;
import com.sparta.copa.copaorder.order.client.dto.PaymentView;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

// 결제 서비스 내부 API(OpenFeign). 결제 시도/취소.
@FeignClient(name = "payment", url = "${copa.clients.payment}")
public interface PaymentFeignClient {

  @PostMapping("/internal/payments")
  ApiEnvelope<PaymentView> pay(@RequestBody Map<String, Object> body);

  @PostMapping("/internal/payments/{orderId}/cancel")
  void cancel(@PathVariable("orderId") Long orderId);
}
