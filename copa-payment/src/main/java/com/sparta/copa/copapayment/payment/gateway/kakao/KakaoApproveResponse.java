package com.sparta.copa.copapayment.payment.gateway.kakao;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KakaoApproveResponse {
  private String aid;
  private String tid;
  private String cid;
  private String partner_order_id;
  private String partner_user_id;
  private String payment_method_type;
  private Amount amount;
  private String approved_at;

  @Getter
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  @AllArgsConstructor
  public static class Amount {
    private Integer total; // 카카오 응답 규격상 Integer 사용
    private Integer tax_free;
    private Integer vat;
    private Integer point;
    private Integer discount;
  }
}
