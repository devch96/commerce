package com.sparta.copa.copaproduct.product.event;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;

/**
 * 상품 생성 이벤트(payload = 도메인 데이터). 재고 서비스가 옵션 leaf(optionKey)별 초기 재고를 시드하는 데 쓴다.
 * 옵션이 없는 상품은 items에 optionKey="" 단일 항목으로 담는다.
 * eventType은 payload가 아니라 outbox 컬럼 → Kafka 헤더로 싣는다(라우팅 단일 소스). EVENT_TYPE 상수는 그 헤더값에 쓴다.
 * 이 클래스는 발행 측에서 직렬화만 한다(역직렬화 없음) → Jackson은 getter로 직렬화하므로 생성자 힌트가 필요 없다.
 */
@Getter
public class ProductCreatedEvent {

  public static final String EVENT_TYPE = "PRODUCT_CREATED";

  private final String eventId;
  private final Long productId;
  private final LocalDateTime occurredAt;
  private final List<Item> items;

  public ProductCreatedEvent(String eventId, Long productId, LocalDateTime occurredAt,
      List<Item> items) {
    this.eventId = eventId;
    this.productId = productId;
    this.occurredAt = occurredAt;
    this.items = items;
  }

  @Getter
  public static class Item {

    private final String optionKey;
    private final int stock;

    public Item(String optionKey, int stock) {
      this.optionKey = optionKey;
      this.stock = stock;
    }
  }
}
