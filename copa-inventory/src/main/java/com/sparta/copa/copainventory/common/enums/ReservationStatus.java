package com.sparta.copa.copainventory.common.enums;

// 재고 예약 상태. RESERVED(가용 재고 차감) → CONFIRMED(결제 성공) / RELEASED(결제 실패·TTL 만료, 보상).
public enum ReservationStatus {
  RESERVED,
  CONFIRMED,
  RELEASED
}
