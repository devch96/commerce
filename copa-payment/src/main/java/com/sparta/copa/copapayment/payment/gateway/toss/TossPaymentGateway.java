package com.sparta.copa.copapayment.payment.gateway.toss;

import com.sparta.copa.copapayment.payment.gateway.PgApproval;
import com.sparta.copa.copapayment.payment.gateway.PgAuthPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component("TOSS")
@RequiredArgsConstructor
@Slf4j
public class TossPaymentGateway {

  private final TossPaymentClient tossPaymentClient;

  public PgApproval approve(PgAuthPayload authPayload, String orderId, Long amount) {
    try {
      TossApproveRequest request = TossApproveRequest.builder()
          .paymentKey(authPayload.getPgToken())
          .orderId(orderId)
          .amount(amount)
          .build();

      TossApproveResponse response = tossPaymentClient.confirmPayment(request,
          String.valueOf(orderId));
      log.info("토스 승인 성공 - 주문번호: {}, 금액: {}", orderId, response.getTotalAmount());

      return PgApproval.success(response.getPaymentKey());
    } catch (IllegalArgumentException e) {
      log.warn("토스 승인 거절(비즈니스 실패) - 주문번호: {}, 사유: {}", orderId, e.getMessage());
      return PgApproval.fail();
    } catch (Exception e) {
      log.error("토스 API 통신 실패(시스템 오류) - 주문번호: {}", orderId, e);
      throw e;
    }
  }

  public void cancel(String pgTransactionId) {
    log.info("토스 결제 취소 요청 - PG 트랜잭션ID: {}", pgTransactionId);
  }
}
