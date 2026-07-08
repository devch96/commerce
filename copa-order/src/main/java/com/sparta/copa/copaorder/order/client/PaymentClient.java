package com.sparta.copa.copaorder.order.client;

import com.sparta.copa.copaorder.common.exception.BusinessException;
import com.sparta.copa.copaorder.common.exception.ErrorCode;
import com.sparta.copa.copaorder.order.client.dto.ApiEnvelope;
import com.sparta.copa.copaorder.order.client.dto.KakaoConfirmRequest;
import com.sparta.copa.copaorder.order.client.dto.KakaoReadyRequest;
import com.sparta.copa.copaorder.order.client.dto.PaymentView;
import com.sparta.copa.copaorder.order.client.dto.PgReadyView;
import com.sparta.copa.copaorder.order.client.dto.TossConfirmRequest;
import com.sparta.copa.copaorder.order.client.feign.PaymentFeignClient;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 결제 서비스 호출 어댑터 — OpenFeign 전송 + 봉투 언랩 + 예외 변환. 금액은 원(₩) 단위 Long으로 전달.
@Component
@RequiredArgsConstructor
public class PaymentClient {

  private final PaymentFeignClient feign;

  // 카카오 준비: tid 발급 + 결제창 리다이렉트 URL 확보.
  public PgReadyView kakaoReady(String orderNo, Long userId, Long amount, String itemName) {
    try {
      ApiEnvelope<PgReadyView> response = feign.kakaoReady(userId,
          new KakaoReadyRequest(orderNo, amount, itemName));
      return unwrapReady(response);
    } catch (FeignException e) {
      throw new BusinessException(ErrorCode.DEPENDENT_SERVICE_ERROR);
    }
  }

  // 카카오 승인. 금액은 결제 서비스가 ready 때 저장한 값을 신뢰 원천으로 사용한다.
  public PaymentView kakaoConfirm(String orderNo, Long userId, String pgToken) {
    try {
      ApiEnvelope<PaymentView> response = feign.kakaoConfirm(userId,
          new KakaoConfirmRequest(orderNo, pgToken));
      return unwrapPayment(response);
    } catch (FeignException e) {
      throw toConfirmError(e);
    }
  }

  // 토스 승인. 서버가 계산한 payable을 금액으로 넘겨 위변조를 차단한다.
  public PaymentView tossConfirm(String orderNo, Long userId, Long amount, String paymentKey) {
    try {
      ApiEnvelope<PaymentView> response = feign.tossConfirm(userId,
          new TossConfirmRequest(orderNo, amount, paymentKey));
      return unwrapPayment(response);
    } catch (FeignException e) {
      throw toConfirmError(e);
    }
  }

  /**
   * 승인 실패의 성격을 구분한다. 4xx는 결제 서비스가 "승인되지 않았다"고 확정 응답한 것(거절·검증 실패)이라
   * PAYMENT_FAILED로 매핑해 Saga가 안전하게 보상(예약 해제·주문 취소)하게 한다.
   * 5xx·타임아웃·연결 실패는 승인이 실제로는 성공했을 수 있는 '결과 불확실'이므로 DEPENDENT_SERVICE_ERROR로
   * 남겨 Saga가 보상하지 않고 재시도 가능 상태(PENDING_PAYMENT)를 유지하게 한다.
   */
  private BusinessException toConfirmError(FeignException e) {
    if (e.status() >= 400 && e.status() < 500) {
      return new BusinessException(ErrorCode.PAYMENT_FAILED);
    }
    return new BusinessException(ErrorCode.DEPENDENT_SERVICE_ERROR);
  }

  public void cancel(String orderNo) {
    try {
      feign.cancel(orderNo);
    } catch (FeignException e) {
      throw new BusinessException(ErrorCode.DEPENDENT_SERVICE_ERROR);
    }
  }

  private PaymentView unwrapPayment(ApiEnvelope<PaymentView> response) {
    if (response == null || response.getData() == null) {
      throw new BusinessException(ErrorCode.DEPENDENT_SERVICE_ERROR);
    }
    return response.getData();
  }

  private PgReadyView unwrapReady(ApiEnvelope<PgReadyView> response) {
    if (response == null || response.getData() == null
        || response.getData().getRedirectUrl() == null) {
      throw new BusinessException(ErrorCode.DEPENDENT_SERVICE_ERROR);
    }
    return response.getData();
  }
}