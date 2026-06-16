package com.sparta.copa.copaorder.order.client.feign;

import com.sparta.copa.copaorder.order.client.dto.ApiEnvelope;
import com.sparta.copa.copaorder.order.client.dto.OptionPriceView;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

// 상품 서비스 내부 API(OpenFeign). 옵션 경로별 현재 재고/할인가 조회.
@FeignClient(name = "product", url = "${copa.clients.product}")
public interface ProductFeignClient {

  @GetMapping("/internal/products/{productId}/option-price")
  ApiEnvelope<OptionPriceView> getOptionPrice(@PathVariable("productId") Long productId,
      @RequestParam(value = "optionKey", required = false) String optionKey);
}
