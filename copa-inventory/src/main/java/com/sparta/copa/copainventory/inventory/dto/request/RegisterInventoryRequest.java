package com.sparta.copa.copainventory.inventory.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 재고 시드/보정. 상품 옵션 leaf를 (productId, optionKey) 기준 재고로 등록한다.
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegisterInventoryRequest {

  @NotNull
  private Long productId;

  private String optionKey;

  @NotNull
  @PositiveOrZero
  private Integer stock;
}
