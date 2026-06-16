package com.sparta.copa.copapayment.payment.controller;

import com.sparta.copa.copapayment.common.response.ApiResponse;
import com.sparta.copa.copapayment.payment.dto.response.PaymentResponse;
import com.sparta.copa.copapayment.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 결제 상세 조회(게이트웨이 경유).
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentService paymentService;

  @GetMapping("/{orderId}")
  public ApiResponse<PaymentResponse> getPayment(@PathVariable Long orderId) {
    return ApiResponse.success(paymentService.getByOrderId(orderId));
  }
}
