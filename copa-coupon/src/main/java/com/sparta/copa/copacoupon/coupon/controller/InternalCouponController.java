package com.sparta.copa.copacoupon.coupon.controller;

import com.sparta.copa.copacoupon.common.response.ApiResponse;
import com.sparta.copa.copacoupon.coupon.dto.request.CouponReserveRequest;
import com.sparta.copa.copacoupon.coupon.dto.request.OrderReferenceRequest;
import com.sparta.copa.copacoupon.coupon.dto.response.CouponReserveResponse;
import com.sparta.copa.copacoupon.coupon.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 서비스 간 내부 호출용. 게이트웨이를 거치지 않고 주문 Saga가 직접 호출한다.
@RestController
@RequestMapping("/internal/coupons")
@RequiredArgsConstructor
public class InternalCouponController {

  private final CouponService couponService;

  // 선점(검증 + 할인 계산). 최소금액 미달·만료 등은 4xx → 주문 Saga가 보상.
  @PostMapping("/reserve")
  public ApiResponse<CouponReserveResponse> reserve(@Valid @RequestBody CouponReserveRequest request) {
    return ApiResponse.success(couponService.reserve(request));
  }

  // 사용 확정(결제 성공).
  @PostMapping("/confirm")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void confirm(@Valid @RequestBody OrderReferenceRequest request) {
    couponService.confirm(request.getOrderId());
  }

  // 선점 해제(결제 전 실패 보상).
  @PostMapping("/release")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void release(@Valid @RequestBody OrderReferenceRequest request) {
    couponService.release(request.getOrderId());
  }

  // 사용 복원(결제 완료 주문의 사용자 취소).
  @PostMapping("/restore")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void restore(@Valid @RequestBody OrderReferenceRequest request) {
    couponService.restore(request.getOrderId());
  }
}
