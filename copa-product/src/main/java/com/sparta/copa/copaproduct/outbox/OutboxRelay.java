package com.sparta.copa.copaproduct.outbox;

import com.sparta.copa.copaproduct.outbox.domain.OutboxEvent;
import com.sparta.copa.copaproduct.outbox.repository.OutboxEventRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional Outbox 릴레이. 미발행 이벤트를 주기적으로 폴링해 Kafka로 발행한 뒤 publishedAt을 채운다.
 * 발행 실패 시 트랜잭션이 롤백되어 다음 폴링에서 재시도된다(at-least-once → 소비자는 멱등해야 함).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "copa.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

  private static final int BATCH_SIZE = 100;
  private static final long SEND_TIMEOUT_SECONDS = 10;
  // 한 토픽에 여러 eventType이 흐를 때 소비자가 payload 파싱 없이 헤더만으로 라우팅하도록 eventType을 헤더로 싣는다.
  private static final String EVENT_TYPE_HEADER = "eventType";

  private final OutboxEventRepository outboxEventRepository;
  private final KafkaTemplate<String, String> kafkaTemplate;

  @Scheduled(fixedDelayString = "${copa.outbox.relay.poll-interval-ms:2000}")
  @Transactional
  public void publishPending() {
    List<OutboxEvent> batch = outboxEventRepository.findUnpublished(PageRequest.of(0, BATCH_SIZE));
    if (batch.isEmpty()) {
      return;
    }
    for (OutboxEvent event : batch) {
      send(event);
      event.markPublished();
    }
    log.debug("outbox 발행 완료: {}건", batch.size());
  }

  // 발행 결과를 동기로 기다려 성공한 이벤트만 published로 표시되도록 한다.
  private void send(OutboxEvent event) {
    try {
      Header eventType = new RecordHeader(
          EVENT_TYPE_HEADER, event.getEventType().getBytes(StandardCharsets.UTF_8));
      ProducerRecord<String, String> record = new ProducerRecord<>(
          event.getTopic(), null, event.getMessageKey(), event.getPayload(), List.of(eventType));
      kafkaTemplate.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("outbox 발행 중 인터럽트: id=" + event.getId(), e);
    } catch (Exception e) {
      // 트랜잭션 롤백 → 다음 폴링에서 재시도. 원인은 로깅으로 남긴다.
      throw new IllegalStateException("outbox 발행 실패: id=" + event.getId(), e);
    }
  }
}