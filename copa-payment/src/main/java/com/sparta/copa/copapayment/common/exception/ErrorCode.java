package com.sparta.copa.copapayment.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

  PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제를 찾을 수 없습니다."),
  INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "결제 금액이 올바르지 않습니다."),
  // 가상 PG가 결제를 거절(예: 한도 초과 모킹).
  PAYMENT_DECLINED(HttpStatus.PAYMENT_REQUIRED, "결제가 거절되었습니다."),
  // 이미 취소/실패한 결제에 대한 잘못된 상태 전이.
  INVALID_PAYMENT_STATE(HttpStatus.CONFLICT, "현재 상태에서 처리할 수 없는 결제 요청입니다."),
  REFUND_EXCEEDS_AMOUNT(HttpStatus.BAD_REQUEST, "환불 금액이 결제 잔액을 초과합니다.");

  private final HttpStatus status;
  private final String message;

  ErrorCode(HttpStatus status, String message) {
    this.status = status;
    this.message = message;
  }
}
