package com.sparta.copa.copaticket.queue.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sparta.copa.copaticket.common.enums.QueueStatus;
import lombok.Getter;

/**
 * 대기열 상태 응답(폴링용). WAITING이면 순번과 함께 폴링 클라이언트에 필요한 서버 측 계산값을 준다:
 * ahead(내 앞 대기 수)·estimatedWaitSeconds(입장 속도 기반 예상 대기)·pollAfterMs(다음 폴링까지 서버 지시 간격).
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QueueStatusResponse {

  private final Long eventId;
  private final QueueStatus status;
  private final Long position;
  private final Long ahead;
  private final Long waitingTotal;
  // 입장 배치 크기·주기는 서버 설정이므로 예상 대기 시간은 서버가 계산해 내려준다.
  private final Long estimatedWaitSeconds;
  // 서버 지시 폴링 간격. 순번이 멀수록 길게 — 대기자 전원이 최단 주기로 폴링하는 부하를 서버가 통제한다.
  private final Long pollAfterMs;

  private QueueStatusResponse(Long eventId, QueueStatus status, Long position, Long ahead,
      Long waitingTotal, Long estimatedWaitSeconds, Long pollAfterMs) {
    this.eventId = eventId;
    this.status = status;
    this.position = position;
    this.ahead = ahead;
    this.waitingTotal = waitingTotal;
    this.estimatedWaitSeconds = estimatedWaitSeconds;
    this.pollAfterMs = pollAfterMs;
  }

  public static QueueStatusResponse waiting(Long eventId, long position, long waitingTotal,
      long estimatedWaitSeconds, long pollAfterMs) {
    return new QueueStatusResponse(eventId, QueueStatus.WAITING, position, position - 1,
        waitingTotal, estimatedWaitSeconds, pollAfterMs);
  }

  public static QueueStatusResponse admitted(Long eventId) {
    return new QueueStatusResponse(eventId, QueueStatus.ADMITTED, null, null, null, null, null);
  }

  public static QueueStatusResponse issued(Long eventId) {
    return new QueueStatusResponse(eventId, QueueStatus.ISSUED, null, null, null, null, null);
  }

  public static QueueStatusResponse notInQueue(Long eventId) {
    return new QueueStatusResponse(eventId, QueueStatus.NOT_IN_QUEUE, null, null, null, null, null);
  }
}