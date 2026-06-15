package com.sparta.copa.copaproduct.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.copa.copaproduct.common.enums.DiscountType;
import com.sparta.copa.copaproduct.common.exception.BusinessException;
import com.sparta.copa.copaproduct.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductOptionTest {

  // 색상:네이비/사이즈:M(10),L(5) + 색상:블랙/사이즈:M(20)
  private Map<String, Object> options() {
    return Map.of("색상", Map.of(
        "네이비", Map.of("사이즈", Map.of("M", 10, "L", 5)),
        "블랙", Map.of("사이즈", Map.of("M", 20))));
  }

  private Product optionProduct(List<OptionDiscount> discounts) {
    return Product.create(1L, "PROD-2026-opt", "옵션상품", BigDecimal.valueOf(1000),
        null, "설명", List.of(), Map.of(), options(), discounts);
  }

  @Test
  @DisplayName("옵션 leaf 합계가 상품 재고로 집계된다")
  void aggregatesStock() {
    Product product = optionProduct(null);
    assertThat(product.getStockQuantity()).isEqualTo(35);
  }

  @Test
  @DisplayName("옵션 경로로 선언적 재고와 기준가를 해석한다")
  void resolvesOption() {
    Product product = optionProduct(null);

    OptionPrice resolved = product.resolveOption("색상:네이비/사이즈:M");

    assertThat(resolved.getStock()).isEqualTo(10);
    assertThat(resolved.getFinalPrice()).isEqualByComparingTo("1000");
  }

  @Test
  @DisplayName("가장 구체적인(조합별) 할인이 단일 옵션 할인을 이긴다")
  void mostSpecificDiscountWins() {
    List<OptionDiscount> discounts = List.of(
        OptionDiscount.builder()
            .optionKey("색상:네이비").discountType(DiscountType.RATE).value(BigDecimal.valueOf(10))
            .build(),
        OptionDiscount.builder()
            .optionKey("색상:네이비/사이즈:L").discountType(DiscountType.AMOUNT)
            .value(BigDecimal.valueOf(300)).build());
    Product product = optionProduct(discounts);

    // M: 단일 옵션(네이비) 10% → 900
    assertThat(product.resolveOption("색상:네이비/사이즈:M").getFinalPrice())
        .isEqualByComparingTo("900");
    // L: 조합별 300원 할인이 더 구체적 → 700
    assertThat(product.resolveOption("색상:네이비/사이즈:L").getFinalPrice())
        .isEqualByComparingTo("700");
    // 블랙: 해당 할인 없음 → 기준가
    assertThat(product.resolveOption("색상:블랙/사이즈:M").getFinalPrice())
        .isEqualByComparingTo("1000");
  }

  @Test
  @DisplayName("존재하지 않는 옵션 경로는 OPTION_NOT_FOUND")
  void unknownOption() {
    Product product = optionProduct(null);

    assertThatThrownBy(() -> product.resolveOption("색상:레드/사이즈:M"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.OPTION_NOT_FOUND);
  }

  @Test
  @DisplayName("어떤 옵션 leaf에도 걸리지 않는 할인은 거부된다")
  void rejectsDanglingDiscount() {
    List<OptionDiscount> discounts = List.of(
        OptionDiscount.builder()
            .optionKey("색상:레드").discountType(DiscountType.RATE).value(BigDecimal.valueOf(10))
            .build());

    assertThatThrownBy(() -> optionProduct(discounts))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.INVALID_OPTION_DISCOUNT);
  }

  @Test
  @DisplayName("옵션 없는 상품에 옵션을 지정하면 OPTION_NOT_ALLOWED")
  void optionNotAllowedForSimpleProduct() {
    Product simple = Product.create(1L, "PROD-2026-s", "단순상품", BigDecimal.valueOf(1000),
        10, "설명", List.of(), Map.of(), null, null);

    assertThatThrownBy(() -> simple.resolveOption("색상:네이비"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.OPTION_NOT_ALLOWED);
  }
}
