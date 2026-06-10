package com.sparta.copa.copaproduct.product.dto.request;

import com.sparta.copa.copaproduct.common.enums.ProductStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
  private Long price;

  @NotEmpty
  private List<String> categoryIds;

  @NotNull
  @PositiveOrZero
  private Integer stockQuantity;

  @Size(max = 2000)
  private String description;

  private Map<String, String> specs;

  // null이면 상태는 변경하지 않는다.
  private ProductStatus status;
}
