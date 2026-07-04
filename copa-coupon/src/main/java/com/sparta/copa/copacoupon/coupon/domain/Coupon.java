package com.sparta.copa.copacoupon.coupon.domain;

import com.sparta.copa.copacoupon.common.enums.CouponStatus;
import com.sparta.copa.copacoupon.common.enums.CouponType;
import com.sparta.copa.copacoupon.common.enums.ExpirationType;
import com.sparta.copa.copacoupon.common.enums.TargetType;
import com.sparta.copa.copacoupon.common.exception.BusinessException;
import com.sparta.copa.copacoupon.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * 쿠폰 정의(템플릿). 발급되면 사용자별 {@link UserCoupon} 인스턴스가 생긴다.
 * 금액은 통화 규약대로 BigDecimal(DECIMAL(19,2)). 정률 할인의 절사는 HALF_UP·소수 2자리.
 */
@Entity
@Getter
@Table(name = "coupons")
@EntityListeners(AuditingEntityListener.class)
@DynamicInsert
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CouponType type;

  // FIXED_AMOUNT면 할인액(원), PERCENTAGE면 할인율(%) — 예: 10.00 = 10%.
  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal value;

  // 정률 할인 상한(PERCENTAGE 전용). 정액이면 null.
  @Column(name = "max_discount", precision = 19, scale = 2)
  private BigDecimal maxDiscount;

  @Column(name = "min_order_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal minOrderAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "expiration_type", nullable = false, length = 20)
  private ExpirationType expirationType;

  // CREATED_PLUS_DAYS·ISSUED_PLUS_DAYS에서 사용. FIXED_RANGE면 null.
  @Column(name = "valid_days")
  private Integer validDays;

  // FIXED_RANGE에서 사용. 그 외 null.
  @Column(name = "start_date")
  private LocalDateTime startDate;

  @Column(name = "end_date")
  private LocalDateTime endDate;

  // 총 발급 가능 수량. null이면 무제한.
  @Column(name = "total_quantity")
  private Integer totalQuantity;

  @Column(name = "issued_quantity", nullable = false)
  private Integer issuedQuantity;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_type", nullable = false, length = 20)
  private TargetType targetType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CouponStatus status;

  // 발급 수량 동시 증가 보호용 낙관적 락(경합 핫패스는 비관적 락 조회로 보강).
  @Version
  private Long version;

  @CreatedDate
  @Column(updatable = false)
  private LocalDateTime createdAt;

  @Builder
  private Coupon(String name, CouponType type, BigDecimal value, BigDecimal maxDiscount,
      BigDecimal minOrderAmount, ExpirationType expirationType, Integer validDays,
      LocalDateTime startDate, LocalDateTime endDate, Integer totalQuantity, TargetType targetType) {
    this.name = name;
    this.type = type;
    this.value = value;
    this.maxDiscount = maxDiscount;
    this.minOrderAmount = minOrderAmount == null ? BigDecimal.ZERO : minOrderAmount;
    this.expirationType = expirationType;
    this.validDays = validDays;
    this.startDate = startDate;
    this.endDate = endDate;
    this.totalQuantity = totalQuantity;
    this.issuedQuantity = 0;
    this.targetType = targetType == null ? TargetType.ALL : targetType;
    this.status = CouponStatus.ACTIVE;
  }

  public static Coupon create(String name, CouponType type, BigDecimal value, BigDecimal maxDiscount,
      BigDecimal minOrderAmount, ExpirationType expirationType, Integer validDays,
      LocalDateTime startDate, LocalDateTime endDate, Integer totalQuantity, TargetType targetType) {
    Coupon coupon = Coupon.builder()
        .name(name).type(type).value(value).maxDiscount(maxDiscount).minOrderAmount(minOrderAmount)
        .expirationType(expirationType).validDays(validDays).startDate(startDate).endDate(endDate)
        .totalQuantity(totalQuantity).targetType(targetType)
        .build();
    coupon.validateDefinition();
    return coupon;
  }

  // 발급 1건 반영. 상태·기간·잔여 수량을 검증하고 발급 수량을 증가시킨다(동시성은 서비스의 비관적 락이 직렬화).
  public void issueOne() {
    if (status != CouponStatus.ACTIVE) {
      throw new BusinessException(ErrorCode.COUPON_NOT_ISSUABLE);
    }
    if (totalQuantity != null && issuedQuantity >= totalQuantity) {
      throw new BusinessException(ErrorCode.COUPON_OUT_OF_STOCK);
    }
    this.issuedQuantity += 1;
  }

  // 선착순(Redis) 발급 반영. 수량 통제는 Redis가 이미 했으므로 상태만 확인하고 발급 수량을 증가시킨다.
  public void markFcfsIssued() {
    if (status != CouponStatus.ACTIVE) {
      throw new BusinessException(ErrorCode.COUPON_NOT_ISSUABLE);
    }
    this.issuedQuantity += 1;
  }

  // 발급 시점 기준 만료 시각 산정.
  public LocalDateTime resolveExpiry(LocalDateTime issuedAt) {
    return switch (expirationType) {
      case CREATED_PLUS_DAYS -> createdAt.plusDays(validDays);
      case ISSUED_PLUS_DAYS -> issuedAt.plusDays(validDays);
      case FIXED_RANGE -> endDate;
    };
  }

  /**
   * 주문 라인 합계(옵션 할인 반영가의 합)에 대한 쿠폰 할인액 계산.
   * 최소 주문 금액 미달이면 거부. 할인이 합계를 넘지 않도록 클램프한다.
   */
  public BigDecimal calculateDiscount(BigDecimal lineTotal) {
    if (lineTotal.compareTo(minOrderAmount) < 0) {
      throw new BusinessException(ErrorCode.COUPON_MIN_ORDER_NOT_MET);
    }
    BigDecimal discount = switch (type) {
      case FIXED_AMOUNT -> value;
      case PERCENTAGE -> {
        BigDecimal raw = lineTotal.multiply(value)
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        yield (maxDiscount != null && raw.compareTo(maxDiscount) > 0) ? maxDiscount : raw;
      }
    };
    return discount.min(lineTotal).setScale(2, RoundingMode.HALF_UP);
  }

  public void changeStatus(CouponStatus next) {
    this.status = next;
  }

  // 쿠폰 정의 정합성: 금액/율 양수, 타입별 필수 필드, 유효기간 방식별 필수 필드.
  private void validateDefinition() {
    if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
      throw new BusinessException(ErrorCode.INVALID_COUPON_DEFINITION);
    }
    if (type == CouponType.PERCENTAGE
        && (value.compareTo(BigDecimal.valueOf(100)) > 0
        || maxDiscount == null || maxDiscount.compareTo(BigDecimal.ZERO) <= 0)) {
      throw new BusinessException(ErrorCode.INVALID_COUPON_DEFINITION);
    }
    boolean rangeMissing = expirationType == ExpirationType.FIXED_RANGE
        && (startDate == null || endDate == null || !endDate.isAfter(startDate));
    boolean daysMissing = expirationType != ExpirationType.FIXED_RANGE
        && (validDays == null || validDays <= 0);
    if (rangeMissing || daysMissing) {
      throw new BusinessException(ErrorCode.INVALID_COUPON_DEFINITION);
    }
    if (totalQuantity != null && totalQuantity <= 0) {
      throw new BusinessException(ErrorCode.INVALID_COUPON_DEFINITION);
    }
  }
}
