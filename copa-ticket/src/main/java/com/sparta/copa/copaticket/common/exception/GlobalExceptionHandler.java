package com.sparta.copa.copaticket.common.exception;

import com.sparta.copa.copaticket.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
    ErrorCode errorCode = e.getErrorCode();
    log.warn("비즈니스 예외: {} - {}", errorCode.name(), errorCode.getMessage());
    return ResponseEntity.status(errorCode.getStatus())
        .body(ApiResponse.error(errorCode.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
    String message = e.getBindingResult().getFieldErrors().stream()
        .findFirst()
        .map(error -> error.getField() + ": " + error.getDefaultMessage())
        .orElse("잘못된 요청입니다.");
    return ResponseEntity.badRequest().body(ApiResponse.error(message));
  }

  // 필수 헤더/파라미터 누락 등 요청 바인딩 오류는 클라이언트 잘못이므로 400으로 유지한다(catch-all에 흡수되지 않도록).
  @ExceptionHandler(ServletRequestBindingException.class)
  public ResponseEntity<ApiResponse<Void>> handleBinding(ServletRequestBindingException e) {
    return ResponseEntity.badRequest().body(ApiResponse.error("필수 요청 값이 누락되었습니다."));
  }

  // (couponId, userId) 유니크 위반 등 무결성 충돌. 500 노출 대신 409로 변환한다.
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
    log.warn("데이터 무결성 위반", e);
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiResponse.error("이미 존재하거나 충돌하는 데이터입니다."));
  }

  // @Version 충돌(동시 발급·예약 경합)은 재시도 가능한 일시 충돌이므로 500이 아닌 409로 구분해 반환한다.
  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(
      ObjectOptimisticLockingFailureException e) {
    log.warn("낙관적 락 충돌", e);
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiResponse.error("동시 요청으로 충돌이 발생했습니다. 다시 시도해 주세요."));
  }

  // 예상 못 한 예외는 스택트레이스를 응답에 노출하지 않고 로깅 후 공통 봉투로 500을 반환한다.
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
    log.error("처리되지 않은 예외", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error("서버 오류가 발생했습니다."));
  }
  // 본문 파싱 실패(깨진 JSON·잘못된 enum 값 등). 스프링 기본 오류 응답 대신 공통 봉투로 400을 준다.
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
    return ResponseEntity.badRequest()
        .body(ApiResponse.error("요청 본문을 읽을 수 없습니다. 형식을 확인해 주세요."));
  }

  // 경로/쿼리 파라미터 타입 불일치(예: 숫자 자리에 문자).
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
    return ResponseEntity.badRequest()
        .body(ApiResponse.error("요청 값의 형식이 올바르지 않습니다: " + e.getName()));
  }

  // 필수 헤더 누락(X-User-Id 등 — 게이트웨이를 거치지 않은 잘못된 접근 포함).
  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException e) {
    return ResponseEntity.badRequest()
        .body(ApiResponse.error("필수 헤더가 없습니다: " + e.getHeaderName()));
  }

}
