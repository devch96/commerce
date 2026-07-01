package com.sparta.copa.copapayment.payment.gateway.toss;

import com.sparta.copa.copapayment.common.exception.BusinessException;
import com.sparta.copa.copapayment.common.exception.ErrorCode;
import com.sparta.copa.copapayment.payment.gateway.PgApproval;
import com.sparta.copa.copapayment.payment.gateway.PgAuthPayload;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component("TOSS")
@RequiredArgsConstructor
@Slf4j
public class TossPaymentGateway {

  private final TossPaymentClient tossPaymentClient;

  public PgApproval approve(PgAuthPayload authPayload, String orderId, Long amount) {
    TossApproveRequest request = TossApproveRequest.builder()
        .paymentKey(authPayload.getPgToken())
        .orderId(orderId)
        .amount(amount)
        .build();

    try {
      TossApproveResponse response = tossPaymentClient.confirmPayment(request, orderId);
      verify(orderId, amount, response);
      log.info("토스 승인 성공 - 주문번호: {}, 금액: {}", orderId, response.getTotalAmount());
      return PgApproval.success(response.getPaymentKey());

    } catch (FeignException e) {
      // 4xx = 토스의 결제 거절(비즈니스 실패) → 보상 흐름. 5xx/타임아웃(status<0) = 시스템 오류 → rethrow.
      if (e.status() >= 400 && e.status() < 500) {
        log.warn("토스 승인 거절 - 주문번호: {}, status: {}, body: {}", orderId, e.status(), e.contentUTF8());
        return PgApproval.fail();
      }
      log.error("토스 API 통신 실패(시스템 오류) - 주문번호: {}, status: {}", orderId, e.status(), e);
      throw e;
    }
  }

  // 승인 상태(DONE)와 승인 금액이 요청과 일치하는지 검증(위변조 방지).
  private void verify(String orderId, Long amount, TossApproveResponse response) {
    if (!"DONE".equals(response.getStatus())
        || response.getTotalAmount() == null || !response.getTotalAmount().equals(amount)) {
      log.error("토스 승인 검증 실패 - 주문번호: {}, 요청: {}, 승인: {}, status: {}",
          orderId, amount, response.getTotalAmount(), response.getStatus());
      throw new BusinessException(ErrorCode.INVALID_AMOUNT);
    }
  }

  // 전액 취소. pgTransactionId == paymentKey.
  public void cancel(String paymentKey, String reason) {
    TossCancelRequest request = TossCancelRequest.builder()
        .cancelReason(reason == null ? "주문 취소" : reason)
        .build();
    tossPaymentClient.cancelPayment(paymentKey, request);
    log.info("토스 결제 취소 성공 - paymentKey: {}", paymentKey);
  }
}
