package com.sparta.copa.copapayment.payment.gateway.kakao;

import com.sparta.copa.copapayment.payment.gateway.PgApproval;
import com.sparta.copa.copapayment.payment.gateway.PgAuthPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component("KAKAO")
@RequiredArgsConstructor
@Slf4j
public class KakaoPaymentGateway{

  private final KakaoPaymentClient kakaoPaymentClient;
  private static final String CID = "TC0ONETIME";

  public PgApproval approve(PgAuthPayload authPayload, String orderId, Long amount, Long userId) {
    try {
      // 페이로드에서 카카오에 필요한 2개의 키를 모두 꺼내어 씁니다! (split 문자열 자르기 제거)
      KakaoApproveRequest request = KakaoApproveRequest.builder()
          .cid(CID)
          .tid(authPayload.getPgExtraId())
          .pgToken(authPayload.getPgToken())
          .partnerOrderId(String.valueOf(orderId))
          .totalAmount(amount)
          .partnerUserId(String.valueOf(userId))
          .build();

      KakaoApproveResponse response = kakaoPaymentClient.approvePayment(request);
      log.info("카카오 승인 성공 - 주문번호: {}, TID: {}", orderId, response.getTid());

      return PgApproval.success(response.getTid());

    } catch (IllegalArgumentException e) {
      log.warn("카카오 승인 거절(비즈니스 실패) - 주문번호: {}, 사유: {}", orderId, e.getMessage());
      return PgApproval.fail();
    } catch (Exception e) {
      log.error("카카오 API 통신 실패(시스템 오류) - 주문번호: {}", orderId, e);
      throw e;
    }
  }

  public void cancel(String pgTransactionId) {
    log.info("카카오 결제 취소 요청 - TID: {}", pgTransactionId);
    // ... (취소 로직)
  }
}
