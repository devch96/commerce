package com.sparta.copa.copaticket.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

  EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "이벤트를 찾을 수 없습니다."),
  // 잘못된 이벤트 정의(관리자 생성 검증).
  INVALID_EVENT_DEFINITION(HttpStatus.BAD_REQUEST, "이벤트 정의가 올바르지 않습니다."),
  // 미오픈/종료 상태에서 대기열 진입·발권 시도. 이벤트는 존재하므로 404가 아닌 상태 충돌(409)로 본다.
  EVENT_NOT_OPEN(HttpStatus.CONFLICT, "지금은 예매가 열려 있지 않은 이벤트입니다."),
  // 발권: 좌석 소진 / 1인 1매 초과 / 입장 미허가.
  TICKET_SOLD_OUT(HttpStatus.CONFLICT, "좌석이 모두 매진되었습니다."),
  TICKET_ALREADY_ISSUED(HttpStatus.CONFLICT, "이미 예매한 이벤트입니다."),
  TICKET_NOT_ADMITTED(HttpStatus.FORBIDDEN, "아직 입장 순서가 아닙니다. 대기열 순서를 기다려 주세요."),
  TICKET_PUBLISH_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "예매 처리에 실패했습니다. 잠시 후 다시 시도해 주세요."),
  // 조회/취소.
  TICKET_NOT_FOUND(HttpStatus.NOT_FOUND, "예매 내역을 찾을 수 없습니다."),
  TICKET_NOT_CANCELABLE(HttpStatus.CONFLICT, "취소할 수 없는 상태의 예매입니다."),
  ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 리소스에 접근할 권한이 없습니다.");

  private final HttpStatus status;
  private final String message;

  ErrorCode(HttpStatus status, String message) {
    this.status = status;
    this.message = message;
  }
}