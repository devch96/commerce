package com.sparta.copa.copaproduct.product.domain;

import com.sparta.copa.copaproduct.common.converter.StringMapJsonConverter;
import com.sparta.copa.copaproduct.common.enums.ProductStatus;
import com.sparta.copa.copaproduct.common.exception.BusinessException;
import com.sparta.copa.copaproduct.common.exception.ErrorCode;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Getter
@Table(name = "products")
@EntityListeners(AuditingEntityListener.class)
@DynamicInsert
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 시스템 전역 유니크 식별자. 'PROD-<연도>-<UUID>' 규격(.clauderules 커머스 규칙).
  @Column(name = "product_code", nullable = false, updatable = false, length = 60)
  private String productCode;

  // 상품을 등록한 판매자(회원 id). 본인 또는 ADMIN만 수정/삭제 가능.
  @Column(name = "seller_id", nullable = false)
  private Long sellerId;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal price;

  // 카테고리는 Product가 컬렉션으로 들지 않는다. 조인 엔티티 ProductCategory + 레포지토리로 관리(.clauderules).

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ProductStatus status;

  @Column(name = "stock_quantity", nullable = false)
  private Integer stockQuantity;

  @Column(length = 2000)
  private String description;

  // soft delete 플래그. 물리 삭제 대신 true로 표시 — 주문·리뷰·장바구니가 참조하는 상품을 보존한다.
  @Column(nullable = false)
  private boolean deleted;

  // 상품 이미지 URL 목록(대표 이미지는 첫 번째 관례). @OrderColumn으로 표시 순서를 보장한다.
  @ElementCollection
  @BatchSize(size = 100)
  @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
  @OrderColumn(name = "image_order")
  @Column(name = "image_url")
  private List<String> images;

  // 상품 종류마다 다른 유연 스펙. 값 테이블 대신 JSON 컬럼으로 저장한다(MySQL JSON / H2 VARCHAR).
  @Convert(converter = StringMapJsonConverter.class)
  @Column(name = "specs", length = 2000)
  private Map<String, String> specs;

  @CreatedDate
  @Column(updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  private LocalDateTime updatedAt;

  @Builder
  private Product(String productCode, Long sellerId, String name, BigDecimal price,
      ProductStatus status, Integer stockQuantity, String description,
      List<String> images, Map<String, String> specs) {
    this.productCode = productCode;
    this.sellerId = sellerId;
    this.name = name;
    this.price = price;
    this.status = status;
    this.stockQuantity = stockQuantity;
    this.description = description;
    this.images = images;
    this.specs = specs;
  }

  // 신규 상품은 항상 HIDDEN(가공 중)으로 시작한다. 노출은 명시적 상태 변경으로만 이루어진다.
  // 카테고리 연결은 ProductCategory로 별도 저장한다(여기선 다루지 않음).
  public static Product create(Long sellerId, String productCode, String name, BigDecimal price,
      Integer stockQuantity, String description, List<String> images, Map<String, String> specs) {
    validatePrice(price);
    validateStock(stockQuantity);
    return Product.builder()
        .sellerId(sellerId)
        .productCode(productCode)
        .name(name)
        .price(price)
        .status(ProductStatus.HIDDEN)
        .stockQuantity(stockQuantity)
        .description(description)
        .images(images)
        .specs(specs)
        .build();
  }

  public void update(String name, BigDecimal price, Integer stockQuantity, String description,
      List<String> images, Map<String, String> specs) {
    validatePrice(price);
    validateStock(stockQuantity);
    this.name = name;
    this.price = price;
    this.stockQuantity = stockQuantity;
    this.description = description;
    this.images = images;
    this.specs = specs;
  }

  public void changeStatus(ProductStatus newStatus) {
    // 판매(SALE) 상태로 전환하려면 재고가 최소 1개 이상이어야 한다.
    if (newStatus == ProductStatus.SALE && this.stockQuantity < 1) {
      throw new BusinessException(ErrorCode.PRODUCT_NOT_SELLABLE);
    }
    this.status = newStatus;
  }

  public boolean isOwnedBy(Long sellerId) {
    return this.sellerId != null && this.sellerId.equals(sellerId);
  }

  // 물리 삭제 대신 플래그만 세운다.
  public void softDelete() {
    this.deleted = true;
  }

  public boolean isPurchasable() {
    return !deleted && this.status == ProductStatus.SALE
        && this.stockQuantity != null && this.stockQuantity > 0;
  }

  private static void validatePrice(BigDecimal price) {
    if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
      throw new BusinessException(ErrorCode.INVALID_PRODUCT_PRICE);
    }
  }

  private static void validateStock(Integer stockQuantity) {
    if (stockQuantity == null || stockQuantity < 0) {
      throw new BusinessException(ErrorCode.INVALID_PRODUCT_STOCK);
    }
  }
}
