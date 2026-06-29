package com.sparta.copa.copapayment.payment.gateway;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// PG 승인 결과(거래 식별자 + 승인 여부).
@Getter
@RequiredArgsConstructor
public class PgApproval {

  private final boolean approved;
  private final String transactionId;

  public static PgApproval success(String transactionId) {
    return new PgApproval(true, transactionId);
  }

  public static PgApproval fail() {
    return new PgApproval(false, null);
  }
}
