package com.sparta.copa.copapayment.payment.service.toss;

import com.sparta.copa.copapayment.common.exception.BusinessException;
import com.sparta.copa.copapayment.common.exception.ErrorCode;
import com.sparta.copa.copapayment.payment.dto.response.PaymentResponse;
import com.sparta.copa.copapayment.payment.gateway.PgApproval;
import com.sparta.copa.copapayment.payment.gateway.PgAuthPayload;
import com.sparta.copa.copapayment.payment.gateway.toss.TossApproveRequest;
import com.sparta.copa.copapayment.payment.gateway.toss.TossPaymentGateway;
import com.sparta.copa.copapayment.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TossPaymentService {

  private final TossPaymentCommandService commandService;
  private final PaymentRepository paymentRepository;
  private final TossPaymentGateway tossPaymentGateway; // 토스 전용 게이트웨이 직접 주입

  public PaymentResponse pay(Long userId, TossApproveRequest request) {
    return paymentRepository.findByOrderId(request.getOrderId())
        .map(PaymentResponse::from)
        .orElseGet(() -> processIdempotently(userId, request));
  }

  private PaymentResponse processIdempotently(Long userId, TossApproveRequest request) {
    try {
      // 1. 장부 선점
      commandService.createPendingPayment(userId, request);

      // 2. 토스 맞춤 페이로드 생성
      PgAuthPayload authPayload = PgAuthPayload.builder()
          .pgToken(request.getPaymentKey())
          .build();

      // 3. 트랜잭션 밖에서 토스 API 호출
      PgApproval approval = tossPaymentGateway.approve(authPayload, request.getOrderId(), request.getAmount());

      // 4. 최종 결과 반영
      return commandService.updateStatus(request.getOrderId(), approval);

    } catch (DataIntegrityViolationException e) {
      return paymentRepository.findByOrderId(request.getOrderId())
          .map(PaymentResponse::from)
          .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }
  }
}