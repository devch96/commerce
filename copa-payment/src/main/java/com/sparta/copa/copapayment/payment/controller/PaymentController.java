package com.sparta.copa.copapayment.payment.controller;

import com.sparta.copa.copapayment.common.response.ApiResponse;
import com.sparta.copa.copapayment.payment.dto.response.PaymentResponse;
import com.sparta.copa.copapayment.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 결제 상세 조회(게이트웨이 경유). 게이트웨이가 주입한 X-User-Id로 본인 결제만 조회를 허용한다.
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

  private static final String USER_ID_HEADER = "X-User-Id";

  private final PaymentService paymentService;

  @GetMapping("/{orderId}")
  public ApiResponse<PaymentResponse> getPayment(@RequestHeader(USER_ID_HEADER) Long userId,
      @PathVariable String orderId) {
    return ApiResponse.success(paymentService.getByOrderId(orderId, userId));
  }
}
