package com.sparta.copa.copainventory.inventory.config;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.listener.adapter.RecordFilterStrategy;

/**
 * `product-events` 단일 토픽에서 재고가 관심 있는 eventType(PRODUCT_CREATED)만 통과시키는 필터.
 * payload를 역직렬화하지 않고 `eventType` 헤더만 읽어 라우팅한다. 헤더가 없으면 폐기 대상으로 본다.
 */
public class ProductEventRecordFilter implements RecordFilterStrategy<String, String> {

  public static final String EVENT_TYPE_HEADER = "eventType";
  private static final String PRODUCT_CREATED = "PRODUCT_CREATED";

  // 반환값 true = 폐기(리스너에 전달하지 않음).
  @Override
  public boolean filter(ConsumerRecord<String, String> record) {
    return !PRODUCT_CREATED.equals(eventType(record));
  }

  private static String eventType(ConsumerRecord<String, String> record) {
    Header header = record.headers().lastHeader(EVENT_TYPE_HEADER);
    return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
  }
}
