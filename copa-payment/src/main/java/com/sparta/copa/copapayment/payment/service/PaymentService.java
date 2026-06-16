package com.sparta.copa.copapayment.payment.service;

import com.sparta.copa.copapayment.common.exception.BusinessException;
import com.sparta.copa.copapayment.common.exception.ErrorCode;
import com.sparta.copa.copapayment.payment.domain.Payment;
import com.sparta.copa.copapayment.payment.dto.request.PaymentRequest;
import com.sparta.copa.copapayment.payment.dto.response.PaymentResponse;
import com.sparta.copa.copapayment.payment.gateway.PaymentGateway;
import com.sparta.copa.copapayment.payment.gateway.PgApproval;
import com.sparta.copa.copapayment.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 요청/취소. 주문 Saga의 결제 단계를 수행한다(재고 예약 성공 이후에만 호출됨).
 * 주문당 결제 1건(orderId 유니크)으로 멱등하며, 결과 상태를 응답에 담아 주문이 confirm/release를 분기하게 한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

  private final PaymentRepository paymentRepository;
  private final PaymentGateway paymentGateway;

  /**
   * 결제 시도. 같은 주문이 이미 결제됐으면 그 결과를 멱등하게 반환한다.
   * PG가 거절하면 FAILED로 기록해 반환(예외 대신) → 주문 Saga가 상태로 분기하고 기록도 남는다.
   */
  @Transactional
  public PaymentResponse pay(PaymentRequest request) {
    Payment existing = paymentRepository.findByOrderId(request.getOrderId()).orElse(null);
    if (existing != null) {
      return PaymentResponse.from(existing);
    }
    Payment payment = paymentRepository.save(
        Payment.request(request.getOrderId(), request.getUserId(), request.getAmount()));

    PgApproval approval = paymentGateway.approve(request.getOrderId(), request.getAmount());
    if (approval.isApproved()) {
      payment.approve(approval.getTransactionId());
    } else {
      payment.fail();
    }
    return PaymentResponse.from(payment);
  }

  // 결제 취소(보상). 이미 취소됐으면 멱등하게 통과, 승인된 결제만 PG 취소 후 CANCELLED.
  @Transactional
  public PaymentResponse cancel(Long orderId) {
    Payment payment = paymentRepository.findByOrderId(orderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    if (payment.isApproved()) {
      paymentGateway.cancel(payment.getPgTransactionId());
      payment.cancel();
    }
    return PaymentResponse.from(payment);
  }

  @Transactional(readOnly = true)
  public PaymentResponse getByOrderId(Long orderId) {
    Payment payment = paymentRepository.findByOrderId(orderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    return PaymentResponse.from(payment);
  }
}
