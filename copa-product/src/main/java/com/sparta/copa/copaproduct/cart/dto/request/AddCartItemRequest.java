package com.sparta.copa.copaproduct.cart.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AddCartItemRequest {

  @NotNull
  private Long productId;

  // 선택한 옵션 경로(예: 색상:네이비/사이즈:M). 옵션 없는 상품은 생략.
  private String optionKey;

  @NotNull
  @Min(1)
  private Integer quantity;
}
