package com.sparta.copa.copapayment.payment.service;

import com.sparta.copa.copapayment.common.exception.BusinessException;
import com.sparta.copa.copapayment.common.exception.ErrorCode;
import com.sparta.copa.copapayment.payment.domain.Payment;
import com.sparta.copa.copapayment.payment.dto.response.PaymentResponse;
import com.sparta.copa.copapayment.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {
  private final PaymentRepository paymentRepository;

  // 사용자 결제 조회. orderId가 순차 노출되는 값이므로 소유자 검증으로 타인 결제 조회(IDOR)를 막는다.
  @Transactional(readOnly = true)
  public PaymentResponse getByOrderId(String orderId, Long userId) {
    Payment payment = paymentRepository.findByOrderId(orderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    if (payment.getUserId() == null || !payment.getUserId().equals(userId)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
    return PaymentResponse.from(payment);
  }

}
