package com.sparta.copa.copaorder.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 주문서가 모은 품목으로 주문 생성(장바구니는 상품 서비스 소관이라 클라이언트가 품목을 전달).
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreateOrderRequest {

  @NotEmpty
  @Valid
  private List<OrderLineRequest> items;

  // 적용 쿠폰(선택, 프로모션 서비스 연동 시 사용).
  private Long couponId;
}
