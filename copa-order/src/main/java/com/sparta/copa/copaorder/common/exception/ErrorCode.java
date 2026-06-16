package com.sparta.copa.copaorder.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

  ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
  EMPTY_ORDER(HttpStatus.BAD_REQUEST, "주문 품목이 비어 있습니다."),
  PRODUCT_UNAVAILABLE(HttpStatus.BAD_REQUEST, "구매할 수 없는 상품이 포함되어 있습니다."),
  // 재고 예약 실패(경쟁에서 밀림) → 주문 취소.
  OUT_OF_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다."),
  PAYMENT_FAILED(HttpStatus.PAYMENT_REQUIRED, "결제에 실패했습니다."),
  ORDER_NOT_CANCELLABLE(HttpStatus.BAD_REQUEST, "현재 상태에서는 취소할 수 없습니다."),
  INVALID_ORDER_STATUS(HttpStatus.BAD_REQUEST, "허용되지 않는 주문 상태 변경입니다."),
  ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 주문에 접근할 권한이 없습니다."),
  // 의존 서비스(상품/재고/결제) 호출 실패.
  DEPENDENT_SERVICE_ERROR(HttpStatus.BAD_GATEWAY, "의존 서비스 호출에 실패했습니다.");

  private final HttpStatus status;
  private final String message;

  ErrorCode(HttpStatus status, String message) {
    this.status = status;
    this.message = message;
  }
}
