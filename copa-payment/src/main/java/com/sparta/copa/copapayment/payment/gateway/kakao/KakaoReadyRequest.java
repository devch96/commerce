package com.sparta.copa.copapayment.payment.gateway.kakao;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 카카오 결제준비(/v1/payment/ready) 요청. 결제창 진입 전 서버가 호출해 tid를 발급받는다.
// 카카오는 snake_case 본문을 받으므로 필드명을 자동 변환한다(partnerOrderId → partner_order_id).
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class KakaoReadyRequest {

  private String cid;
  private String partnerOrderId;
  private String partnerUserId;
  private String itemName;
  private Integer quantity;
  private Long totalAmount;
  private Long taxFreeAmount;
  private String approvalUrl;
  private String cancelUrl;
  private String failUrl;
}