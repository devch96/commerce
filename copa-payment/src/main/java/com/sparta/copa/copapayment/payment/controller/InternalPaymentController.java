package com.sparta.copa.copapayment.payment.controller;

import com.sparta.copa.copapayment.common.response.ApiResponse;
import com.sparta.copa.copapayment.payment.dto.request.KakaoConfirmRequest;
import com.sparta.copa.copapayment.payment.dto.request.PgReadyRequest;
import com.sparta.copa.copapayment.payment.dto.request.TossConfirmRequest;
import com.sparta.copa.copapayment.payment.dto.response.PaymentResponse;
import com.sparta.copa.copapayment.payment.dto.response.PgReadyResponse;
import com.sparta.copa.copapayment.payment.service.PaymentCancelService;
import com.sparta.copa.copapayment.payment.service.kakao.KakaoPaymentService;
import com.sparta.copa.copapayment.payment.service.toss.TossPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 서비스 간 내부 호출용. 주문 Saga가 결제 준비(카카오)/승인/취소를 호출한다.
@RestController
@RequestMapping("/internal/payments")
@RequiredArgsConstructor
public class InternalPaymentController {

  private static final String USER_ID_HEADER = "X-User-Id";

  private final KakaoPaymentService kakaoPaymentService;
  private final TossPaymentService tossPaymentService;
  private final PaymentCancelService paymentCancelService;

  // 카카오 준비(결제창 진입 전). tid + 리다이렉트 URL 반환.
  @PostMapping("/kakao/ready")
  public ApiResponse<PgReadyResponse> kakaoReady(@RequestHeader(USER_ID_HEADER) Long userId,
      @Valid @RequestBody PgReadyRequest request) {
    return ApiResponse.success(kakaoPaymentService.ready(userId, request));
  }

  // 카카오 승인(리다이렉트 후).
  @PostMapping("/kakao/confirm")
  public ApiResponse<PaymentResponse> kakaoConfirm(@RequestHeader(USER_ID_HEADER) Long userId,
      @Valid @RequestBody KakaoConfirmRequest request) {
    return ApiResponse.success(kakaoPaymentService.confirm(userId, request));
  }

  // 토스 승인(리다이렉트 후).
  @PostMapping("/toss/confirm")
  public ApiResponse<PaymentResponse> tossConfirm(@RequestHeader(USER_ID_HEADER) Long userId,
      @Valid @RequestBody TossConfirmRequest request) {
    return ApiResponse.success(tossPaymentService.confirm(userId, request));
  }

  // 결제 취소(보상). 주문 Saga가 호출.
  @PostMapping("/{orderId}/cancel")
  public ApiResponse<Void> cancel(@PathVariable String orderId) {
    paymentCancelService.cancel(orderId);
    return ApiResponse.success();
  }
}