package com.sparta.copa.copaorder.order.dto.request;

import com.sparta.copa.copaorder.common.enums.PgProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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

  // 결제할 PG(카카오면 주문 생성 시 ready 호출로 결제창 URL을 발급받는다).
  @NotNull
  private PgProvider pgProvider;

  // 결제창에 표시할 주문명(카카오 ready item_name 등). 미지정 시 서버 기본값 사용.
  private String orderName;
}
