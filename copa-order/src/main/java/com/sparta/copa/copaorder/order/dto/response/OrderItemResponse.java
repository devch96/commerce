package com.sparta.copa.copaorder.order.dto.response;

import com.sparta.copa.copaorder.order.domain.OrderItem;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderItemResponse {

  private final Long productId;
  private final String optionKey;
  private final Integer quantity;
  private final BigDecimal price;
  private final BigDecimal lineTotal;

  public static OrderItemResponse from(OrderItem item) {
    return OrderItemResponse.builder()
        .productId(item.getProductId())
        .optionKey(item.getOptionKey())
        .quantity(item.getQuantity())
        .price(item.getPrice())
        .lineTotal(item.lineTotal())
        .build();
  }
}
