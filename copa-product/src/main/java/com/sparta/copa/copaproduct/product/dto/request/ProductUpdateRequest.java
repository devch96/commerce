package com.sparta.copa.copaproduct.product.dto.request;

import com.sparta.copa.copaproduct.common.enums.ProductStatus;
import com.sparta.copa.copaproduct.product.domain.OptionDiscount;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductUpdateRequest {

  @NotBlank
  @Size(max = 100)
  private String name;

  @NotNull
  @PositiveOrZero
  private BigDecimal price;

  @NotEmpty
  private List<Long> categoryIds;

  // 옵션 없는 단순 상품의 재고. 옵션이 있으면 leaf 합계로 집계되므로 생략 가능.
  @PositiveOrZero
  private Integer stockQuantity;

  @Size(max = 2000)
  private String description;

  // 상품 이미지 URL 목록(선택). 대표 이미지는 첫 번째 관례.
  private List<String> images;

  private Map<String, String> specs;

  // 옵션(무한 뎁스 JSON 트리). leaf = 선언적 초기 재고.
  private Map<String, Object> options;

  // 옵션별/조합별 할인 규칙(선택).
  private List<OptionDiscount> optionDiscounts;

  // null이면 상태는 변경하지 않는다.
  private ProductStatus status;
}
