package com.sparta.copa.copaorder.order.domain;

import com.sparta.copa.copaorder.common.enums.OrderStatus;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

// 주문 상태 변경 이력(감사 추적). 어드민 변경 포함.
@Entity
@Getter
@Table(name = "order_status_history")
@EntityListeners(AuditingEntityListener.class)
@DynamicInsert
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderStatusHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_id", nullable = false)
  private Long orderId;

  // 최초 생성 이력은 from이 null.
  @Enumerated(EnumType.STRING)
  @Column(name = "from_status", length = 30)
  private OrderStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_status", nullable = false, length = 30)
  private OrderStatus toStatus;

  @Column(length = 200)
  private String reason;

  @CreatedDate
  @Column(name = "changed_at", updatable = false)
  private LocalDateTime changedAt;

  @Builder
  private OrderStatusHistory(Long orderId, OrderStatus fromStatus, OrderStatus toStatus,
      String reason) {
    this.orderId = orderId;
    this.fromStatus = fromStatus;
    this.toStatus = toStatus;
    this.reason = reason;
  }

  public static OrderStatusHistory of(Long orderId, OrderStatus fromStatus, OrderStatus toStatus,
      String reason) {
    return OrderStatusHistory.builder()
        .orderId(orderId)
        .fromStatus(fromStatus)
        .toStatus(toStatus)
        .reason(reason)
        .build();
  }
}
