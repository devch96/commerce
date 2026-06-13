package com.sparta.copa.copaproduct.cart.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CartResponse {

  private final List<CartItemResponse> items;
  private final int totalQuantity;
  // 구매 가능한 항목들의 합계 금액. (품절/삭제 항목은 제외)
  private final BigDecimal totalPrice;

  public static CartResponse of(List<CartItemResponse> items) {
    int totalQuantity = 0;
    BigDecimal totalPrice = BigDecimal.ZERO;
    for (CartItemResponse item : items) {
      totalQuantity += item.getQuantity();
      if (item.isAvailable() && item.getLineTotal() != null) {
        totalPrice = totalPrice.add(item.getLineTotal());
      }
    }
    return CartResponse.builder()
        .items(items)
        .totalQuantity(totalQuantity)
        .totalPrice(totalPrice)
        .build();
  }
}
