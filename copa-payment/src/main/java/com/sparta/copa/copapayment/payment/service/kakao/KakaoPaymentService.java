package com.sparta.copa.copapayment.payment.service.kakao;

import com.sparta.copa.copapayment.common.exception.BusinessException;
import com.sparta.copa.copapayment.common.exception.ErrorCode;
import com.sparta.copa.copapayment.payment.domain.Payment;
import com.sparta.copa.copapayment.payment.dto.request.KakaoConfirmRequest;
import com.sparta.copa.copapayment.payment.dto.request.PgReadyRequest;
import com.sparta.copa.copapayment.payment.dto.response.PaymentResponse;
import com.sparta.copa.copapayment.payment.dto.response.PgReadyResponse;
import com.sparta.copa.copapayment.payment.gateway.PgApproval;
import com.sparta.copa.copapayment.payment.gateway.PgAuthPayload;
import com.sparta.copa.copapayment.payment.gateway.kakao.KakaoPaymentGateway;
import com.sparta.copa.copapayment.payment.gateway.kakao.KakaoReadyResponse;
import com.sparta.copa.copapayment.payment.repository.PaymentRepository;
import com.sparta.copa.copapayment.payment.service.PaymentCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 카카오 결제 오케스트레이션. 결제창 진입 전 ready(tid 발급)와 리다이렉트 후 confirm(승인) 2단계로 나뉜다.
 * PG 호출은 트랜잭션 밖에서 수행하고 DB 변경은 PaymentCommandService에 위임한다.
 */
@Service
@RequiredArgsConstructor
public class KakaoPaymentService {

  private final PaymentCommandService commandService;
  private final PaymentRepository paymentRepository;
  private final KakaoPaymentGateway kakaoPaymentGateway;

  // 1단계: 카카오 ready 호출로 tid + 리다이렉트 URL을 확보하고 결제 레코드를 선점한다.
  public PgReadyResponse ready(Long userId, PgReadyRequest request) {
    KakaoReadyResponse response = kakaoPaymentGateway.ready(
        request.getOrderId(), userId, request.getAmount(), request.getItemName());
    commandService.createReadyPayment(
        request.getOrderId(), userId, request.getAmount(), response.getTid());
    return PgReadyResponse.of(response.getTid(), response.redirectUrl());
  }

  // 2단계: 리다이렉트로 받은 pg_token으로 승인. ready에서 저장한 tid·금액을 신뢰 원천으로 사용한다.
  public PaymentResponse confirm(Long userId, KakaoConfirmRequest request) {
    Payment payment = paymentRepository.findByOrderId(request.getOrderId())
        .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    if (payment.isApproved()) {
      return PaymentResponse.from(payment); // 멱등: 이미 승인된 건은 그대로 반환
    }

    PgAuthPayload authPayload = PgAuthPayload.builder()
        .pgToken(request.getPgToken())
        .pgExtraId(payment.getTid())
        .build();
    PgApproval approval = kakaoPaymentGateway.approve(
        authPayload, payment.getOrderId(), payment.getAmount(), userId);
    return commandService.updateStatus(payment.getOrderId(), approval);
  }
}