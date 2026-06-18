package com.sparta.copa.copacoupon.coupon.controller;

import com.sparta.copa.copacoupon.common.response.ApiResponse;
import com.sparta.copa.copacoupon.coupon.dto.response.UserCouponResponse;
import com.sparta.copa.copacoupon.coupon.service.CouponService;
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

  @PostMapping("/{couponId}/issue")
  public ApiResponse<UserCouponResponse> issue(@RequestHeader(USER_ID_HEADER) Long userId,
      @PathVariable Long couponId) {
    return ApiResponse.success(couponService.issue(couponId, userId));
  }

  @GetMapping("/me")
  public ApiResponse<List<UserCouponResponse>> myCoupons(
      @RequestHeader(USER_ID_HEADER) Long userId) {
    return ApiResponse.success(couponService.getMyCoupons(userId));
  }
}
