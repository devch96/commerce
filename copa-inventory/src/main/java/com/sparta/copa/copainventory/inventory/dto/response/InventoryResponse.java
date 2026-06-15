package com.sparta.copa.copainventory.inventory.dto.response;

import com.sparta.copa.copainventory.inventory.domain.Inventory;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InventoryResponse {

  private final Long productId;
  private final String optionKey;
  private final Integer stock;

  public static InventoryResponse from(Inventory inventory) {
    return InventoryResponse.builder()
        .productId(inventory.getProductId())
        .optionKey(inventory.getOptionKey())
        .stock(inventory.getStock())
        .build();
  }
}
