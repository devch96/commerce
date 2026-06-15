package com.sparta.copa.copaproduct.product.dto.response;

import com.sparta.copa.copaproduct.product.domain.OptionPrice;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

/**
 * 특정 옵션(optionKey)의 가격/재고 해석 결과. 주문·재고 서비스가 소비하는 내부 응답.
 */
@Getter
@Builder
public class OptionPriceResponse {

  // 옵션 없는 상품은 빈 문자열("").
  private final String optionKey;
  // 선언적 초기 재고(권위 원천은 재고 서비스).
  private final int stock;
  private final BigDecimal originalPrice;
  // 옵션 할인 적용가(할인 없으면 originalPrice와 동일).
  private final BigDecimal finalPrice;

  public static OptionPriceResponse from(OptionPrice optionPrice) {
    return OptionPriceResponse.builder()
        .optionKey(optionPrice.getOptionKey())
        .stock(optionPrice.getStock())
        .originalPrice(optionPrice.getOriginalPrice())
        .finalPrice(optionPrice.getFinalPrice())
        .build();
  }
}
