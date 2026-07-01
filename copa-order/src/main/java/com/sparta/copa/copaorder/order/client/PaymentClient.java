package com.sparta.copa.copaorder.order.client;

import com.sparta.copa.copaorder.common.exception.BusinessException;
import com.sparta.copa.copaorder.common.exception.ErrorCode;
import com.sparta.copa.copaorder.order.client.dto.ApiEnvelope;
import com.sparta.copa.copaorder.order.client.dto.PaymentView;
import com.sparta.copa.copaorder.order.client.dto.PgReadyView;
import com.sparta.copa.copaorder.order.client.feign.PaymentFeignClient;
import feign.FeignException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 결제 서비스 호출 어댑터 — OpenFeign 전송 + 봉투 언랩 + 예외 변환. 금액은 원(₩) 단위 Long으로 전달.
@Component
@RequiredArgsConstructor
public class PaymentClient {

  private final PaymentFeignClient feign;

  // 카카오 준비: tid 발급 + 결제창 리다이렉트 URL 확보.
  public PgReadyView kakaoReady(Long orderId, Long userId, Long amount, String itemName) {
    try {
      ApiEnvelope<PgReadyView> response = feign.kakaoReady(userId, Map.of(
          "orderId", String.valueOf(orderId), "amount", amount, "itemName", itemName));
      return unwrapReady(response);
    } catch (FeignException e) {
      throw new BusinessException(ErrorCode.DEPENDENT_SERVICE_ERROR);
    }
  }

  // 카카오 승인. 금액은 결제 서비스가 ready 때 저장한 값을 신뢰 원천으로 사용한다.
  public PaymentView kakaoConfirm(Long orderId, Long userId, String pgToken) {
    try {
      ApiEnvelope<PaymentView> response = feign.kakaoConfirm(userId, Map.of(
          "orderId", String.valueOf(orderId), "pgToken", pgToken));
      return unwrapPayment(response);
    } catch (FeignException e) {
      throw new BusinessException(ErrorCode.DEPENDENT_SERVICE_ERROR);
    }
  }

  // 토스 승인. 서버가 계산한 payable을 금액으로 넘겨 위변조를 차단한다.
  public PaymentView tossConfirm(Long orderId, Long userId, Long amount, String paymentKey) {
    try {
      ApiEnvelope<PaymentView> response = feign.tossConfirm(userId, Map.of(
          "orderId", String.valueOf(orderId), "amount", amount, "paymentKey", paymentKey));
      return unwrapPayment(response);
    } catch (FeignException e) {
      throw new BusinessException(ErrorCode.DEPENDENT_SERVICE_ERROR);
    }
  }

  public void cancel(Long orderId) {
    try {
      feign.cancel(orderId);
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