package com.sparta.copa.copaproduct.product.domain;

import com.sparta.copa.copaproduct.category.domain.Category;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

// 상품-카테고리 다대다를 .clauderules(@ManyToOne 단방향만) 규약에 맞춰 조인 엔티티로 표현한다.
// Product는 컬렉션을 들지 않고, 이 엔티티 레포지토리에서 FK 조건으로 조회한다.
@Entity
@Getter
@Table(name = "product_categories")
@DynamicInsert
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductCategory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id")
  private Product product;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "category_id")
  private Category category;

  @Builder
  private ProductCategory(Product product, Category category) {
    this.product = product;
    this.category = category;
  }

  public static ProductCategory of(Product product, Category category) {
    return ProductCategory.builder().product(product).category(category).build();
  }
}
