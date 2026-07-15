package com.sparta.copa.copaticket.ticket.service;

import static com.sparta.copa.copaticket.common.redis.TicketRedisKeys.entryKey;
import static com.sparta.copa.copaticket.common.redis.TicketRedisKeys.issuedKey;
import static com.sparta.copa.copaticket.common.redis.TicketRedisKeys.queueKey;
import static com.sparta.copa.copaticket.common.redis.TicketRedisKeys.stockKey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.copa.copaticket.common.exception.BusinessException;
import com.sparta.copa.copaticket.common.exception.ErrorCode;
import com.sparta.copa.copaticket.event.domain.TicketEvent;
import com.sparta.copa.copaticket.event.service.TicketEventService;
import com.sparta.copa.copaticket.ticket.dto.response.TicketIssueResponse;
import com.sparta.copa.copaticket.ticket.event.TicketIssuedEvent;
import com.sparta.copa.copaticket.ticket.support.TicketNoGenerator;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * 선착순 발권의 1차 관문(source of truth = Redis). 선착순 쿠폰(FcfsCouponService)과 같은 골격에
 * 대기열 입장 검증이 더해진다.
 *
 * <p>발권 흐름: (1) 관리자 오픈이 좌석을 Redis에 시드({@link #open}) → (2) 사용자는 대기열을 거쳐
 * 입장 허가(entry 키)를 받은 뒤 Lua 원자 발권(입장 검증 + 좌석 차감 + 1인 1매 + 입장권 소모) →
 * (3) 통과분만 Kafka {@code ticket-issued}로 발행 → (4) 컨슈머가 DB에 적재.
 * DB 락에 트래픽이 몰리는 병목을 없애고, DB의 (event_id,user_id) 유니크가 최종 방어선이 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketIssueService {

  public static final String TOPIC_TICKET_ISSUED = "ticket-issued";
  private static final long PUBLISH_TIMEOUT_SECONDS = 5;

  // Lua 반환 코드.
  private static final long RESULT_SUCCESS = 1;
  private static final long RESULT_SOLD_OUT = -1;
  private static final long RESULT_ALREADY_ISSUED = -2;
  private static final long RESULT_NOT_OPEN = -3;
  private static final long RESULT_NOT_ADMITTED = -4;

  // 입장 허가 TTL. 보상(발행 실패) 시 입장권을 같은 TTL로 되살려 사용자가 대기열 재진입 없이 재시도하게 한다.
  @Value("${copa.ticket.queue.entry-ttl-seconds:300}")
  private long entryTtlSeconds;

  private final StringRedisTemplate redisTemplate;
  private final RedisScript<Long> ticketIssueScript;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;
  private final TicketEventService eventService;
  private final TicketService ticketService;
  private final TicketNoGenerator ticketNoGenerator;

  /**
   * 예매 오픈(관리자): 상태 전이(OPEN) 후 잔여 좌석을 Redis에 시드한다.
   * 재오픈 시 발권자 집합은 보존하고 재고는 "총 좌석 - 이미 발권된 수"로 다시 계산해
   * 중복 발권·초과 시드를 막는다.
   */
  public long open(Long eventId) {
    TicketEvent event = eventService.markOpen(eventId);
    Long issuedCount = redisTemplate.opsForSet().size(issuedKey(eventId));
    long stock = Math.max(0, event.getTotalSeats() - (issuedCount == null ? 0 : issuedCount));
    redisTemplate.opsForValue().set(stockKey(eventId), String.valueOf(stock));
    log.info("예매 오픈: eventId={}, seededStock={}", eventId, stock);
    return stock;
  }

  /**
   * 예매 마감(관리자): 상태 전이(CLOSED) 후 재고 키를 지워 발권 Lua가 -3(미오픈)을 반환하게 하고,
   * 남은 대기열도 비운다. 입장 허가 키는 TTL로 자연 소멸한다.
   */
  public void close(Long eventId) {
    eventService.markClosed(eventId);
    redisTemplate.delete(stockKey(eventId));
    redisTemplate.delete(queueKey(eventId));
    log.info("예매 마감: eventId={}", eventId);
  }

  /**
   * 선착순 발권. Lua 원자 연산으로 입장 허가·1인 1매·좌석 차감을 통제하고, 성공분만 Kafka로 발행한다.
   * 발행이 실패하면 Redis 효과(좌석·발권자·입장권)를 되돌려 재시도 가능한 상태로 남긴다.
   */
  public TicketIssueResponse issue(Long eventId, Long userId) {
    Long raw = redisTemplate.execute(ticketIssueScript,
        List.of(stockKey(eventId), issuedKey(eventId), entryKey(eventId, userId)),
        String.valueOf(userId));
    long code = raw == null ? RESULT_SOLD_OUT : raw;

    if (code == RESULT_NOT_OPEN) {
      throw new BusinessException(ErrorCode.EVENT_NOT_OPEN);
    }
    if (code == RESULT_ALREADY_ISSUED) {
      throw new BusinessException(ErrorCode.TICKET_ALREADY_ISSUED);
    }
    if (code == RESULT_NOT_ADMITTED) {
      throw new BusinessException(ErrorCode.TICKET_NOT_ADMITTED);
    }
    if (code == RESULT_SOLD_OUT) {
      throw new BusinessException(ErrorCode.TICKET_SOLD_OUT);
    }
    if (code != RESULT_SUCCESS) {
      throw new BusinessException(ErrorCode.TICKET_PUBLISH_FAILED);
    }

    String ticketNo = publishOrCompensate(eventId, userId);
    return TicketIssueResponse.accepted(eventId, userId, ticketNo, remainingStock(eventId));
  }

  /**
   * 사용자 취소: DB 전이(TicketService, 멱등 아님 — 중복 취소는 409) 성공 후 Redis를 복원한다.
   * 발권자 집합에서 빼서 재예매를 허용하고, 아직 오픈 중이면 좌석을 풀에 되돌린다.
   * Redis 복원이 실패해도 좌석이 "팔린 채"로 남을 뿐 초과 판매 방향이 아니므로 로깅만 한다.
   */
  public void cancel(String ticketNo, Long userId) {
    Long eventId = ticketService.cancel(ticketNo, userId);
    try {
      redisTemplate.opsForSet().remove(issuedKey(eventId), String.valueOf(userId));
      if (Boolean.TRUE.equals(redisTemplate.hasKey(stockKey(eventId)))) {
        redisTemplate.opsForValue().increment(stockKey(eventId));
      }
    } catch (Exception e) {
      log.error("취소 좌석 Redis 복원 실패(좌석 1석 잠김 가능, 대사로 정정): eventId={}, userId={}",
          eventId, userId, e);
    }
  }

  // 성공분 발행(ticketNo를 여기서 확정해 이벤트에 싣는다 — 재배달에도 같은 번호로 멱등).
  // 발행 실패 시 Redis 발권 효과를 롤백하고 입장권을 되살려 사용자가 재시도할 수 있게 한다.
  private String publishOrCompensate(Long eventId, Long userId) {
    String ticketNo = ticketNoGenerator.generate();
    try {
      TicketIssuedEvent event = new TicketIssuedEvent(
          UUID.randomUUID().toString(), eventId, userId, ticketNo, LocalDateTime.now());
      String payload = objectMapper.writeValueAsString(event);
      kafkaTemplate.send(TOPIC_TICKET_ISSUED, String.valueOf(eventId), payload)
          .get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      return ticketNo;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      compensate(eventId, userId);
      throw new BusinessException(ErrorCode.TICKET_PUBLISH_FAILED);
    } catch (Exception e) {
      log.error("발권 이벤트 발행 실패, Redis 보상 수행: eventId={}, userId={}", eventId, userId, e);
      compensate(eventId, userId);
      throw new BusinessException(ErrorCode.TICKET_PUBLISH_FAILED);
    }
  }

  // 발행 실패 보상: 발권자 집합 제거 + 좌석 원복 + 입장권 재부여(대기열 재진입 없이 재시도 가능).
  private void compensate(Long eventId, Long userId) {
    try {
      redisTemplate.opsForSet().remove(issuedKey(eventId), String.valueOf(userId));
      redisTemplate.opsForValue().increment(stockKey(eventId));
      redisTemplate.opsForValue()
          .set(entryKey(eventId, userId), "1", Duration.ofSeconds(entryTtlSeconds));
    } catch (Exception e) {
      // 보상까지 실패하면 좌석이 한 석 잠긴다(초과 판매는 아님). 대사(reconciliation)로 정정한다.
      log.error("발권 보상 실패(좌석 1석 잠김 가능): eventId={}, userId={}", eventId, userId, e);
    }
  }

  private long remainingStock(Long eventId) {
    String value = redisTemplate.opsForValue().get(stockKey(eventId));
    return value == null ? 0 : Long.parseLong(value);
  }
}