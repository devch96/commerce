package com.sparta.copa.copapayment.payment.service.kakao;

import com.sparta.copa.copapayment.common.exception.BusinessException;
import com.sparta.copa.copapayment.common.exception.ErrorCode;
import com.sparta.copa.copapayment.payment.dto.response.PaymentResponse;
import com.sparta.copa.copapayment.payment.gateway.PgApproval;
import com.sparta.copa.copapayment.payment.gateway.PgAuthPayload;
import com.sparta.copa.copapayment.payment.gateway.kakao.KakaoApproveRequest;
import com.sparta.copa.copapayment.payment.gateway.kakao.KakaoPaymentGateway;
import com.sparta.copa.copapayment.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KakaoPaymentService {

  private final KakaoPaymentCommandService commandService;
  private final PaymentRepository paymentRepository;
  private final KakaoPaymentGateway kakaoPaymentGateway;

  public PaymentResponse pay(Long userId, KakaoApproveRequest request) {
    return paymentRepository.findByOrderId(request.getPartnerOrderId())
        .map(PaymentResponse::from)
        .orElseGet(() -> processIdempotently(userId, request));
  }

  private PaymentResponse processIdempotently(Long userId, KakaoApproveRequest request) {
    try {
      // 1. 장부 선점
      commandService.createPendingPayment(userId, request);

      // 2. 카카오 맞춤 페이로드 생성
      PgAuthPayload authPayload = PgAuthPayload.builder()
          .pgToken(request.getPgToken())
          .pgExtraId(request.getTid())
          .build();

      // 3. 트랜잭션 밖에서 카카오 API 호출
      PgApproval approval = kakaoPaymentGateway.approve(authPayload, request.getPartnerOrderId(),
          request.getTotalAmount(), userId);

      // 4. 최종 결과 반영
      return commandService.updateStatus(request.getPartnerOrderId(), approval);

    } catch (DataIntegrityViolationException e) {
      return paymentRepository.findByOrderId(request.getPartnerOrderId())
          .map(PaymentResponse::from)
          .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }
  }
}
