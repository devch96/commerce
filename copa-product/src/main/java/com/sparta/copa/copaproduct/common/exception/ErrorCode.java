package com.sparta.copa.copaproduct.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

  PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
  INVALID_PRODUCT_PRICE(HttpStatus.BAD_REQUEST, "상품 가격은 0 이상이어야 합니다."),
  INVALID_PRODUCT_STOCK(HttpStatus.BAD_REQUEST, "재고 수량은 0 이상이어야 합니다."),
  PRODUCT_NOT_SELLABLE(HttpStatus.BAD_REQUEST, "판매 상태로 변경하려면 재고가 1개 이상이어야 합니다."),
  CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다."),
  DUPLICATE_CATEGORY(HttpStatus.CONFLICT, "같은 상위 카테고리에 동일한 이름이 이미 존재합니다."),
  CATEGORY_HAS_CHILDREN(HttpStatus.BAD_REQUEST, "하위 카테고리가 있어 삭제할 수 없습니다."),
  CATEGORY_CYCLE(HttpStatus.BAD_REQUEST, "카테고리를 자기 자신 또는 하위로 이동할 수 없습니다."),
  INVALID_CATEGORY(HttpStatus.BAD_REQUEST, "존재하지 않는 카테고리가 포함되어 있습니다."),
  PRODUCT_NOT_PURCHASABLE(HttpStatus.BAD_REQUEST, "현재 판매 중인 상품이 아닙니다."),
  INVALID_PRODUCT_OPTION(HttpStatus.BAD_REQUEST, "상품 옵션 구조가 올바르지 않습니다."),
  INVALID_OPTION_DISCOUNT(HttpStatus.BAD_REQUEST, "옵션 할인 설정이 올바르지 않습니다."),
  OPTION_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "옵션 상품은 옵션(optionKey)을 선택해야 합니다."),
  OPTION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "옵션이 없는 상품에는 옵션을 지정할 수 없습니다."),
  OPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 옵션을 찾을 수 없습니다."),
  CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "장바구니에 해당 상품이 없습니다."),
  // 검색 가격 범위가 음수이거나 최소가 최대보다 큰 경우.
  INVALID_SEARCH_CONDITION(HttpStatus.BAD_REQUEST, "검색 가격 범위가 올바르지 않습니다."),
  ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 작업을 수행할 권한이 없습니다."),
  OUTBOX_SERIALIZATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이벤트 직렬화에 실패했습니다.");

  private final HttpStatus status;
  private final String message;

  ErrorCode(HttpStatus status, String message) {
    this.status = status;
    this.message = message;
  }
}
