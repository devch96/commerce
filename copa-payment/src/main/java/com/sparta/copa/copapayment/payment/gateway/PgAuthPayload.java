package com.sparta.copa.copapayment.payment.gateway;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PgAuthPayload {

  private final String pgToken;
  private final String pgExtraId;
}
