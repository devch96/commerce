package com.sparta.copa.copaproduct.search.indexer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.copa.copaproduct.outbox.OutboxRecorder;
import com.sparta.copa.copaproduct.product.event.ProductSearchEvent;
import com.sparta.copa.copaproduct.search.document.ProductDocument;
import com.sparta.copa.copaproduct.search.repository.ProductSearchDocumentRepository;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * product-search-events 구독 → Elasticsearch 색인기. outbox 릴레이가 실은 {@code eventType} 헤더로
 * upsert/delete를 라우팅한다. 릴레이는 at-least-once이므로 소비는 멱등이다
 * (save=id upsert, deleteById=존재 여부 무관).
 *
 * <p>테스트·브로커 부재 환경에서는 {@code copa.search.indexer.enabled=false}로 비활성화한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "copa.search.indexer.enabled", havingValue = "true", matchIfMissing = true)
public class ProductSearchIndexer {

  private static final String EVENT_TYPE_HEADER = "eventType";

  private final ObjectMapper objectMapper;
  private final ProductSearchDocumentRepository documentRepository;

  @KafkaListener(
      topics = OutboxRecorder.TOPIC_PRODUCT_SEARCH_EVENTS,
      groupId = "${copa.search.indexer.group-id:copa-product-search-indexer}")
  public void onMessage(@Payload String payload,
      @Header(name = EVENT_TYPE_HEADER, required = false) byte[] eventTypeHeader) {
    String eventType = eventTypeHeader == null
        ? "" : new String(eventTypeHeader, StandardCharsets.UTF_8);
    ProductSearchEvent event = deserialize(payload);
    if (event == null || event.getProductId() == null) {
      log.warn("검색 색인 이벤트 파싱 실패 또는 productId 누락 → 스킵");
      return;
    }

    if (ProductSearchEvent.EVENT_TYPE_DELETE.equals(eventType)) {
      documentRepository.deleteById(String.valueOf(event.getProductId()));
      log.debug("검색 색인 삭제: productId={}", event.getProductId());
    } else {
      documentRepository.save(ProductDocument.from(event));
      log.debug("검색 색인 upsert: productId={}", event.getProductId());
    }
  }

  private ProductSearchEvent deserialize(String payload) {
    try {
      return objectMapper.readValue(payload, ProductSearchEvent.class);
    } catch (Exception e) {
      // 파싱 불가 메시지는 무한 재시도가 되지 않도록 삼키고 로깅만 한다(포이즌 메시지 방지).
      log.error("검색 색인 이벤트 역직렬화 실패: {}", payload, e);
      return null;
    }
  }
}