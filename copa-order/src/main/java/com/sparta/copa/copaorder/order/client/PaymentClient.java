package com.sparta.copa.copaorder.order.client;

import com.sparta.copa.copaorder.common.exception.BusinessException;
import com.sparta.copa.copaorder.common.exception.ErrorCode;
import com.sparta.copa.copaorder.order.client.dto.ApiEnvelope;
import com.sparta.copa.copaorder.order.client.dto.PaymentView;
import com.sparta.copa.copaorder.order.client.feign.PaymentFeignClient;
import feign.FeignException;
import java.math.BigDecimal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 결제 서비스 호출 어댑터 — OpenFeign 전송 + 봉투 언랩 + 예외 변환.
@Component
@RequiredArgsConstructor
public class PaymentClient {

  private final PaymentFeignClient feign;

  public PaymentView pay(Long orderId, Long userId, BigDecimal amount) {
    try {
      ApiEnvelope<PaymentView> response =
          feign.pay(Map.of("orderId", orderId, "userId", userId, "amount", amount));
      if (response == null || response.getData() == null) {
        throw new BusinessException(ErrorCode.DEPENDENT_SERVICE_ERROR);
      }
      return response.getData();
    } catch (FeignException e) {
      throw new BusinessException(ErrorCode.DEPENDENT_SERVICE_ERROR);
    }
  }

  public void cancel(Long orderId) {
    try {
      feign.cancel(orderId);
    } catch (FeignException e) {
      throw new BusinessException(ErrorCode.DEPENDENT_SERVICE_ERROR);
    }
  }
}
