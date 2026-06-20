package com.sparta.copa.copainventory.inventory.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Getter;

/**
 * 상품 서비스가 발행하는 상품 생성 이벤트(product-events). 재고 시드에 필요한 필드만 매핑한다.
 * 생성자 파라미터 이름으로 바인딩한다(Spring Boot가 -parameters 컴파일 + ParameterNamesModule 등록).
 * 모르는 필드(eventId/eventType/occurredAt 등)는 무시해 생산자 스키마 변화에 견딘다.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductCreatedEvent {

  private final Long productId;
  private final List<Item> items;

  public ProductCreatedEvent(Long productId, List<Item> items) {
    this.productId = productId;
    this.items = items;
  }

  @Getter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Item {

    private final String optionKey;
    private final int stock;

    public Item(String optionKey, int stock) {
      this.optionKey = optionKey;
      this.stock = stock;
    }
  }
}
