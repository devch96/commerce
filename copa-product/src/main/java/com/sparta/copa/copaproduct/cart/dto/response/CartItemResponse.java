package com.sparta.copa.copaproduct.cart.dto.response;

import com.sparta.copa.copaproduct.cart.domain.CartItem;
import com.sparta.copa.copaproduct.common.enums.ProductStatus;
import com.sparta.copa.copaproduct.common.exception.BusinessException;
import com.sparta.copa.copaproduct.product.domain.OptionPrice;
import com.sparta.copa.copaproduct.product.domain.Product;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CartItemResponse {

  private final Long productId;
  private final String productName;
  // 선택한 옵션 경로. 옵션 없는 상품은 빈 문자열("").
  private final String optionKey;
  // 옵션 할인을 반영한 단가(옵션 없으면 상품 기준가).
  private final BigDecimal price;
  private final String image;
  private final Integer quantity;
  private final ProductStatus status;
  // 현재 구매 가능 여부(판매중 + 해당 옵션 재고>0). 주문 단계에서 다시 정밀 검증한다.
  private final boolean available;
  private final BigDecimal lineTotal;

  // 상품은 soft delete되어 항상 존재한다. 삭제·품절·비노출·옵션 소멸이면 available=false로 표시(정보는 보여줌).
  public static CartItemResponse from(CartItem item) {
    Product product = item.getProduct();
    String image = (product.getImages() == null || product.getImages().isEmpty())
        ? null : product.getImages().get(0);

    // 가격은 항상 현재 상품 기준으로 재계산한다. 담은 뒤 옵션이 바뀌어 더 이상 유효하지 않으면 구매 불가로 표시.
    BigDecimal unitPrice = product.getPrice();
    boolean available = product.isPurchasable();
    try {
      OptionPrice optionPrice = product.resolveOption(item.getOptionKey());
      unitPrice = optionPrice.getFinalPrice();
      available = available && optionPrice.getStock() > 0;
    } catch (BusinessException e) {
      available = false;
    }

    return CartItemResponse.builder()
        .productId(product.getId())
        .productName(product.getName())
        .optionKey(item.getOptionKey())
        .price(unitPrice)
        .image(image)
        .quantity(item.getQuantity())
        .status(product.getStatus())
        .available(available)
        .lineTotal(unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())))
        .build();
  }
}
