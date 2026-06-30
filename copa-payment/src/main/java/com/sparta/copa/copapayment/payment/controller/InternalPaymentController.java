package com.sparta.copa.copapayment.payment.controller;

import com.sparta.copa.copapayment.common.response.ApiResponse;
import com.sparta.copa.copapayment.payment.dto.response.PaymentResponse;
import com.sparta.copa.copapayment.payment.gateway.kakao.KakaoApproveRequest;
import com.sparta.copa.copapayment.payment.gateway.toss.TossApproveRequest;
import com.sparta.copa.copapayment.payment.service.kakao.KakaoPaymentService;
import com.sparta.copa.copapayment.payment.service.toss.TossPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 서비스 간 내부 호출용. 주문 Saga가 결제/취소를 호출한다.
@RestController
@RequestMapping("/internal/payments")
@RequiredArgsConstructor
public class InternalPaymentController {

  private static final String USER_ID_HEADER = "X-User-Id";

  private final TossPaymentService tossPaymentService;
  private final KakaoPaymentService kakaoPaymentService;

  @PostMapping("/kakao")
  public ApiResponse<PaymentResponse> kakaoPay(@RequestHeader(USER_ID_HEADER) Long userId,
      @Valid @RequestBody KakaoApproveRequest request) {
    return ApiResponse.success(kakaoPaymentService.pay(userId, request));
  }

  @PostMapping("/toss")
  public ApiResponse<PaymentResponse> tossPay(@RequestHeader(USER_ID_HEADER) Long userId,
      @Valid @RequestBody TossApproveRequest request) {
    return ApiResponse.success(tossPaymentService.pay(userId, request));
  }
}
