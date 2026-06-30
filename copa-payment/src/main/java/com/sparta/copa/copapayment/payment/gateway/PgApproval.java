package com.sparta.copa.copapayment.payment.gateway;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
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
