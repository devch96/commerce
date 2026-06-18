package com.sparta.copa.copacoupon.coupon.repository;

import com.sparta.copa.copacoupon.coupon.domain.Coupon;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

  // 발급 핫패스: 한정 수량의 issuedQuantity 증가를 비관적 락으로 직렬화한다(초과 발급 0).
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Coupon c where c.id = :id")
  Optional<Coupon> findByIdForUpdate(@Param("id") Long id);
}
