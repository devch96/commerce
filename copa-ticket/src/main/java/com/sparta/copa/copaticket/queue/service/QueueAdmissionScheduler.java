package com.sparta.copa.copaticket.queue.service;

import static com.sparta.copa.copaticket.common.redis.TicketRedisKeys.entryKeyPrefix;
import static com.sparta.copa.copaticket.common.redis.TicketRedisKeys.queueKey;

import com.sparta.copa.copaticket.common.enums.EventStatus;
import com.sparta.copa.copaticket.event.domain.TicketEvent;
import com.sparta.copa.copaticket.event.repository.TicketEventRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 대기열 입장 스케줄러. OPEN 이벤트마다 주기적으로 대기열 상위 N명을 원자적으로 pop해
 * 입장 허가 키(TTL)를 부여한다(queue_admit.lua). 배치 크기·주기가 발권 유입의 유량 제어 밸브다.
 *
 * <p>테스트·브로커 부재 환경에서는 {@code copa.ticket.queue.scheduler.enabled=false}로 비활성화한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "copa.ticket.queue.scheduler.enabled", havingValue = "true",
    matchIfMissing = true)
public class QueueAdmissionScheduler {

  @Value("${copa.ticket.queue.admit-batch-size:100}")
  private int admitBatchSize;

  @Value("${copa.ticket.queue.entry-ttl-seconds:300}")
  private long entryTtlSeconds;

  private final StringRedisTemplate redisTemplate;
  private final RedisScript<Long> queueAdmitScript;
  private final TicketEventRepository eventRepository;

  @Scheduled(fixedDelayString = "${copa.ticket.queue.admit-interval-ms:1000}")
  public void admit() {
    for (TicketEvent event : eventRepository.findByStatus(EventStatus.OPEN)) {
      try {
        Long admitted = redisTemplate.execute(queueAdmitScript,
            List.of(queueKey(event.getId())),
            String.valueOf(admitBatchSize), String.valueOf(entryTtlSeconds),
            entryKeyPrefix(event.getId()));
        if (admitted != null && admitted > 0) {
          log.info("대기열 입장 허가: eventId={}, admitted={}", event.getId(), admitted);
        }
      } catch (Exception e) {
        // 한 이벤트의 실패가 다른 이벤트 입장까지 막지 않게 개별로 삼키고 다음 주기에 재시도한다.
        log.error("대기열 입장 처리 실패: eventId={}", event.getId(), e);
      }
    }
  }
}