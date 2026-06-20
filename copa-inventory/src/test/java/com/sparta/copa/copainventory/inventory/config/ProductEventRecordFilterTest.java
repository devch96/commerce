package com.sparta.copa.copainventory.inventory.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 한 토픽(product-events)에서 eventType 헤더로 PRODUCT_CREATED만 통과시키는지 검증.
 * filter()==true는 폐기를 의미한다. 리스너가 테스트에서 꺼져 있어 검증되지 않는 필터 로직을 단위로 못박는다.
 */
class ProductEventRecordFilterTest {

  private final ProductEventRecordFilter filter = new ProductEventRecordFilter();

  private ConsumerRecord<String, String> recordWithHeader(String eventType) {
    ConsumerRecord<String, String> record =
        new ConsumerRecord<>("product-events", 0, 0L, "1", "{}");
    if (eventType != null) {
      record.headers().add(ProductEventRecordFilter.EVENT_TYPE_HEADER,
          eventType.getBytes(StandardCharsets.UTF_8));
    }
    return record;
  }

  @Test
  @DisplayName("PRODUCT_CREATED 헤더면 통과(폐기 안 함)")
  void keepsProductCreated() {
    assertThat(filter.filter(recordWithHeader("PRODUCT_CREATED"))).isFalse();
  }

  @Test
  @DisplayName("다른 eventType이면 폐기")
  void discardsOtherEventTypes() {
    assertThat(filter.filter(recordWithHeader("PRODUCT_UPDATED"))).isTrue();
  }

  @Test
  @DisplayName("eventType 헤더가 없으면 폐기")
  void discardsWhenHeaderMissing() {
    assertThat(filter.filter(recordWithHeader(null))).isTrue();
  }
}
