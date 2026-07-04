package com.sparta.copa.copacoupon.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

  COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "쿠폰을 찾을 수 없습니다."),
  USER_COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "보유한 쿠폰을 찾을 수 없습니다."),
  // 잘못된 쿠폰 정의(관리자 생성 검증).
  INVALID_COUPON_DEFINITION(HttpStatus.BAD_REQUEST, "쿠폰 정의가 올바르지 않습니다."),
  // 발급: 총 수량 소진 / 사용자당 1매 초과.
  COUPON_OUT_OF_STOCK(HttpStatus.CONFLICT, "쿠폰이 모두 소진되었습니다."),
  COUPON_ALREADY_ISSUED(HttpStatus.CONFLICT, "이미 발급받은 쿠폰입니다."),
  COUPON_NOT_ISSUABLE(HttpStatus.BAD_REQUEST, "지금은 발급할 수 없는 쿠폰입니다."),
  // 선착순(Redis) 발급.
  COUPON_FCFS_NOT_OPEN(HttpStatus.NOT_FOUND, "선착순 발급이 열려 있지 않은 쿠폰입니다."),
  COUPON_FCFS_PUBLISH_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "발급 처리에 실패했습니다. 잠시 후 다시 시도해 주세요."),
  // 사용/검증.
  COUPON_EXPIRED(HttpStatus.BAD_REQUEST, "만료된 쿠폰입니다."),
  COUPON_MIN_ORDER_NOT_MET(HttpStatus.BAD_REQUEST, "최소 주문 금액을 충족하지 않습니다."),
  COUPON_NOT_USABLE(HttpStatus.CONFLICT, "사용할 수 없는 상태의 쿠폰입니다."),
  ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 쿠폰에 접근할 권한이 없습니다.");

  private final HttpStatus status;
  private final String message;

  ErrorCode(HttpStatus status, String message) {
    this.status = status;
    this.message = message;
  }
}
