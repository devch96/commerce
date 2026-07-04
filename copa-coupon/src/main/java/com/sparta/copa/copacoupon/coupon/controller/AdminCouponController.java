package com.sparta.copa.copacoupon.coupon.controller;

import com.sparta.copa.copacoupon.common.exception.BusinessException;
import com.sparta.copa.copacoupon.common.exception.ErrorCode;
import com.sparta.copa.copacoupon.common.response.ApiResponse;
import com.sparta.copa.copacoupon.coupon.dto.request.CouponCreateRequest;
import com.sparta.copa.copacoupon.coupon.dto.request.CouponStatusRequest;
import com.sparta.copa.copacoupon.coupon.dto.response.CouponResponse;
import com.sparta.copa.copacoupon.coupon.service.CouponService;
import com.sparta.copa.copacoupon.fcfs.dto.FcfsOpenRequest;
import com.sparta.copa.copacoupon.fcfs.service.FcfsCouponService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 쿠폰 정의 관리(ADMIN). 게이트웨이가 주입한 X-User-Role로 ADMIN 권한을 한 번 더 검증한다(방어적 설계).
 */
@RestController
@RequestMapping("/admin/coupons")
@RequiredArgsConstructor
public class AdminCouponController {

  private static final String USER_ROLE_HEADER = "X-User-Role";
  private static final String ADMIN_ROLE = "ADMIN";

  private final CouponService couponService;
  private final FcfsCouponService fcfsCouponService;

  @PostMapping
  public ApiResponse<CouponResponse> create(@RequestHeader(USER_ROLE_HEADER) String role,
      @Valid @RequestBody CouponCreateRequest request) {
    requireAdmin(role);
    return ApiResponse.success(couponService.createCoupon(request));
  }

  // 선착순 발급 오픈: 재고를 Redis에 시드한다(quantity 생략 시 기본 1000장).
  @PostMapping("/{couponId}/fcfs/open")
  public ApiResponse<Long> openFcfs(@RequestHeader(USER_ROLE_HEADER) String role,
      @PathVariable Long couponId, @Valid @RequestBody(required = false) FcfsOpenRequest request) {
    requireAdmin(role);
    Integer quantity = request == null ? null : request.getQuantity();
    return ApiResponse.success(fcfsCouponService.open(couponId, quantity));
  }

  @GetMapping
  public ApiResponse<List<CouponResponse>> list(@RequestHeader(USER_ROLE_HEADER) String role) {
    requireAdmin(role);
    return ApiResponse.success(couponService.getCoupons());
  }

  @PatchMapping("/{couponId}/status")
  public ApiResponse<CouponResponse> changeStatus(@RequestHeader(USER_ROLE_HEADER) String role,
      @PathVariable Long couponId, @Valid @RequestBody CouponStatusRequest request) {
    requireAdmin(role);
    return ApiResponse.success(couponService.changeStatus(couponId, request.getStatus()));
  }

  private void requireAdmin(String role) {
    if (!ADMIN_ROLE.equals(role)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
  }
}
