package com.sparta.copa.copaorder.order.dto.request;

import com.sparta.copa.copaorder.common.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 어드민 배송 상태 변경.
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminStatusRequest {

  @NotNull
  private OrderStatus status;
}
