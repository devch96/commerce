package com.sparta.copa.copacoupon.coupon.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.copa.copacoupon.common.enums.CouponType;
import com.sparta.copa.copacoupon.common.enums.ExpirationType;
import com.sparta.copa.copacoupon.common.enums.TargetType;
import com.sparta.copa.copacoupon.common.exception.BusinessException;
import com.sparta.copa.copacoupon.common.exception.ErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CouponTest {

  private Coupon fixed(long value, long minOrder) {
    return Coupon.create("정액", CouponType.FIXED_AMOUNT, BigDecimal.valueOf(value), null,
        BigDecimal.valueOf(minOrder), ExpirationType.ISSUED_PLUS_DAYS, 30, null, null, 100,
        TargetType.ALL);
  }

  private Coupon percent(long rate, long maxDiscount) {
    return Coupon.create("정률", CouponType.PERCENTAGE, BigDecimal.valueOf(rate),
        BigDecimal.valueOf(maxDiscount), BigDecimal.ZERO, ExpirationType.ISSUED_PLUS_DAYS, 30,
        null, null, 100, TargetType.ALL);
  }

  @Test
  @DisplayName("정액 할인은 value를 그대로, 단 라인합계를 넘지 않게 클램프한다")
  void fixedDiscount() {
    assertThat(fixed(3000, 0).calculateDiscount(BigDecimal.valueOf(10000)))
        .isEqualByComparingTo("3000");
    assertThat(fixed(3000, 0).calculateDiscount(BigDecimal.valueOf(2000)))
        .isEqualByComparingTo("2000");
  }

  @Test
  @DisplayName("정률 할인은 maxDiscount로 상한을 둔다")
  void percentageDiscountCapped() {
    assertThat(percent(10, 5000).calculateDiscount(BigDecimal.valueOf(30000)))
        .isEqualByComparingTo("3000");
    assertThat(percent(10, 5000).calculateDiscount(BigDecimal.valueOf(100000)))
        .isEqualByComparingTo("5000");
  }

  @Test
  @DisplayName("최소 주문 금액 미달이면 거부한다")
  void minOrderNotMet() {
    assertThatThrownBy(() -> fixed(3000, 50000).calculateDiscount(BigDecimal.valueOf(10000)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.COUPON_MIN_ORDER_NOT_MET);
  }

  @Test
  @DisplayName("정률 쿠폰은 maxDiscount가 없으면 정의 오류")
  void percentageRequiresMaxDiscount() {
    assertThatThrownBy(() -> Coupon.create("정률", CouponType.PERCENTAGE, BigDecimal.TEN, null,
        BigDecimal.ZERO, ExpirationType.ISSUED_PLUS_DAYS, 30, null, null, 100, TargetType.ALL))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.INVALID_COUPON_DEFINITION);
  }

  @Test
  @DisplayName("기간 방식이 일수인데 validDays가 없으면 정의 오류")
  void validDaysRequired() {
    assertThatThrownBy(() -> Coupon.create("정액", CouponType.FIXED_AMOUNT, BigDecimal.valueOf(1000),
        null, BigDecimal.ZERO, ExpirationType.ISSUED_PLUS_DAYS, null, null, null, 100,
        TargetType.ALL))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.INVALID_COUPON_DEFINITION);
  }

  @Test
  @DisplayName("총 수량 소진 시 발급은 OUT_OF_STOCK")
  void issueOutOfStock() {
    Coupon coupon = Coupon.create("한정", CouponType.FIXED_AMOUNT, BigDecimal.valueOf(1000), null,
        BigDecimal.ZERO, ExpirationType.ISSUED_PLUS_DAYS, 30, null, null, 1, TargetType.ALL);
    coupon.issueOne();
    assertThatThrownBy(coupon::issueOne)
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.COUPON_OUT_OF_STOCK);
  }
}
