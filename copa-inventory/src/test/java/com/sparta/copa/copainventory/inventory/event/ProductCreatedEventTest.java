package com.sparta.copa.copainventory.inventory.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

/**
 * 상품 생성 이벤트 역직렬화 검증. @JsonTest는 앱과 동일하게 구성된 ObjectMapper(ParameterNamesModule 포함)를 주입한다.
 * DTO에 @JsonCreator/@JsonProperty가 없어도 생성자 파라미터 이름으로 바인딩되는지, 모르는 필드를 무시하는지 확인한다.
 */
@JsonTest
class ProductCreatedEventTest {

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  @DisplayName("생성자 어노테이션 없이 파라미터 이름으로 역직렬화하고, 모르는 필드(eventId 등)는 무시한다")
  void deserializesByParameterNamesAndIgnoresUnknown() throws Exception {
    String json = """
        {
          "eventId": "11111111-2222-3333-4444-555555555555",
          "eventType": "PRODUCT_CREATED",
          "productId": 42,
          "occurredAt": "2026-06-20T11:00:00",
          "items": [
            {"optionKey": "색상:네이비/사이즈:M", "stock": 10},
            {"optionKey": "", "stock": 3}
          ]
        }
        """;

    ProductCreatedEvent event = objectMapper.readValue(json, ProductCreatedEvent.class);

    assertThat(event.getProductId()).isEqualTo(42L);
    assertThat(event.getItems()).hasSize(2);
    assertThat(event.getItems().get(0).getOptionKey()).isEqualTo("색상:네이비/사이즈:M");
    assertThat(event.getItems().get(0).getStock()).isEqualTo(10);
    assertThat(event.getItems().get(1).getOptionKey()).isEmpty();
    assertThat(event.getItems().get(1).getStock()).isEqualTo(3);
  }
}
