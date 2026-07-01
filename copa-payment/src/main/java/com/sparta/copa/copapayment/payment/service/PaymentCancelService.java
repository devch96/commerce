package com.sparta.copa.copapayment.payment.service;

import com.sparta.copa.copapayment.common.enums.PaymentStatus;
import com.sparta.copa.copapayment.common.exception.BusinessException;
import com.sparta.copa.copapayment.common.exception.ErrorCode;
import com.sparta.copa.copapayment.payment.domain.Payment;
import com.sparta.copa.copapayment.payment.gateway.kakao.KakaoPaymentGateway;
import com.sparta.copa.copapayment.payment.gateway.toss.TossPaymentGateway;
import com.sparta.copa.copapayment.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 결제 취소(보상). 저장된 pgProvider로 게이트웨이를 라우팅해 PG 취소를 먼저 수행하고,
 * 성공 시 결제 상태를 CANCELLED로 확정한다. orderId 기준 멱등.
 */
@Service
@RequiredArgsConstructor
public class PaymentCancelService {

  private final PaymentRepository paymentRepository;
  private final PaymentCommandService commandService;
  private final TossPaymentGateway tossPaymentGateway;
  private final KakaoPaymentGateway kakaoPaymentGateway;

  public void cancel(String orderId) {
    Payment payment = paymentRepository.findByOrderId(orderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    if (payment.getStatus() == PaymentStatus.CANCELLED) {
      return; // 멱등
    }
    if (!payment.isApproved()) {
      throw new BusinessException(ErrorCode.INVALID_PAYMENT_STATE);
    }

    if (payment.getPgProvider() == null) {
      throw new BusinessException(ErrorCode.INVALID_PAYMENT_STATE);
    }
    switch (payment.getPgProvider()) {
      case TOSS -> tossPaymentGateway.cancel(payment.getPgTransactionId(), "주문 취소");
      case KAKAO -> kakaoPaymentGateway.cancel(payment.getTid(), payment.getAmount());
    }
    commandService.cancel(orderId);
  }
}