package com.sparta.copa.copaorder.order.client;

import com.sparta.copa.copaorder.common.exception.BusinessException;
import com.sparta.copa.copaorder.common.exception.ErrorCode;
import com.sparta.copa.copaorder.order.client.dto.ApiEnvelope;
import com.sparta.copa.copaorder.order.client.dto.CouponReserveView;
import com.sparta.copa.copaorder.order.client.feign.CouponFeignClient;
import feign.FeignException;
import java.math.BigDecimal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 쿠폰 서비스 호출 어댑터 — OpenFeign 전송 + 봉투 언랩 + 예외 변환.
@Component
@RequiredArgsConstructor
public class CouponClient {

  private final CouponFeignClient feign;

  // 선점(검증 + 할인 계산). 적용 불가(최소금액·만료·소유/상태)는 4xx → COUPON_NOT_APPLICABLE로 주문 거절.
  public BigDecimal reserve(Long userCouponId, Long userId, Long orderId, BigDecimal orderAmount) {
    try {
      ApiEnvelope<CouponReserveView> response = feign.reserve(Map.of(
          "userCouponId", userCouponId, "userId", userId,
          "orderId", orderId, "orderAmount", orderAmount));
      if (response == null || response.getData() == null
          || response.getData().getDiscountAmount() == null) {
        throw new BusinessException(ErrorCode.DEPENDENT_SERVICE_ERROR);
      }
      return response.getData().getDiscountAmount();
    } catch (FeignException e) {
      if (e.status() >= 400 && e.status() < 500) {
        throw new BusinessException(ErrorCode.COUPON_NOT_APPLICABLE);
      }
      throw new BusinessException(ErrorCode.DEPENDENT_SERVICE_ERROR);
    }
  }

  public void confirm(Long orderId) {
    safe(() -> feign.confirm(Map.of("orderId", orderId)));
  }

  public void release(Long orderId) {
    safe(() -> feign.release(Map.of("orderId", orderId)));
  }

  // 사용 확정된 쿠폰까지 되돌린다(결제 완료 주문의 사용자 취소).
  public void restore(Long orderId) {
    safe(() -> feign.restore(Map.of("orderId", orderId)));
  }

  private void safe(Runnable call) {
    try {
      call.run();
    } catch (FeignException e) {
      throw new BusinessException(ErrorCode.DEPENDENT_SERVICE_ERROR);
    }
  }
}
