package com.sparta.copa.copaticket.common.enums;

/**
 * 예매 이벤트 상태. OPEN일 때만 대기열 진입·발권이 가능하다.
 * 실제 오픈은 관리자 open API가 Redis 좌석 시드와 함께 전이시킨다.
 */
public enum EventStatus {
  SCHEDULED, // 오픈 대기(정보 공개만)
  OPEN,      // 예매 진행 중
  CLOSED     // 예매 종료
}