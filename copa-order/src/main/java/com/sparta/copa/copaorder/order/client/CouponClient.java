package com.sparta.copa.copaorder.order.client;

import com.sparta.copa.copaorder.common.exception.BusinessException;
import com.sparta.copa.copaorder.common.exception.ErrorCode;
import com.sparta.copa.copaorder.order.client.dto.ApiEnvelope;
import com.sparta.copa.copaorder.order.client.dto.CouponReserveRequest;
import com.sparta.copa.copaorder.order.client.dto.CouponReserveView;
import com.sparta.copa.copaorder.order.client.dto.OrderRefRequest;
import com.sparta.copa.copaorder.order.client.feign.CouponFeignClient;
import feign.FeignException;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 쿠폰 서비스 호출 어댑터 — OpenFeign 전송 + 봉투 언랩 + 예외 변환.
@Component
@RequiredArgsConstructor
public class CouponClient {

  private final CouponFeignClient feign;

  // 선점(검증 + 할인 계산). 적용 불가(최소금액·만료·소유/상태)는 4xx → COUPON_NOT_APPLICABLE로 주문 거절.
  public BigDecimal reserve(Long userCouponId, Long userId, String orderNo, BigDecimal orderAmount) {
    try {
      ApiEnvelope<CouponReserveView> response = feign.reserve(
          new CouponReserveRequest(userCouponId, userId, orderNo, orderAmount));
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

  public void confirm(String orderNo) {
    safe(() -> feign.confirm(new OrderRefRequest(orderNo)));
  }

  public void release(String orderNo) {
    safe(() -> feign.release(new OrderRefRequest(orderNo)));
  }

  // 사용 확정된 쿠폰까지 되돌린다(결제 완료 주문의 사용자 취소).
  public void restore(String orderNo) {
    safe(() -> feign.restore(new OrderRefRequest(orderNo)));
  }

  private void safe(Runnable call) {
    try {
      call.run();
    } catch (FeignException e) {
      throw new BusinessException(ErrorCode.DEPENDENT_SERVICE_ERROR);
    }
  }
}
