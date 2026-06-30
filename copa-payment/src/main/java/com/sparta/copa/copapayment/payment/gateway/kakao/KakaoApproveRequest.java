package com.sparta.copa.copapayment.payment.gateway.kakao;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KakaoApproveRequest{
  private String cid;
  private String tid;
  private String partnerOrderId;
  private String partnerUserId;
  private String pgToken;
  private Long totalAmount;

}
