package com.sparta.copa.copaorder.order.client.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 결제 서비스 응답 페이로드. status는 결제 서비스의 PaymentStatus 이름 문자열.
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class PaymentView {

  private Long orderId;
  private BigDecimal amount;
  private String status;
  private String pgTransactionId;

  public boolean isApproved() {
    return "APPROVED".equals(status);
  }
}
