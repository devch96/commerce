package com.sparta.copa.copainventory.inventory.domain;

import com.sparta.copa.copainventory.common.enums.ReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
 * 주문 단위의 재고 예약 기록. 보상(해제)의 근거이자 멱등성 판단 기준.
 * 주문 한 건이 여러 품목을 가지면 같은 {@code orderId}로 여러 행이 생긴다.
 */
@Entity
@Getter
@Table(name = "stock_reservation")
@EntityListeners(AuditingEntityListener.class)
@DynamicInsert
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockReservation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 멱등 키. 같은 주문의 중복 예약 이벤트를 막는 기준.
  @Column(name = "order_id", nullable = false)
  private Long orderId;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(name = "option_key", nullable = false, length = 255)
  private String optionKey;

  @Column(nullable = false)
  private Integer quantity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ReservationStatus status;

  // 결제 미완료 시 자동 해제 대상이 되는 만료 시각.
  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @CreatedDate
  @Column(updatable = false)
  private LocalDateTime createdAt;

  @Builder
  private StockReservation(Long orderId, Long productId, String optionKey, Integer quantity,
      ReservationStatus status, LocalDateTime expiresAt) {
    this.orderId = orderId;
    this.productId = productId;
    this.optionKey = optionKey;
    this.quantity = quantity;
    this.status = status;
    this.expiresAt = expiresAt;
  }

  public static StockReservation reserve(Long orderId, Long productId, String optionKey,
      int quantity, LocalDateTime expiresAt) {
    return StockReservation.builder()
        .orderId(orderId)
        .productId(productId)
        .optionKey(optionKey)
        .quantity(quantity)
        .status(ReservationStatus.RESERVED)
        .expiresAt(expiresAt)
        .build();
  }

  public boolean isReserved() {
    return status == ReservationStatus.RESERVED;
  }

  // 결제 성공 → 확정(가용 재고는 이미 차감 상태라 추가 차감 없음).
  public void confirm() {
    this.status = ReservationStatus.CONFIRMED;
  }

  // 결제 실패/타임아웃/TTL 만료 → 해제(가용 재고 복원은 서비스가 수행).
  public void release() {
    this.status = ReservationStatus.RELEASED;
  }
}
