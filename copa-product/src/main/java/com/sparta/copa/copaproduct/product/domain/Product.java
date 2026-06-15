package com.sparta.copa.copaproduct.product.domain;

import com.sparta.copa.copaproduct.common.converter.OptionDiscountListJsonConverter;
import com.sparta.copa.copaproduct.common.converter.OptionTreeJsonConverter;
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
import java.util.Comparator;
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

  // 옵션(무한 뎁스 JSON 트리). leaf = 선언적 초기 재고. 재고의 권위 원천은 별도 재고 서비스(optionKey 매핑).
  @Convert(converter = OptionTreeJsonConverter.class)
  @Column(name = "options", length = 4000)
  private Map<String, Object> options;

  // 옵션별/옵션 조합별 할인 규칙. 쿠폰은 별도 프로모션 서비스 소관.
  @Convert(converter = OptionDiscountListJsonConverter.class)
  @Column(name = "option_discounts", length = 4000)
  private List<OptionDiscount> optionDiscounts;

  @CreatedDate
  @Column(updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  private LocalDateTime updatedAt;

  @Builder
  private Product(String productCode, Long sellerId, String name, BigDecimal price,
      ProductStatus status, Integer stockQuantity, String description,
      List<String> images, Map<String, String> specs,
      Map<String, Object> options, List<OptionDiscount> optionDiscounts) {
    this.productCode = productCode;
    this.sellerId = sellerId;
    this.name = name;
    this.price = price;
    this.status = status;
    this.stockQuantity = stockQuantity;
    this.description = description;
    this.images = images;
    this.specs = specs;
    this.options = options;
    this.optionDiscounts = optionDiscounts;
  }

  // 신규 상품은 항상 HIDDEN(가공 중)으로 시작한다. 노출은 명시적 상태 변경으로만 이루어진다.
  // 카테고리 연결은 ProductCategory로 별도 저장한다(여기선 다루지 않음).
  public static Product create(Long sellerId, String productCode, String name, BigDecimal price,
      Integer stockQuantity, String description, List<String> images, Map<String, String> specs,
      Map<String, Object> options, List<OptionDiscount> optionDiscounts) {
    validatePrice(price);
    int resolvedStock = validateOptionsAndResolveStock(options, optionDiscounts, stockQuantity);
    return Product.builder()
        .sellerId(sellerId)
        .productCode(productCode)
        .name(name)
        .price(price)
        .status(ProductStatus.HIDDEN)
        .stockQuantity(resolvedStock)
        .description(description)
        .images(images)
        .specs(specs)
        .options(options)
        .optionDiscounts(optionDiscounts)
        .build();
  }

  public void update(String name, BigDecimal price, Integer stockQuantity, String description,
      List<String> images, Map<String, String> specs,
      Map<String, Object> options, List<OptionDiscount> optionDiscounts) {
    validatePrice(price);
    int resolvedStock = validateOptionsAndResolveStock(options, optionDiscounts, stockQuantity);
    this.name = name;
    this.price = price;
    this.stockQuantity = resolvedStock;
    this.description = description;
    this.images = images;
    this.specs = specs;
    this.options = options;
    this.optionDiscounts = optionDiscounts;
  }

  public boolean hasOptions() {
    return options != null && !options.isEmpty();
  }

  /**
   * 옵션 경로(optionKey)를 해석해 선언적 재고와 (옵션 할인 반영) 가격을 돌려준다.
   * 주문이 가격을 스냅샷하거나 재고 서비스가 재고를 시드할 때의 진입점.
   */
  public OptionPrice resolveOption(String optionKey) {
    if (!hasOptions()) {
      if (optionKey != null && !optionKey.isBlank()) {
        throw new BusinessException(ErrorCode.OPTION_NOT_ALLOWED);
      }
      return new OptionPrice("", stockQuantity, price, price);
    }
    if (optionKey == null || optionKey.isBlank()) {
      throw new BusinessException(ErrorCode.OPTION_KEY_REQUIRED);
    }
    Integer stock = ProductOptions.flatten(options).get(optionKey);
    if (stock == null) {
      throw new BusinessException(ErrorCode.OPTION_NOT_FOUND);
    }
    return new OptionPrice(optionKey, stock, price, finalPriceFor(optionKey));
  }

  // 가장 구체적인(경로가 가장 긴) 할인 규칙을 적용한 최종가. 없으면 기준가 그대로.
  private BigDecimal finalPriceFor(String optionKey) {
    if (optionDiscounts == null) {
      return price;
    }
    return optionDiscounts.stream()
        .filter(discount -> discount.matches(optionKey))
        .max(Comparator.comparingInt(OptionDiscount::specificity))
        .map(discount -> discount.applyTo(price))
        .orElse(price);
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

  // 옵션이 있으면 트리를 검증하고 leaf 합계를 재고로 집계, 없으면 단순 stockQuantity를 쓴다.
  // 옵션 할인은 옵션 상품에서만, 실제 옵션 경로를 가리킬 때만 허용한다.
  private static int validateOptionsAndResolveStock(Map<String, Object> options,
      List<OptionDiscount> optionDiscounts, Integer stockQuantity) {
    Map<String, Integer> leaves = ProductOptions.flatten(options);
    if (leaves.isEmpty()) {
      if (optionDiscounts != null && !optionDiscounts.isEmpty()) {
        throw new BusinessException(ErrorCode.OPTION_NOT_ALLOWED);
      }
      validateStock(stockQuantity);
      return stockQuantity;
    }
    validateOptionDiscounts(optionDiscounts, leaves);
    return leaves.values().stream().mapToInt(Integer::intValue).sum();
  }

  private static void validateOptionDiscounts(List<OptionDiscount> optionDiscounts,
      Map<String, Integer> leaves) {
    if (optionDiscounts == null) {
      return;
    }
    for (OptionDiscount discount : optionDiscounts) {
      discount.validate();
      boolean matchesAnyLeaf = leaves.keySet().stream().anyMatch(discount::matches);
      if (!matchesAnyLeaf) {
        throw new BusinessException(ErrorCode.INVALID_OPTION_DISCOUNT);
      }
    }
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
