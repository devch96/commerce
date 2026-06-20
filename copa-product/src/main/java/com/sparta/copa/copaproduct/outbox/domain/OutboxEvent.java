package com.sparta.copa.copaproduct.outbox.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Transactional Outbox 행. 도메인 변경과 같은 트랜잭션에 적재되어, 별도 릴레이가 Kafka로 발행한다.
 * publishedAt이 채워지면 발행 완료다. payload는 직렬화된 JSON 문자열(서비스 간 타입 결합 회피).
 */
@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 50)
  private String aggregateType;

  @Column(nullable = false, length = 100)
  private String aggregateId;

  @Column(nullable = false, length = 50)
  private String eventType;

  @Column(nullable = false, length = 100)
  private String topic;

  @Column(length = 100)
  private String messageKey;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String payload;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  private LocalDateTime publishedAt;

  @Builder
  private OutboxEvent(String aggregateType, String aggregateId, String eventType, String topic,
      String messageKey, String payload, LocalDateTime createdAt) {
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.topic = topic;
    this.messageKey = messageKey;
    this.payload = payload;
    this.createdAt = createdAt;
  }

  // 미발행 이벤트로 적재한다(createdAt은 적재 시각, publishedAt은 발행 후 채운다).
  public static OutboxEvent of(String aggregateType, String aggregateId, String eventType,
      String topic, String messageKey, String payload) {
    return OutboxEvent.builder()
        .aggregateType(aggregateType)
        .aggregateId(aggregateId)
        .eventType(eventType)
        .topic(topic)
        .messageKey(messageKey)
        .payload(payload)
        .createdAt(LocalDateTime.now())
        .build();
  }

  public void markPublished() {
    this.publishedAt = LocalDateTime.now();
  }
}