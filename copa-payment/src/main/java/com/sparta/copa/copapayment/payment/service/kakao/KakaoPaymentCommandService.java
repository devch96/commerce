package com.sparta.copa.copapayment.payment.service.kakao;

import com.sparta.copa.copapayment.common.exception.BusinessException;
import com.sparta.copa.copapayment.common.exception.ErrorCode;
import com.sparta.copa.copapayment.payment.domain.Payment;
import com.sparta.copa.copapayment.payment.dto.response.PaymentResponse;
import com.sparta.copa.copapayment.payment.gateway.PgApproval;
import com.sparta.copa.copapayment.payment.gateway.kakao.KakaoApproveRequest;
import com.sparta.copa.copapayment.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KakaoPaymentCommandService {

  private final PaymentRepository paymentRepository;

  @Transactional
  public Payment createPendingPayment(Long userId, KakaoApproveRequest request) {
    return paymentRepository.saveAndFlush(
        Payment.request(request.getPartnerOrderId(), userId, request.getTotalAmount())
    );
  }

  @Transactional
  public PaymentResponse updateStatus(String orderId, PgApproval approval) {
    Payment payment = paymentRepository.findByOrderId(orderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

    if (approval.isApproved()) {
      payment.approve(approval.getTransactionId());
    } else {
      payment.fail();
    }
    return PaymentResponse.from(payment);
  }
}
