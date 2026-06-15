package com.sparta.copa.copainventory.inventory.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 확정/해제 요청. 주문 단위로 보상한다.
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderReferenceRequest {

  @NotNull
  private Long orderId;
}
