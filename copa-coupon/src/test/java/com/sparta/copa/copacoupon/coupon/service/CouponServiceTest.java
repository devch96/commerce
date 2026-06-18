package com.sparta.copa.copacoupon.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.copa.copacoupon.common.enums.CouponType;
import com.sparta.copa.copacoupon.common.enums.ExpirationType;
import com.sparta.copa.copacoupon.common.enums.TargetType;
import com.sparta.copa.copacoupon.common.enums.UserCouponStatus;
import com.sparta.copa.copacoupon.common.exception.BusinessException;
import com.sparta.copa.copacoupon.common.exception.ErrorCode;
import com.sparta.copa.copacoupon.coupon.domain.Coupon;
import com.sparta.copa.copacoupon.coupon.domain.UserCoupon;
import com.sparta.copa.copacoupon.coupon.dto.request.CouponReserveRequest;
import com.sparta.copa.copacoupon.coupon.dto.response.CouponReserveResponse;
import com.sparta.copa.copacoupon.coupon.repository.CouponRepository;
import com.sparta.copa.copacoupon.coupon.repository.UserCouponRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

  @Mock
  private CouponRepository couponRepository;
  @Mock
  private UserCouponRepository userCouponRepository;

  @InjectMocks
  private CouponService couponService;

  private Coupon fixedCoupon() {
    return Coupon.create("정액3천", CouponType.FIXED_AMOUNT, BigDecimal.valueOf(3000), null,
        BigDecimal.valueOf(5000), ExpirationType.ISSUED_PLUS_DAYS, 30, null, null, 100,
        TargetType.ALL);
  }

  private UserCoupon issuedCoupon(long id, long userId) {
    UserCoupon uc = UserCoupon.issue(fixedCoupon(), userId, LocalDateTime.now().plusDays(10));
    ReflectionTestUtils.setField(uc, "id", id);
    return uc;
  }

  private CouponReserveRequest reserveRequest(long userCouponId, long userId, long orderId, long amount) {
    CouponReserveRequest req = new CouponReserveRequest();
    ReflectionTestUtils.setField(req, "userCouponId", userCouponId);
    ReflectionTestUtils.setField(req, "userId", userId);
    ReflectionTestUtils.setField(req, "orderId", orderId);
    ReflectionTestUtils.setField(req, "orderAmount", BigDecimal.valueOf(amount));
    return req;
  }

  @Test
  @DisplayName("이미 발급받은 쿠폰이면 발급은 COUPON_ALREADY_ISSUED")
  void issueAlreadyIssued() {
    given(userCouponRepository.existsByCoupon_IdAndUserId(1L, 7L)).willReturn(true);

    assertThatThrownBy(() -> couponService.issue(1L, 7L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.COUPON_ALREADY_ISSUED);

    verify(couponRepository, never()).findByIdForUpdate(any());
  }

  @Test
  @DisplayName("선점은 검증 후 할인을 계산하고 RESERVED로 전환한다")
  void reserveComputesDiscount() {
    UserCoupon uc = issuedCoupon(10L, 7L);
    given(userCouponRepository.findByIdForUpdate(10L)).willReturn(Optional.of(uc));

    CouponReserveResponse response = couponService.reserve(reserveRequest(10L, 7L, 100L, 30000));

    assertThat(response.getDiscountAmount()).isEqualByComparingTo("3000");
    assertThat(response.getOrderId()).isEqualTo(100L);
    assertThat(uc.getStatus()).isEqualTo(UserCouponStatus.RESERVED);
  }

  @Test
  @DisplayName("다른 사용자의 쿠폰 선점은 ACCESS_DENIED")
  void reserveNotOwned() {
    given(userCouponRepository.findByIdForUpdate(10L)).willReturn(Optional.of(issuedCoupon(10L, 7L)));

    assertThatThrownBy(() -> couponService.reserve(reserveRequest(10L, 999L, 100L, 30000)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
  }

  @Test
  @DisplayName("같은 주문으로 이미 선점된 쿠폰은 멱등하게 그 결과를 반환한다")
  void reserveIdempotent() {
    UserCoupon uc = issuedCoupon(10L, 7L);
    uc.reserve(100L, BigDecimal.valueOf(3000));
    given(userCouponRepository.findByIdForUpdate(10L)).willReturn(Optional.of(uc));

    CouponReserveResponse response = couponService.reserve(reserveRequest(10L, 7L, 100L, 30000));

    assertThat(response.getDiscountAmount()).isEqualByComparingTo("3000");
    assertThat(uc.getStatus()).isEqualTo(UserCouponStatus.RESERVED);
  }

  @Test
  @DisplayName("확정은 RESERVED 쿠폰을 USED로 전환한다(멱등)")
  void confirmUsesCoupon() {
    UserCoupon uc = issuedCoupon(10L, 7L);
    uc.reserve(100L, BigDecimal.valueOf(3000));
    given(userCouponRepository.findByOrderIdForUpdate(100L)).willReturn(Optional.of(uc));

    couponService.confirm(100L);

    assertThat(uc.getStatus()).isEqualTo(UserCouponStatus.USED);
  }

  @Test
  @DisplayName("쿠폰 없는 주문의 확정/해제는 멱등하게 아무것도 하지 않는다")
  void confirmReleaseNoOpWhenAbsent() {
    given(userCouponRepository.findByOrderIdForUpdate(100L)).willReturn(Optional.empty());

    couponService.confirm(100L);
    couponService.release(100L);
  }

  @Test
  @DisplayName("해제는 RESERVED 쿠폰을 ISSUED로 되돌린다")
  void releaseReturnsToIssued() {
    UserCoupon uc = issuedCoupon(10L, 7L);
    uc.reserve(100L, BigDecimal.valueOf(3000));
    given(userCouponRepository.findByOrderIdForUpdate(100L)).willReturn(Optional.of(uc));

    couponService.release(100L);

    assertThat(uc.getStatus()).isEqualTo(UserCouponStatus.ISSUED);
    assertThat(uc.getReservedOrderId()).isNull();
  }

  @Test
  @DisplayName("복원은 USED 쿠폰을 ISSUED로 되돌린다")
  void restoreReturnsUsedToIssued() {
    UserCoupon uc = issuedCoupon(10L, 7L);
    uc.reserve(100L, BigDecimal.valueOf(3000));
    uc.use(100L);
    given(userCouponRepository.findByOrderIdForUpdate(100L)).willReturn(Optional.of(uc));

    couponService.restore(100L);

    assertThat(uc.getStatus()).isEqualTo(UserCouponStatus.ISSUED);
    assertThat(uc.getUsedOrderId()).isNull();
  }
}
