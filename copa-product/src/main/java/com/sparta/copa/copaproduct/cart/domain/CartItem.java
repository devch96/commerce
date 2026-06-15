package com.sparta.copa.copaproduct.cart.domain;

import com.sparta.copa.copaproduct.product.domain.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

// 회원 장바구니 항목. 한 회원의 같은 상품·옵션은 한 행(수량 누적). 상품은 다대일 단방향(@ManyToOne)으로 참조.
@Entity
@Getter
@Table(name = "cart_items")
@EntityListeners(AuditingEntityListener.class)
@DynamicInsert
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  // 상품은 soft delete되어 항상 존재하므로 그냥 다대일 단방향으로 참조한다.
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  // 선택한 옵션 경로(예: 색상:네이비/사이즈:M). 옵션 없는 상품은 빈 문자열("").
  // 빈 문자열을 쓰는 이유: 유니크 (user_id, product_id, option_key)에서 NULL은 중복 허용이라 누적이 깨진다.
  @Column(name = "option_key", nullable = false, length = 255)
  private String optionKey;

  @Column(nullable = false)
  private Integer quantity;

  @CreatedDate
  @Column(name = "added_at", updatable = false)
  private LocalDateTime addedAt;

  @Builder
  private CartItem(Long userId, Product product, String optionKey, Integer quantity) {
    this.userId = userId;
    this.product = product;
    this.optionKey = optionKey;
    this.quantity = quantity;
  }

  public static CartItem create(Long userId, Product product, String optionKey, int quantity) {
    return CartItem.builder()
        .userId(userId)
        .product(product)
        .optionKey(optionKey)
        .quantity(quantity)
        .build();
  }

  // 담기: 기존 수량에 누적.
  public void addQuantity(int quantity) {
    this.quantity += quantity;
  }

  // 수량 변경: 절대값으로 설정.
  public void changeQuantity(int quantity) {
    this.quantity = quantity;
  }
}
