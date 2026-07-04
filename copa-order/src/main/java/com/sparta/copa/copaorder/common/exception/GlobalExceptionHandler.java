package com.sparta.copa.copaorder.common.exception;

import com.sparta.copa.copaorder.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
    ErrorCode errorCode = e.getErrorCode();
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

  // 그 외 미처리 예외 — 내부 정보를 감춘 채 공통 봉투로 500. 원인은 로그로 남긴다.
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
    log.error("미처리 예외", e);
    return ResponseEntity.internalServerError()
        .body(ApiResponse.error("서버 내부 오류가 발생했습니다."));
  }
}
