package com.sparta.copa.copaorder.order.client.feign;

import com.sparta.copa.copaorder.order.client.dto.ApiEnvelope;
import com.sparta.copa.copaorder.order.client.dto.CouponReserveView;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

// 쿠폰 서비스 내부 API(OpenFeign). 선점/사용 확정/해제/복원.
@FeignClient(name = "coupon", url = "${copa.clients.coupon}")
public interface CouponFeignClient {

  @PostMapping("/internal/coupons/reserve")
  ApiEnvelope<CouponReserveView> reserve(@RequestBody Map<String, Object> body);

  @PostMapping("/internal/coupons/confirm")
  void confirm(@RequestBody Map<String, Object> body);

  @PostMapping("/internal/coupons/release")
  void release(@RequestBody Map<String, Object> body);

  @PostMapping("/internal/coupons/restore")
  void restore(@RequestBody Map<String, Object> body);
}
