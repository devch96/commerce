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
  // 쿠폰 적용 불가(최소금액 미달·만료·소유/상태 부적합) → 주문 거절.
  COUPON_NOT_APPLICABLE(HttpStatus.BAD_REQUEST, "쿠폰을 적용할 수 없습니다."),
  ORDER_NOT_CANCELLABLE(HttpStatus.BAD_REQUEST, "현재 상태에서는 취소할 수 없습니다."),
  INVALID_ORDER_STATUS(HttpStatus.BAD_REQUEST, "허용되지 않는 주문 상태 변경입니다."),
  ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 주문에 접근할 권한이 없습니다."),
  // 결제는 승인(캡처)됐으나 이후 재고 확정·주문 완료에 실패. 환불로 되돌리지 않고 후속 복구가 재처리한다.
  ORDER_COMPLETION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR,
      "결제는 완료되었으나 주문 확정 처리에 실패했습니다. 잠시 후 자동 반영됩니다."),
  // 의존 서비스(상품/재고/결제) 호출 실패.
  DEPENDENT_SERVICE_ERROR(HttpStatus.BAD_GATEWAY, "의존 서비스 호출에 실패했습니다.");

  private final HttpStatus status;
  private final String message;

  ErrorCode(HttpStatus status, String message) {
    this.status = status;
    this.message = message;
  }
}
