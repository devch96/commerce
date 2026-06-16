package com.sparta.copa.copaorder.order.client.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 상품 서비스 /internal/products/{id}/option-price 응답 페이로드.
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class OptionPriceView {

  private String optionKey;
  private int stock;
  private BigDecimal originalPrice;
  private BigDecimal finalPrice;
}
