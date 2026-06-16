package com.sparta.copa.copaorder.order.client;

import com.sparta.copa.copaorder.common.exception.BusinessException;
import com.sparta.copa.copaorder.common.exception.ErrorCode;
import com.sparta.copa.copaorder.order.client.dto.ApiEnvelope;
import com.sparta.copa.copaorder.order.client.dto.OptionPriceView;
import com.sparta.copa.copaorder.order.client.feign.ProductFeignClient;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 상품 서비스 호출 어댑터 — OpenFeign 전송 위에 공통 응답 봉투 언랩 + 예외 변환을 얹는다.
@Component
@RequiredArgsConstructor
public class ProductClient {

  private final ProductFeignClient feign;

  public OptionPriceView getOptionPrice(Long productId, String optionKey) {
    try {
      ApiEnvelope<OptionPriceView> response = feign.getOptionPrice(productId, emptyToNull(optionKey));
      if (response == null || response.getData() == null) {
        throw new BusinessException(ErrorCode.DEPENDENT_SERVICE_ERROR);
      }
      return response.getData();
    } catch (FeignException e) {
      // 상품 없음·옵션 무효 등 4xx → 구매 불가로 매핑, 그 외(5xx·연결 실패)는 의존 서비스 오류.
      if (e.status() >= 400 && e.status() < 500) {
        throw new BusinessException(ErrorCode.PRODUCT_UNAVAILABLE);
      }
      throw new BusinessException(ErrorCode.DEPENDENT_SERVICE_ERROR);
    }
  }

  private static String emptyToNull(String value) {
    return (value == null || value.isBlank()) ? null : value;
  }
}
