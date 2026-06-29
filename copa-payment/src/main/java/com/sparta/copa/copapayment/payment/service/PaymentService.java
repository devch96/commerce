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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 요청/취소. 주문 Saga의 결제 단계를 수행한다(재고 예약 성공 이후에만 호출됨).
 * 주문당 결제 1건(orderId 유니크)으로 멱등하며, 결과 상태를 응답에 담아 주문이 confirm/release를 분기하게 한다.
 * 결제 생성 트랜잭션은 PaymentCommandService에 위임하고, 여기선 멱등 분기만 한다(트랜잭션 밖에서 충돌 흡수).
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

  private final PaymentCommandService commandService;
  private final PaymentRepository paymentRepository;
  private final PaymentGateway paymentGateway;

  /**
   * 결제 시도. 같은 주문이 이미 결제됐으면 그 결과를 멱등하게 반환한다.
   * 동시 중복 요청으로 유니크 충돌이 나면, 먼저 성공한 결제를 재조회해 멱등하게 반환한다(이중 청구 없음).
   * PG가 거절하면 FAILED로 기록해 반환(예외 대신) → 주문 Saga가 상태로 분기하고 기록도 남는다.
   */
  public PaymentResponse pay(Long userId, PaymentRequest request) {
    return paymentRepository.findByOrderId(request.getOrderId())
        .map(PaymentResponse::from)
        .orElseGet(() -> processIdempotently(userId, request));
  }

  private PaymentResponse processIdempotently(Long userId, PaymentRequest request) {
    try {
      // 1. DB에 펜딩 상태 선점 (유니크 충돌 시 여기서 튕겨나감)
      commandService.createPendingPayment(userId, request);

      // 2. 트랜잭션 밖에서 안전하게 외부 PG 호출 (타임아웃이 나도 내 DB 롤백 안 됨)
      PgApproval approval = commandService.requestPgApproval(request.getOrderId(), request.getAmount());

      // 3. 결과를 DB에 최종 반영
      return commandService.updateStatus(request.getOrderId(), approval);

    } catch (DataIntegrityViolationException e) {
      // 동시성 충돌 시 기존 결제 재조회 멱등 처리
      return paymentRepository.findByOrderId(request.getOrderId())
          .map(PaymentResponse::from)
          .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }
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
