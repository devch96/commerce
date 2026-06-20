package com.sparta.copa.copaproduct.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.copa.copaproduct.common.exception.BusinessException;
import com.sparta.copa.copaproduct.common.exception.ErrorCode;
import com.sparta.copa.copaproduct.outbox.domain.OutboxEvent;
import com.sparta.copa.copaproduct.outbox.repository.OutboxEventRepository;
import com.sparta.copa.copaproduct.product.domain.Product;
import com.sparta.copa.copaproduct.product.domain.ProductOptions;
import com.sparta.copa.copaproduct.product.event.ProductCreatedEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 도메인 변경과 같은 트랜잭션에서 outbox 행을 적재한다(호출자의 트랜잭션에 참여).
 * payload는 JSON 문자열로 직렬화해 서비스 간 타입 결합을 피한다.
 */
@Component
@RequiredArgsConstructor
public class OutboxRecorder {

  public static final String TOPIC_PRODUCT_EVENTS = "product-events";
  private static final String AGGREGATE_TYPE = "PRODUCT";

  private final OutboxEventRepository outboxEventRepository;
  private final ObjectMapper objectMapper;

  // 상품 생성 → 재고 시드용 이벤트를 적재한다. 옵션이 있으면 leaf별, 없으면 단일 재고(optionKey="").
  public void recordProductCreated(Product product) {
    List<ProductCreatedEvent.Item> items = toItems(product);
    // payload는 도메인 데이터만. eventType은 outbox 컬럼 → Kafka 헤더가 라우팅의 단일 소스다(아래 OutboxEvent.of).
    ProductCreatedEvent event = new ProductCreatedEvent(
        UUID.randomUUID().toString(),
        product.getId(),
        LocalDateTime.now(),
        items);
    outboxEventRepository.save(OutboxEvent.of(
        AGGREGATE_TYPE,
        String.valueOf(product.getId()),
        ProductCreatedEvent.EVENT_TYPE,
        TOPIC_PRODUCT_EVENTS,
        String.valueOf(product.getId()),
        serialize(event)));
  }

  private List<ProductCreatedEvent.Item> toItems(Product product) {
    List<ProductCreatedEvent.Item> items = new ArrayList<>();
    Map<String, Integer> leaves = ProductOptions.flatten(product.getOptions());
    if (leaves.isEmpty()) {
      items.add(new ProductCreatedEvent.Item("", product.getStockQuantity()));
      return items;
    }
    for (Map.Entry<String, Integer> leaf : leaves.entrySet()) {
      items.add(new ProductCreatedEvent.Item(leaf.getKey(), leaf.getValue()));
    }
    return items;
  }

  private String serialize(ProductCreatedEvent event) {
    try {
      return objectMapper.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      throw new BusinessException(ErrorCode.OUTBOX_SERIALIZATION_FAILED);
    }
  }
}