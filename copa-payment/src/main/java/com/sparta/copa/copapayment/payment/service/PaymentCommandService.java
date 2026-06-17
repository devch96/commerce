package com.sparta.copa.copapayment.payment.service;

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
 * 결제 DB 변경(짧은 트랜잭션)만 담당. 오케스트레이션(멱등 분기)은 PaymentService가 맡고,
 * 트랜잭션 메서드를 별도 빈으로 둬 멱등 충돌 예외를 트랜잭션 경계 밖에서 처리할 수 있게 한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentCommandService {

  private final PaymentRepository paymentRepository;
  private final PaymentGateway paymentGateway;

  /**
   * 신규 결제 1건을 생성한다. orderId 유니크 위반은 saveAndFlush로 PG 호출 전에 즉시 드러내,
   * 동시 중복 요청 시 이중 승인(이중 청구)을 막는다(예외는 호출자가 멱등 재조회로 흡수).
   */
  @Transactional
  public PaymentResponse process(PaymentRequest request) {
    Payment payment = paymentRepository.saveAndFlush(
        Payment.request(request.getOrderId(), request.getUserId(), request.getAmount()));

    PgApproval approval = paymentGateway.approve(request.getOrderId(), request.getAmount());
    if (approval.isApproved()) {
      payment.approve(approval.getTransactionId());
    } else {
      payment.fail();
    }
    return PaymentResponse.from(payment);
  }
}
