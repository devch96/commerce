package com.sparta.copa.copapayment.payment.controller;

import com.sparta.copa.copapayment.common.response.ApiResponse;
import com.sparta.copa.copapayment.payment.dto.request.PaymentRequest;
import com.sparta.copa.copapayment.payment.dto.response.PaymentResponse;
import com.sparta.copa.copapayment.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 서비스 간 내부 호출용. 주문 Saga가 결제/취소를 호출한다.
@RestController
@RequestMapping("/internal/payments")
@RequiredArgsConstructor
public class InternalPaymentController {

  private final PaymentService paymentService;

  // 결제 시도. 응답의 status(APPROVED/FAILED)로 주문이 confirm/release를 분기한다.
  @PostMapping
  public ApiResponse<PaymentResponse> pay(@Valid @RequestBody PaymentRequest request) {
    return ApiResponse.success(paymentService.pay(request));
  }

  // 결제 취소(보상).
  @PostMapping("/{orderId}/cancel")
  public ApiResponse<PaymentResponse> cancel(@PathVariable Long orderId) {
    return ApiResponse.success(paymentService.cancel(orderId));
  }
}
