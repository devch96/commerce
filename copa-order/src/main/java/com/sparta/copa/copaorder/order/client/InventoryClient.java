package com.sparta.copa.copaorder.order.client;

import com.sparta.copa.copaorder.common.exception.BusinessException;
import com.sparta.copa.copaorder.common.exception.ErrorCode;
import com.sparta.copa.copaorder.order.client.dto.InventoryReserveRequest;
import com.sparta.copa.copaorder.order.client.dto.OrderRefRequest;
import com.sparta.copa.copaorder.order.client.dto.ReserveLine;
import com.sparta.copa.copaorder.order.client.feign.InventoryFeignClient;
import feign.FeignException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 재고 서비스 호출 어댑터 — OpenFeign 전송 + 예외 변환.
@Component
@RequiredArgsConstructor
public class InventoryClient {

  private final InventoryFeignClient feign;

  // 예약. 재고 부족(409)은 OUT_OF_STOCK으로 매핑해 주문이 취소 보상을 타게 한다.
  public void reserve(String orderNo, List<ReserveLine> items) {
    try {
      feign.reserve(new InventoryReserveRequest(orderNo, items));
    } catch (FeignException e) {
      if (e.status() == 409) {
        throw new BusinessException(ErrorCode.OUT_OF_STOCK);
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

  // 확정된 예약까지 되돌려 재고 복원(결제 완료 주문의 사용자 취소).
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
