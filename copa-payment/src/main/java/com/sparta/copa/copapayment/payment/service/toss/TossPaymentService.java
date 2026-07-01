package com.sparta.copa.copapayment.payment.service.toss;

import com.sparta.copa.copapayment.common.exception.BusinessException;
import com.sparta.copa.copapayment.common.exception.ErrorCode;
import com.sparta.copa.copapayment.payment.domain.Payment;
import com.sparta.copa.copapayment.payment.domain.PgProvider;
import com.sparta.copa.copapayment.payment.dto.request.TossConfirmRequest;
import com.sparta.copa.copapayment.payment.dto.response.PaymentResponse;
import com.sparta.copa.copapayment.payment.gateway.PgApproval;
import com.sparta.copa.copapayment.payment.gateway.PgAuthPayload;
import com.sparta.copa.copapayment.payment.gateway.toss.TossPaymentGateway;
import com.sparta.copa.copapayment.payment.repository.PaymentRepository;
import com.sparta.copa.copapayment.payment.service.PaymentCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 토스 결제 오케스트레이션. 토스는 ready가 없어 결제창 인증 후 confirm(paymentKey) 단계만 있다.
 * PG 호출은 트랜잭션 밖에서 수행하고 DB 변경은 PaymentCommandService에 위임한다.
 */
@Service
@RequiredArgsConstructor
public class TossPaymentService {

  private final PaymentCommandService commandService;
  private final PaymentRepository paymentRepository;
  private final TossPaymentGateway tossPaymentGateway;

  public PaymentResponse confirm(Long userId, TossConfirmRequest request) {
    return paymentRepository.findByOrderId(request.getOrderId())
        .filter(Payment::isApproved)
        .map(PaymentResponse::from) // 멱등: 이미 승인된 건은 그대로 반환
        .orElseGet(() -> process(userId, request));
  }

  private PaymentResponse process(Long userId, TossConfirmRequest request) {
    try {
      // 결제 레코드 선점(재시도로 이미 있으면 재사용).
      if (paymentRepository.findByOrderId(request.getOrderId()).isEmpty()) {
        commandService.createPendingPayment(
            request.getOrderId(), userId, request.getAmount(), PgProvider.TOSS);
      }

      PgAuthPayload authPayload = PgAuthPayload.builder()
          .pgToken(request.getPaymentKey())
          .build();
      PgApproval approval = tossPaymentGateway.approve(
          authPayload, request.getOrderId(), request.getAmount());
      return commandService.updateStatus(request.getOrderId(), approval);

    } catch (DataIntegrityViolationException e) {
      return paymentRepository.findByOrderId(request.getOrderId())
          .map(PaymentResponse::from)
          .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }
  }
}