package com.sparta.copa.copapayment.payment.gateway;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 가상 PG. 금액이 유효하면 항상 승인하고 거래 식별자를 발급한다(실 PG 연동 전 단계).
 * 결제 거절·실패 경로는 실제 PG 교체 시 또는 테스트에서 게이트웨이를 모킹해 검증한다.
 */
@Slf4j
@Component
public class MockPaymentGateway implements PaymentGateway {

  @Override
  public PgApproval approve(PgAuthPayload authPayload, Long orderId, Long amount) {
    boolean approved = amount != null && amount > 0;
    String transactionId = approved ? "PG-" + UUID.randomUUID() : null;
    log.info("[MockPG] approve orderId={} amount={} -> {}", orderId, amount, approved);
    return new PgApproval(approved, transactionId);
  }
  @Override
  public void cancel(String pgTransactionId) {
    log.info("[MockPG] cancel txId={}", pgTransactionId);
  }
}
