package com.sparta.copa.copapayment.payment.gateway;

import java.math.BigDecimal;

/**
 * 외부 PG 연동 경계. 처음엔 가상(mock) 구현으로 시작하고, 추후 실 PG(토스/카카오)로 교체한다.
 * 트랜잭션 밖에서 호출되는 외부 I/O로 취급한다.
 */
public interface PaymentGateway {

  PgApproval approve(PgAuthPayload authPayload, Long orderId, Long amount);

  void cancel(String pgTransactionId);
}
