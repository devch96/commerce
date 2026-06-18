package com.sparta.copa.copacoupon.coupon.service;

import com.sparta.copa.copacoupon.common.enums.UserCouponStatus;
import com.sparta.copa.copacoupon.common.exception.BusinessException;
import com.sparta.copa.copacoupon.common.exception.ErrorCode;
import com.sparta.copa.copacoupon.coupon.domain.Coupon;
import com.sparta.copa.copacoupon.coupon.domain.UserCoupon;
import com.sparta.copa.copacoupon.coupon.dto.request.CouponCreateRequest;
import com.sparta.copa.copacoupon.coupon.dto.request.CouponReserveRequest;
import com.sparta.copa.copacoupon.coupon.dto.response.CouponReserveResponse;
import com.sparta.copa.copacoupon.coupon.dto.response.CouponResponse;
import com.sparta.copa.copacoupon.coupon.dto.response.UserCouponResponse;
import com.sparta.copa.copacoupon.common.enums.CouponStatus;
import com.sparta.copa.copacoupon.coupon.repository.CouponRepository;
import com.sparta.copa.copacoupon.coupon.repository.UserCouponRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 정의 관리(ADMIN)·발급·조회 + 주문 Saga용 선점/사용/해제/복원.
 * 한정 수량 발급과 사용 상태 전이는 비관적 락으로 직렬화하고, (couponId,userId) 유니크로 1인 1매를 보장한다.
 * reserve/confirm/release/restore는 orderId 기준 멱등.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

  private final CouponRepository couponRepository;
  private final UserCouponRepository userCouponRepository;

  @Transactional
  public CouponResponse createCoupon(CouponCreateRequest request) {
    Coupon coupon = couponRepository.save(Coupon.create(
        request.getName(), request.getType(), request.getValue(), request.getMaxDiscount(),
        request.getMinOrderAmount(), request.getExpirationType(), request.getValidDays(),
        request.getStartDate(), request.getEndDate(), request.getTotalQuantity(),
        request.getTargetType()));
    return CouponResponse.from(coupon);
  }

  @Transactional
  public CouponResponse changeStatus(Long couponId, CouponStatus status) {
    Coupon coupon = getCoupon(couponId);
    coupon.changeStatus(status);
    return CouponResponse.from(coupon);
  }

  @Transactional(readOnly = true)
  public List<CouponResponse> getCoupons() {
    return couponRepository.findAll().stream().map(CouponResponse::from).toList();
  }

  /**
   * 쿠폰 발급(사용자당 1매). 발급 행을 비관적 락으로 잠가 한정 수량 초과 발급을 막고,
   * (couponId, userId) 유니크가 동시 중복 발급의 최종 방어선이 된다.
   */
  @Transactional
  public UserCouponResponse issue(Long couponId, Long userId) {
    if (userCouponRepository.existsByCoupon_IdAndUserId(couponId, userId)) {
      throw new BusinessException(ErrorCode.COUPON_ALREADY_ISSUED);
    }
    Coupon coupon = couponRepository.findByIdForUpdate(couponId)
        .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));
    coupon.issueOne();
    UserCoupon userCoupon = userCouponRepository.save(
        UserCoupon.issue(coupon, userId, coupon.resolveExpiry(LocalDateTime.now())));
    return UserCouponResponse.from(userCoupon);
  }

  @Transactional(readOnly = true)
  public List<UserCouponResponse> getMyCoupons(Long userId) {
    return userCouponRepository.findByUserId(userId).stream()
        .map(UserCouponResponse::from).toList();
  }

  /**
   * 선점(검증 + 할인 계산 + ISSUED→RESERVED). 같은 주문으로 이미 선점됐으면 멱등하게 그 결과를 반환한다.
   */
  @Transactional
  public CouponReserveResponse reserve(CouponReserveRequest request) {
    UserCoupon userCoupon = userCouponRepository.findByIdForUpdate(request.getUserCouponId())
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_COUPON_NOT_FOUND));
    if (!userCoupon.isOwnedBy(request.getUserId())) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
    if (userCoupon.isReservedFor(request.getOrderId())) {
      return CouponReserveResponse.from(userCoupon);
    }
    if (userCoupon.isExpired(LocalDateTime.now())) {
      throw new BusinessException(ErrorCode.COUPON_EXPIRED);
    }
    BigDecimal discount = userCoupon.getCoupon().calculateDiscount(request.getOrderAmount());
    userCoupon.reserve(request.getOrderId(), discount);
    return CouponReserveResponse.from(userCoupon);
  }

  // 사용 확정(RESERVED→USED). 결제 성공. 쿠폰 없는 주문이거나 이미 확정이면 멱등 no-op.
  @Transactional
  public void confirm(Long orderId) {
    UserCoupon userCoupon = userCouponRepository.findByOrderIdForUpdate(orderId).orElse(null);
    if (userCoupon == null || userCoupon.isUsedFor(orderId)) {
      return;
    }
    userCoupon.use(orderId);
  }

  // 선점 해제(RESERVED→ISSUED). 결제 전 실패 보상. 선점이 없으면 멱등 no-op.
  @Transactional
  public void release(Long orderId) {
    UserCoupon userCoupon = userCouponRepository.findByOrderIdForUpdate(orderId).orElse(null);
    if (userCoupon == null || userCoupon.getStatus() != UserCouponStatus.RESERVED) {
      return;
    }
    userCoupon.release();
  }

  // 사용 복원(USED/RESERVED→ISSUED). 결제 완료 주문의 사용자 취소. 이미 ISSUED면 멱등 no-op.
  @Transactional
  public void restore(Long orderId) {
    UserCoupon userCoupon = userCouponRepository.findByOrderIdForUpdate(orderId).orElse(null);
    if (userCoupon == null || userCoupon.getStatus() == UserCouponStatus.ISSUED) {
      return;
    }
    userCoupon.restore();
  }

  private Coupon getCoupon(Long couponId) {
    return couponRepository.findById(couponId)
        .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));
  }
}
