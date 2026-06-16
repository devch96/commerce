package com.sparta.copa.copaorder.order.client;

import com.sparta.copa.copaorder.common.exception.BusinessException;
import com.sparta.copa.copaorder.common.exception.ErrorCode;
import com.sparta.copa.copaorder.order.client.dto.ReserveLine;
import com.sparta.copa.copaorder.order.client.feign.InventoryFeignClient;
import feign.FeignException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 재고 서비스 호출 어댑터 — OpenFeign 전송 + 예외 변환.
@Component
@RequiredArgsConstructor
public class InventoryClient {

  private final InventoryFeignClient feign;

  // 예약. 재고 부족(409)은 OUT_OF_STOCK으로 매핑해 주문이 취소 보상을 타게 한다.
  public void reserve(Long orderId, List<ReserveLine> items) {
    try {
      feign.reserve(Map.of("orderId", orderId, "items", items));
    } catch (FeignException e) {
      if (e.status() == 409) {
        throw new BusinessException(ErrorCode.OUT_OF_STOCK);
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

  // 확정된 예약까지 되돌려 재고 복원(결제 완료 주문의 사용자 취소).
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
