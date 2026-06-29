package com.sparta.copa.copapayment.payment.service;

import com.sparta.copa.copapayment.common.exception.BusinessException;
import com.sparta.copa.copapayment.common.exception.ErrorCode;
import com.sparta.copa.copapayment.payment.domain.Payment;
import com.sparta.copa.copapayment.payment.dto.request.PaymentRequest;
import com.sparta.copa.copapayment.payment.dto.response.PaymentResponse;
import com.sparta.copa.copapayment.payment.gateway.PaymentGateway;
import com.sparta.copa.copapayment.payment.gateway.PgApproval;
import com.sparta.copa.copapayment.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 DB 변경(짧은 트랜잭션)만 담당. 오케스트레이션(멱등 분기)은 PaymentService가 맡고,
 * 트랜잭션 메서드를 별도 빈으로 둬 멱등 충돌 예외를 트랜잭션 경계 밖에서 처리할 수 있게 한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentCommandService {

  private final PaymentRepository paymentRepository;
  private final PaymentGateway paymentGateway;

  // 1단계: 트랜잭션을 켜고 일단 PENDING(결제진행중) 상태로 DB에 확실히 박아넣고 커밋까지 끝낸다.
  @Transactional
  public Payment createPendingPayment(Long userId, PaymentRequest request) {
    // saveAndFlush로 유니크 제약조건 위반을 여기서 즉시 터트림
    return paymentRepository.saveAndFlush(
        Payment.request(request.getOrderId(), userId, request.getAmount())
    );
  }

  // 2단계: 외부 PG API를 찌른다 (트랜잭션 없음 -> 스레드 대기 오버헤드 최소화)
  public PgApproval requestPgApproval(Long orderId, BigDecimal amount) {
    return paymentGateway.approve(orderId, amount);
  }

  // 3단계: 트랜잭션을 다시 켜고 PG 결과를 DB에 최종 반영한다.
  @Transactional
  public PaymentResponse updateStatus(Long orderId, PgApproval approval) {
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
