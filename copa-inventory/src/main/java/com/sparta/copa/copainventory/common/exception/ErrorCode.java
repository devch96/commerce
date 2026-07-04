package com.sparta.copa.copainventory.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

  INVENTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "재고를 찾을 수 없습니다."),
  // 예약 단계에서 재고 부족 → 주문 Saga의 INVENTORY_FAILED 트리거. 결제로 진입하지 못한다.
  OUT_OF_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다."),
  INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "수량은 1 이상이어야 합니다."),
  INVALID_STOCK(HttpStatus.BAD_REQUEST, "재고 수량은 0 이상이어야 합니다."),
  // 낙관적 락 충돌이 재시도 후에도 해소되지 않은 경우.
  CONCURRENT_UPDATE(HttpStatus.CONFLICT, "동시 요청으로 재고 갱신에 실패했습니다."),
  // 확정 시점에 살아있는(RESERVED/CONFIRMED) 예약이 없음 — TTL 만료 해제 후 결제가 승인된 경우 등.
  // 조용히 no-op하면 재고 차감 없이 주문이 완료(오버셀)되므로 명시적으로 실패시킨다.
  RESERVATION_EXPIRED(HttpStatus.CONFLICT, "예약이 만료되어 확정할 수 없습니다.");

  private final HttpStatus status;
  private final String message;

  ErrorCode(HttpStatus status, String message) {
    this.status = status;
    this.message = message;
  }
}
