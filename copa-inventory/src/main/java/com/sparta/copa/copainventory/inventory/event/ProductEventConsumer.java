package com.sparta.copa.copainventory.inventory.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.copa.copainventory.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 상품 생성 이벤트를 구독해 옵션 leaf별 재고를 시드한다.
 * eventType 분기는 KafkaConfig의 RecordFilterStrategy가 처리하므로(헤더 기반), 여기선 PRODUCT_CREATED만 도착한다.
 * outbox 릴레이는 at-least-once라 중복 전달이 가능하므로 seedIfAbsent로 멱등 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventConsumer {

  private final ObjectMapper objectMapper;
  private final InventoryService inventoryService;

  @KafkaListener(topics = "product-events", groupId = "copa-inventory",
      containerFactory = "productEventListenerContainerFactory")
  public void onProductCreated(String message) {
    ProductCreatedEvent event = parse(message);
    if (event == null || event.getProductId() == null || event.getItems() == null) {
      log.warn("상품 생성 이벤트 형식 오류, 건너뜀: {}", message);
      return;
    }
    for (ProductCreatedEvent.Item item : event.getItems()) {
      seed(event.getProductId(), item);
    }
  }

  // 동시 중복 소비로 유니크 제약(uk_inventory_product_option) 위반 시에도 멱등으로 흡수한다.
  private void seed(Long productId, ProductCreatedEvent.Item item) {
    try {
      inventoryService.seedIfAbsent(productId, item.getOptionKey(), item.getStock());
    } catch (DataIntegrityViolationException e) {
      log.debug("재고 시드 중복(이미 존재): productId={}, optionKey='{}'", productId, item.getOptionKey());
    }
  }

  private ProductCreatedEvent parse(String message) {
    try {
      return objectMapper.readValue(message, ProductCreatedEvent.class);
    } catch (Exception e) {
      log.warn("상품 생성 이벤트 역직렬화 실패, 건너뜀: {}", message, e);
      return null;
    }
  }
}
