package com.sparta.copa.copaproduct.product.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/**
 * 검색 색인 이벤트(payload). 상품 등록/수정 시 Elasticsearch 문서를 upsert하기 위한
 * 전문 검색 대상 필드를 싣는다. 삭제 시에는 {@code productId}만 채워 보내고 나머지는 null이다.
 *
 * <p>eventType(UPSERT/DELETE)은 payload가 아니라 outbox 컬럼 → Kafka 헤더로 싣는다(라우팅 단일 소스).
 * 발행 측 직렬화와 소비 측 역직렬화를 모두 지원하도록 빌더 기반({@code @Jacksonized})으로 둔다.
 */
@Getter
@Builder
@Jacksonized
public class ProductSearchEvent {

  public static final String EVENT_TYPE_UPSERT = "PRODUCT_UPSERTED";
  public static final String EVENT_TYPE_DELETE = "PRODUCT_DELETED";

  private final String eventId;
  private final Long productId;
  private final String productCode;
  private final Long sellerId;
  private final String name;
  private final String description;
  private final BigDecimal price;
  private final String status;
  private final Integer stockQuantity;
  private final List<Long> categoryIds;
  private final LocalDateTime createdAt;
  private final LocalDateTime occurredAt;
}