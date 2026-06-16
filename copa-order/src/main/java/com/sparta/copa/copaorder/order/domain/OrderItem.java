package com.sparta.copa.copaorder.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

// 주문 품목. 주문 시점 가격(옵션 할인 반영가)을 스냅샷한다. 주문은 다대일 단방향 참조.
@Entity
@Getter
@Table(name = "order_items")
@DynamicInsert
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  // 옵션 경로(옵션 없으면 빈 문자열). 재고 예약 키와 동일.
  @Column(name = "option_key", nullable = false, length = 255)
  private String optionKey;

  @Column(nullable = false)
  private Integer quantity;

  // 주문 시점 단가 스냅샷(옵션 할인 반영).
  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal price;

  @Builder
  private OrderItem(Order order, Long productId, String optionKey, Integer quantity,
      BigDecimal price) {
    this.order = order;
    this.productId = productId;
    this.optionKey = optionKey;
    this.quantity = quantity;
    this.price = price;
  }

  public static OrderItem of(Order order, Long productId, String optionKey, int quantity,
      BigDecimal price) {
    return OrderItem.builder()
        .order(order)
        .productId(productId)
        .optionKey(optionKey == null ? "" : optionKey)
        .quantity(quantity)
        .price(price)
        .build();
  }

  public BigDecimal lineTotal() {
    return price.multiply(BigDecimal.valueOf(quantity));
  }
}
