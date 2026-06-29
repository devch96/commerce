package com.sparta.copa.copapayment.payment.gateway.toss;

import com.sparta.copa.copapayment.payment.dto.request.PaymentRequest;
import com.sparta.copa.copapayment.payment.gateway.PaymentGateway;
import com.sparta.copa.copapayment.payment.gateway.PgApproval;
import com.sparta.copa.copapayment.payment.gateway.PgAuthPayload;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component("TOSS")
@RequiredArgsConstructor
@Slf4j
public class TossPaymentGateway implements PaymentGateway {

  private final TossPaymentClient tossPaymentClient;

  @Override
  public PgApproval approve(PgAuthPayload authPayload, Long orderId, Long amount) {
    try {
      TossConfirmRequest request = TossConfirmRequest.builder()
          .paymentKey(authPayload.getPgToken())
          .orderId(String.valueOf(orderId))
          .amount(amount)
          .build();

      TossConfirmResponse response = tossPaymentClient.confirmPayment(request,
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

  @Override
  public void cancel(String pgTransactionId) {
    log.info("토스 결제 취소 요청 - PG 트랜잭션ID: {}", pgTransactionId);
  }
}
