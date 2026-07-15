package com.sparta.copa.copaticket.common.enums;

/**
 * 대기열 조회 상태. 오류가 아닌 정상 응답으로 구분한다(폴링 클라이언트가 분기).
 */
public enum QueueStatus {
  WAITING,      // 대기 중(position 포함)
  ADMITTED,     // 입장 허가(entry TTL 내 발권 가능)
  ISSUED,       // 이미 발권 완료
  NOT_IN_QUEUE  // 대기열에 없음
}