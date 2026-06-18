package com.sparta.copa.copacoupon.coupon.domain;

import com.sparta.copa.copacoupon.common.enums.UserCouponStatus;
import com.sparta.copa.copacoupon.common.exception.BusinessException;
import com.sparta.copa.copacoupon.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 사용자에게 발급된 쿠폰 인스턴스. (coupon_id, user_id) 유니크로 1인 1매를 보장한다.
 * 주문 Saga가 orderId 기준으로 reserve→use/release 하며, 각 전이는 멱등하다.
 */
@Entity
@Getter
@Table(name = "user_coupons")
@EntityListeners(AuditingEntityListener.class)
@DynamicInsert
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCoupon {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // UserCoupon -> Coupon 단방향 ManyToOne. FK(coupon_id)는 자식 테이블이 소유한다.
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "coupon_id", nullable = false)
  private Coupon coupon;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserCouponStatus status;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  // 선점/사용된 주문. 멱등 판단 기준(같은 orderId면 재처리하지 않는다).
  @Column(name = "reserved_order_id")
  private Long reservedOrderId;

  @Column(name = "used_order_id")
  private Long usedOrderId;

  // reserve 시 계산된 할인액(보상 시 null). 멱등 reserve가 동일 값을 반환하도록 보존.
  @Column(name = "discount_amount", precision = 19, scale = 2)
  private BigDecimal discountAmount;

  @Version
  private Long version;

  @CreatedDate
  @Column(name = "issued_at", updatable = false)
  private LocalDateTime issuedAt;

  @Builder
  private UserCoupon(Coupon coupon, Long userId, LocalDateTime expiresAt) {
    this.coupon = coupon;
    this.userId = userId;
    this.status = UserCouponStatus.ISSUED;
    this.expiresAt = expiresAt;
  }

  public static UserCoupon issue(Coupon coupon, Long userId, LocalDateTime expiresAt) {
    return UserCoupon.builder().coupon(coupon).userId(userId).expiresAt(expiresAt).build();
  }

  public boolean isExpired(LocalDateTime now) {
    return now.isAfter(expiresAt);
  }

  public boolean isOwnedBy(Long userId) {
    return this.userId != null && this.userId.equals(userId);
  }

  public boolean isReservedFor(Long orderId) {
    return status == UserCouponStatus.RESERVED && orderId.equals(reservedOrderId);
  }

  public boolean isUsedFor(Long orderId) {
    return status == UserCouponStatus.USED && orderId.equals(usedOrderId);
  }

  // 주문에 선점(ISSUED→RESERVED). 만료/상태 검증은 서비스가 선행한다.
  public void reserve(Long orderId, BigDecimal discountAmount) {
    if (status != UserCouponStatus.ISSUED) {
      throw new BusinessException(ErrorCode.COUPON_NOT_USABLE);
    }
    this.status = UserCouponStatus.RESERVED;
    this.reservedOrderId = orderId;
    this.discountAmount = discountAmount;
  }

  // 사용 확정(RESERVED→USED). 결제 성공 시.
  public void use(Long orderId) {
    if (!isReservedFor(orderId)) {
      throw new BusinessException(ErrorCode.COUPON_NOT_USABLE);
    }
    this.status = UserCouponStatus.USED;
    this.usedOrderId = orderId;
    this.reservedOrderId = null;
  }

  // 선점 해제(RESERVED→ISSUED). 결제 전 실패 보상.
  public void release() {
    this.status = UserCouponStatus.ISSUED;
    this.reservedOrderId = null;
    this.discountAmount = null;
  }

  // 사용 복원(USED→ISSUED). 결제 완료 주문의 사용자 취소 시 쿠폰을 되돌린다.
  public void restore() {
    this.status = UserCouponStatus.ISSUED;
    this.usedOrderId = null;
    this.reservedOrderId = null;
    this.discountAmount = null;
  }
}
