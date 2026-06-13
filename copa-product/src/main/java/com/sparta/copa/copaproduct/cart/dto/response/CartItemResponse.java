package com.sparta.copa.copaproduct.cart.dto.response;

import com.sparta.copa.copaproduct.cart.domain.CartItem;
import com.sparta.copa.copaproduct.common.enums.ProductStatus;
import com.sparta.copa.copaproduct.product.domain.Product;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CartItemResponse {

  private final Long productId;
  private final String productName;
  private final BigDecimal price;
  private final String image;
  private final Integer quantity;
  private final ProductStatus status;
  // 현재 구매 가능 여부(판매중 + 재고>0). 주문 단계에서 다시 정밀 검증한다.
  private final boolean available;
  private final BigDecimal lineTotal;

  // 상품은 soft delete되어 항상 존재한다. 삭제·품절·비노출이면 available=false로 표시(정보는 보여줌).
  public static CartItemResponse from(CartItem item) {
    Product product = item.getProduct();
    String image = (product.getImages() == null || product.getImages().isEmpty())
        ? null : product.getImages().get(0);
    return CartItemResponse.builder()
        .productId(product.getId())
        .productName(product.getName())
        .price(product.getPrice())
        .image(image)
        .quantity(item.getQuantity())
        .status(product.getStatus())
        .available(product.isPurchasable())
        .lineTotal(product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
        .build();
  }
}
