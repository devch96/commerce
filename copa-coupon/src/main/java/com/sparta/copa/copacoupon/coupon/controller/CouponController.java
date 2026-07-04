package com.sparta.copa.copacoupon.coupon.controller;

import com.sparta.copa.copacoupon.common.response.ApiResponse;
import com.sparta.copa.copacoupon.coupon.dto.response.UserCouponResponse;
import com.sparta.copa.copacoupon.coupon.service.CouponService;
import com.sparta.copa.copacoupon.fcfs.dto.FcfsIssueResponse;
import com.sparta.copa.copacoupon.fcfs.service.FcfsCouponService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자용 쿠폰 발급·조회. 인증은 게이트웨이가 처리하고 X-User-Id를 주입한다.
 */
@RestController
@RequestMapping("/coupons")
@RequiredArgsConstructor
public class CouponController {

  private static final String USER_ID_HEADER = "X-User-Id";

  private final CouponService couponService;
  private final FcfsCouponService fcfsCouponService;

  @PostMapping("/{couponId}/issue")
  public ApiResponse<UserCouponResponse> issue(@RequestHeader(USER_ID_HEADER) Long userId,
      @PathVariable Long couponId) {
    return ApiResponse.success(couponService.issue(couponId, userId));
  }

  // 선착순 발급: Redis 원자 발급으로 재고 통제, DB 반영은 Kafka로 비동기 처리(설계 08-B).
  @PostMapping("/{couponId}/issue-fcfs")
  public ApiResponse<FcfsIssueResponse> issueFcfs(@RequestHeader(USER_ID_HEADER) Long userId,
      @PathVariable Long couponId) {
    return ApiResponse.success(fcfsCouponService.issue(couponId, userId));
  }

  @GetMapping("/me")
  public ApiResponse<List<UserCouponResponse>> myCoupons(
      @RequestHeader(USER_ID_HEADER) Long userId) {
    return ApiResponse.success(couponService.getMyCoupons(userId));
  }
}
