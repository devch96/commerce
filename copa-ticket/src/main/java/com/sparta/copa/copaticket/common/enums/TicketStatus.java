package com.sparta.copa.copaticket.common.enums;

public enum TicketStatus {
  ISSUED,    // 발권 완료
  CANCELLED  // 사용자 취소(좌석은 Redis 풀로 복원)
}