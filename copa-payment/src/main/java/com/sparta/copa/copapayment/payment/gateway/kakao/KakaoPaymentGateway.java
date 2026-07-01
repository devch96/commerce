package com.sparta.copa.copapayment.payment.gateway.kakao;

import com.sparta.copa.copapayment.common.exception.BusinessException;
import com.sparta.copa.copapayment.common.exception.ErrorCode;
import com.sparta.copa.copapayment.payment.gateway.PgApproval;
import com.sparta.copa.copapayment.payment.gateway.PgAuthPayload;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("KAKAO")
@Slf4j
public class KakaoPaymentGateway {

  private final KakaoPaymentClient kakaoPaymentClient;
  private final String cid;
  private final String approvalUrl;
  private final String cancelUrl;
  private final String failUrl;

  public KakaoPaymentGateway(
      KakaoPaymentClient kakaoPaymentClient,
      @Value("${payment.kakao.cid:TC0ONETIME}") String cid,
      @Value("${payment.kakao.approval-url}") String approvalUrl,
      @Value("${payment.kakao.cancel-url}") String cancelUrl,
      @Value("${payment.kakao.fail-url}") String failUrl) {
    this.kakaoPaymentClient = kakaoPaymentClient;
    this.cid = cid;
    this.approvalUrl = approvalUrl;
    this.cancelUrl = cancelUrl;
    this.failUrl = failUrl;
  }

  // 결제창 진입 전 서버가 호출해 tid + 리다이렉트 URL을 발급받는다.
  public KakaoReadyResponse ready(String orderId, Long userId, Long amount, String itemName) {
    KakaoReadyRequest request = KakaoReadyRequest.builder()
        .cid(cid)
        .partnerOrderId(orderId)
        .partnerUserId(String.valueOf(userId))
        .itemName(itemName)
        .quantity(1)
        .totalAmount(amount)
        .taxFreeAmount(0L)
        .approvalUrl(approvalUrl)
        .cancelUrl(cancelUrl)
        .failUrl(failUrl)
        .build();
    KakaoReadyResponse response = kakaoPaymentClient.ready(request);
    log.info("카카오 준비 성공 - 주문번호: {}, TID: {}", orderId, response.getTid());
    return response;
  }

  public PgApproval approve(PgAuthPayload authPayload, String orderId, Long amount, Long userId) {
    KakaoApproveRequest request = KakaoApproveRequest.builder()
        .cid(cid)
        .tid(authPayload.getPgExtraId())
        .pgToken(authPayload.getPgToken())
        .partnerOrderId(orderId)
        .totalAmount(amount)
        .partnerUserId(String.valueOf(userId))
        .build();

    try {
      KakaoApproveResponse response = kakaoPaymentClient.approvePayment(request);
      verifyAmount(orderId, amount, response);
      log.info("카카오 승인 성공 - 주문번호: {}, TID: {}", orderId, response.getTid());
      return PgApproval.success(response.getTid());

    } catch (FeignException e) {
      // 4xx = 카카오의 결제 거절(비즈니스 실패) → 보상 흐름. 5xx/타임아웃(status<0) = 시스템 오류 → rethrow.
      if (e.status() >= 400 && e.status() < 500) {
        log.warn("카카오 승인 거절 - 주문번호: {}, status: {}, body: {}", orderId, e.status(), e.contentUTF8());
        return PgApproval.fail();
      }
      log.error("카카오 API 통신 실패(시스템 오류) - 주문번호: {}, status: {}", orderId, e.status(), e);
      throw e;
    }
  }

  // 승인 응답 금액이 요청 금액과 다르면 위변조/불일치이므로 승인으로 처리하지 않는다.
  private void verifyAmount(String orderId, Long amount, KakaoApproveResponse response) {
    Integer approved = response.getAmount() == null ? null : response.getAmount().getTotal();
    if (approved == null || amount == null || approved.longValue() != amount) {
      log.error("카카오 승인 금액 불일치 - 주문번호: {}, 요청: {}, 승인: {}", orderId, amount, approved);
      throw new BusinessException(ErrorCode.INVALID_AMOUNT);
    }
  }

  // 전액 취소. tid로 승인 건을 지정한다(pgTransactionId == tid).
  public void cancel(String tid, Long amount) {
    KakaoCancelRequest request = KakaoCancelRequest.builder()
        .cid(cid)
        .tid(tid)
        .cancelAmount(amount)
        .cancelTaxFreeAmount(0L)
        .build();
    kakaoPaymentClient.cancel(request);
    log.info("카카오 결제 취소 성공 - TID: {}, 금액: {}", tid, amount);
  }
}