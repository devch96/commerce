package com.sparta.copa.copainventory.inventory.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 주문 한 건의 예약 요청. 같은 orderId로 여러 품목을 한 번에(원자적으로) 예약한다.
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReserveRequest {

  @NotBlank
  private String orderId;

  @NotEmpty
  @Valid
  private List<ReserveItemRequest> items;
}
