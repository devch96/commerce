package com.sparta.copa.copaorder.order.dto.request;

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
public class OrderLineRequest {

  @NotNull
  private Long productId;

  // 옵션 경로(옵션 없는 상품은 생략).
  private String optionKey;

  @NotNull
  @Min(1)
  private Integer quantity;
}
