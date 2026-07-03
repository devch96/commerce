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
import com.sparta.copa.copaproduct.product.event.ProductSearchEvent;
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
  // 검색 색인 전용 토픽. 상품 등록/수정/삭제 → 검색 색인기(@KafkaListener)가 구독해 ES에 반영.
  public static final String TOPIC_PRODUCT_SEARCH_EVENTS = "product-search-events";
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

  // 상품 등록/수정 → 검색 색인(ES) upsert 이벤트. 전문 검색 대상 필드를 통째로 싣는다.
  // categoryIds는 서비스가 넘겨준다(Product가 카테고리 컬렉션을 들지 않으므로 조인 엔티티에서 조회한 값).
  public void recordProductUpserted(Product product, List<Long> categoryIds) {
    ProductSearchEvent event = ProductSearchEvent.builder()
        .eventId(UUID.randomUUID().toString())
        .productId(product.getId())
        .productCode(product.getProductCode())
        .sellerId(product.getSellerId())
        .name(product.getName())
        .description(product.getDescription())
        .price(product.getPrice())
        .status(product.getStatus().name())
        .stockQuantity(product.getStockQuantity())
        .categoryIds(categoryIds)
        .createdAt(product.getCreatedAt())
        .occurredAt(LocalDateTime.now())
        .build();
    outboxEventRepository.save(OutboxEvent.of(
        AGGREGATE_TYPE,
        String.valueOf(product.getId()),
        ProductSearchEvent.EVENT_TYPE_UPSERT,
        TOPIC_PRODUCT_SEARCH_EVENTS,
        String.valueOf(product.getId()),
        serialize(event)));
  }

  // 상품 soft delete → 검색 색인에서 문서 제거. payload에는 productId만 담는다.
  public void recordProductDeleted(Long productId) {
    ProductSearchEvent event = ProductSearchEvent.builder()
        .eventId(UUID.randomUUID().toString())
        .productId(productId)
        .occurredAt(LocalDateTime.now())
        .build();
    outboxEventRepository.save(OutboxEvent.of(
        AGGREGATE_TYPE,
        String.valueOf(productId),
        ProductSearchEvent.EVENT_TYPE_DELETE,
        TOPIC_PRODUCT_SEARCH_EVENTS,
        String.valueOf(productId),
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

  private String serialize(Object event) {
    try {
      return objectMapper.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      throw new BusinessException(ErrorCode.OUTBOX_SERIALIZATION_FAILED);
    }
  }
}