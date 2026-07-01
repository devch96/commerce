package com.sparta.copa.copapayment.payment.service;

import com.sparta.copa.copapayment.common.exception.BusinessException;
import com.sparta.copa.copapayment.common.exception.ErrorCode;
import com.sparta.copa.copapayment.payment.domain.Payment;
import com.sparta.copa.copapayment.payment.domain.PgProvider;
import com.sparta.copa.copapayment.payment.dto.response.PaymentResponse;
import com.sparta.copa.copapayment.payment.gateway.PgApproval;
import com.sparta.copa.copapayment.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 DB 변경(짧은 로컬 트랜잭션)만 담당. PG 호출(외부 I/O)은 오케스트레이션 서비스가 트랜잭션 밖에서 수행하고,
 * 단계별로 이 서비스의 트랜잭션 메서드를 호출한다(self-invocation 프록시 우회 방지).
 */
@Service
@RequiredArgsConstructor
public class PaymentCommandService {

  private final PaymentRepository paymentRepository;

  // 카카오 ready: tid를 담은 REQUESTED 결제 레코드 선점.
  @Transactional
  public Payment createReadyPayment(String orderId, Long userId, Long amount, String tid) {
    Payment payment = Payment.request(orderId, userId, amount, PgProvider.KAKAO);
    payment.assignTid(tid);
    return paymentRepository.saveAndFlush(payment);
  }

  // 토스: ready 없이 confirm 시점에 REQUESTED 결제 레코드 선점.
  @Transactional
  public Payment createPendingPayment(String orderId, Long userId, Long amount, PgProvider provider) {
    return paymentRepository.saveAndFlush(Payment.request(orderId, userId, amount, provider));
  }

  @Transactional
  public PaymentResponse updateStatus(String orderId, PgApproval approval) {
    Payment payment = findByOrderId(orderId);
    if (approval.isApproved()) {
      payment.approve(approval.getTransactionId());
    } else {
      payment.fail();
    }
    return PaymentResponse.from(payment);
  }

  // 결제 취소(전액 환불 후 CANCELLED). PG 취소 성공 이후에 호출한다.
  @Transactional
  public void cancel(String orderId) {
    findByOrderId(orderId).cancel();
  }

  private Payment findByOrderId(String orderId) {
    return paymentRepository.findByOrderId(orderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
  }
}