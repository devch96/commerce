package com.sparta.copa.copaticket.queue.service;

import static com.sparta.copa.copaticket.common.redis.TicketRedisKeys.entryKey;
import static com.sparta.copa.copaticket.common.redis.TicketRedisKeys.issuedKey;
import static com.sparta.copa.copaticket.common.redis.TicketRedisKeys.queueKey;
import static com.sparta.copa.copaticket.common.redis.TicketRedisKeys.stockKey;

import com.sparta.copa.copaticket.common.exception.BusinessException;
import com.sparta.copa.copaticket.common.exception.ErrorCode;
import com.sparta.copa.copaticket.queue.dto.QueueStatusResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 가상 대기열(ZSET, score=진입 시각). 발권 핫패스 앞단에서 유입을 직렬화해
 * Redis 발권 연산조차 몰리지 않게 하는 유량 제어 장치다. 입장은 스케줄러가 배치로 허가한다.
 *
 * <p>진입·조회는 개별 Redis 연산으로 충분하다 — ZADD NX가 중복 진입을 막고,
 * 이미 발권한 사용자가 새치기로 들어와도 발권 Lua(1인 1매)가 최종 차단한다.
 */
@Service
public class QueueService {

  // 폴링 간격 하한/상한. 하한은 서버 보호(최단 주기 폭주 방지), 상한은 입장 놓침 방지.
  private static final long MIN_POLL_MS = 1000;
  private static final long MAX_POLL_MS = 10000;

  private final StringRedisTemplate redisTemplate;
  private final int admitBatchSize;
  private final long admitIntervalMs;

  public QueueService(StringRedisTemplate redisTemplate,
      @Value("${copa.ticket.queue.admit-batch-size:100}") int admitBatchSize,
      @Value("${copa.ticket.queue.admit-interval-ms:1000}") long admitIntervalMs) {
    this.redisTemplate = redisTemplate;
    this.admitBatchSize = admitBatchSize;
    this.admitIntervalMs = admitIntervalMs;
  }

  /**
   * 대기열 진입. 이미 입장 허가를 받았거나 대기 중이면 현재 상태를 그대로 반환한다(멱등).
   */
  public QueueStatusResponse enter(Long eventId, Long userId) {
    if (!Boolean.TRUE.equals(redisTemplate.hasKey(stockKey(eventId)))) {
      throw new BusinessException(ErrorCode.EVENT_NOT_OPEN);
    }
    if (Boolean.TRUE.equals(
        redisTemplate.opsForSet().isMember(issuedKey(eventId), String.valueOf(userId)))) {
      throw new BusinessException(ErrorCode.TICKET_ALREADY_ISSUED);
    }
    if (Boolean.TRUE.equals(redisTemplate.hasKey(entryKey(eventId, userId)))) {
      return QueueStatusResponse.admitted(eventId);
    }
    // NX: 이미 대기 중이면 score(순번)를 보존한다 — 재요청으로 뒤로 밀리지 않는다.
    redisTemplate.opsForZSet()
        .addIfAbsent(queueKey(eventId), String.valueOf(userId), System.currentTimeMillis());
    return waitingStatus(eventId, userId);
  }

  /**
   * 대기 상태 조회(폴링). 오류가 아닌 상태값으로 구분해 클라이언트가 분기한다.
   * WAITING이면 예상 대기 시간과 다음 폴링 간격(서버 지시)을 함께 내려준다.
   */
  public QueueStatusResponse status(Long eventId, Long userId) {
    if (Boolean.TRUE.equals(redisTemplate.hasKey(entryKey(eventId, userId)))) {
      return QueueStatusResponse.admitted(eventId);
    }
    Long rank = redisTemplate.opsForZSet().rank(queueKey(eventId), String.valueOf(userId));
    if (rank != null) {
      return waitingResponse(eventId, rank);
    }
    if (Boolean.TRUE.equals(
        redisTemplate.opsForSet().isMember(issuedKey(eventId), String.valueOf(userId)))) {
      return QueueStatusResponse.issued(eventId);
    }
    return QueueStatusResponse.notInQueue(eventId);
  }

  private QueueStatusResponse waitingStatus(Long eventId, Long userId) {
    Long rank = redisTemplate.opsForZSet().rank(queueKey(eventId), String.valueOf(userId));
    // ZADD 직후라 rank는 항상 존재하지만, 방어적으로 없으면 재조회 대신 미대기 상태를 준다.
    if (rank == null) {
      return QueueStatusResponse.notInQueue(eventId);
    }
    return waitingResponse(eventId, rank);
  }

  private QueueStatusResponse waitingResponse(Long eventId, long rank) {
    Long total = redisTemplate.opsForZSet().zCard(queueKey(eventId));
    long position = rank + 1;
    long estimatedWaitSeconds = estimateWaitSeconds(position);
    return QueueStatusResponse.waiting(eventId, position, total == null ? 0 : total,
        estimatedWaitSeconds, pollAfterMs(estimatedWaitSeconds));
  }

  // rank r(0-based)은 floor(r/batch)+1 번째 배치에 입장한다 → 대기 = ceil(position/batch) × 주기.
  private long estimateWaitSeconds(long position) {
    long batchesAhead = (position + admitBatchSize - 1) / admitBatchSize;
    return batchesAhead * admitIntervalMs / 1000;
  }

  // 예상 대기의 1/10 주기로 조회하되 [1s, 10s]로 클램프 — 순번이 멀수록 폴링을 드물게 지시한다.
  private long pollAfterMs(long estimatedWaitSeconds) {
    long proportional = estimatedWaitSeconds * 1000 / 10;
    return Math.max(MIN_POLL_MS, Math.min(MAX_POLL_MS, proportional));
  }
}