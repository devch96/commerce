package com.sparta.copa.copacoupon.coupon.repository;

import com.sparta.copa.copacoupon.coupon.domain.UserCoupon;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

  // 1인 1매 판단(발급 전 선검사, 최종 방어선은 DB 유니크 제약).
  boolean existsByCoupon_IdAndUserId(Long couponId, Long userId);

  List<UserCoupon> findByUserId(Long userId);

  // reserve/use/release: 상태 전이를 비관적 락으로 직렬화(동시 confirm/release 경합 차단).
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select uc from UserCoupon uc where uc.id = :id")
  Optional<UserCoupon> findByIdForUpdate(@Param("id") Long id);

  // 멱등 보상: 주문 기준으로 선점/사용된 쿠폰 조회(orderId 단위 confirm/release).
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select uc from UserCoupon uc where uc.reservedOrderId = :orderId or uc.usedOrderId = :orderId")
  Optional<UserCoupon> findByOrderIdForUpdate(@Param("orderId") Long orderId);
}
