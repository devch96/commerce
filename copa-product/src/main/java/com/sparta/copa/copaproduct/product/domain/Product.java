package com.sparta.copa.copaproduct.product.domain;

import com.sparta.copa.copaproduct.common.enums.ProductStatus;
import com.sparta.copa.copaproduct.common.exception.BusinessException;
import com.sparta.copa.copaproduct.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Document(collection = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

  @Id
  private String id;

  // 시스템 전역 유니크 식별자. 'PROD-<연도>-<UUID>' 규격(.clauderules 커머스 규칙).
  @Indexed(unique = true)
  private String productCode;

  // 상품을 등록한 판매자(회원 id). 본인 또는 ADMIN만 수정/삭제 가능.
  @Indexed
  private Long sellerId;

  private String name;

  private Long price;

  // 셀러가 선택한 카테고리(원본). 표시·편집용.
  private List<String> categoryIds;

  // 선택 카테고리 + 모든 조상의 클로저. 무한 트리에서 상위 카테고리로 필터해도 하위 상품이 잡히도록
  // 비정규화 저장한다. 카테고리 필터는 이 멀티키 인덱스로 한 번에 처리.
  @Indexed
  private List<String> categoryPathIds;

  private ProductStatus status;

  private Integer stockQuantity;

  private String description;

  // 상품 종류마다 다른 유연 스펙. MongoDB 문서에 그대로 중첩 저장된다.
  private Map<String, String> specs;

  @CreatedDate
  @Field(write = Field.Write.NON_NULL)
  private LocalDateTime createdAt;

  @LastModifiedDate
  private LocalDateTime updatedAt;

  @Builder
  private Product(String productCode, Long sellerId, String name, Long price,
      List<String> categoryIds, List<String> categoryPathIds, ProductStatus status,
      Integer stockQuantity, String description, Map<String, String> specs) {
    this.productCode = productCode;
    this.sellerId = sellerId;
    this.name = name;
    this.price = price;
    this.categoryIds = categoryIds;
    this.categoryPathIds = categoryPathIds;
    this.status = status;
    this.stockQuantity = stockQuantity;
    this.description = description;
    this.specs = specs;
  }

  // 신규 상품은 항상 HIDDEN(가공 중)으로 시작한다. 노출은 명시적 상태 변경으로만 이루어진다.
  // categoryIds=셀러 선택, categoryPathIds=조상까지 펼친 클로저(필터용).
  public static Product create(Long sellerId, String productCode, String name, Long price,
      List<String> categoryIds, List<String> categoryPathIds, Integer stockQuantity,
      String description, Map<String, String> specs) {
    validatePrice(price);
    validateStock(stockQuantity);
    return Product.builder()
        .sellerId(sellerId)
        .productCode(productCode)
        .name(name)
        .price(price)
        .categoryIds(categoryIds)
        .categoryPathIds(categoryPathIds)
        .status(ProductStatus.HIDDEN)
        .stockQuantity(stockQuantity)
        .description(description)
        .specs(specs)
        .build();
  }

  public void update(String name, Long price, List<String> categoryIds,
      List<String> categoryPathIds, Integer stockQuantity, String description,
      Map<String, String> specs) {
    validatePrice(price);
    validateStock(stockQuantity);
    this.name = name;
    this.price = price;
    this.categoryIds = categoryIds;
    this.categoryPathIds = categoryPathIds;
    this.stockQuantity = stockQuantity;
    this.description = description;
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

  private static void validatePrice(Long price) {
    if (price == null || price < 0) {
      throw new BusinessException(ErrorCode.INVALID_PRODUCT_PRICE);
    }
  }

  private static void validateStock(Integer stockQuantity) {
    if (stockQuantity == null || stockQuantity < 0) {
      throw new BusinessException(ErrorCode.INVALID_PRODUCT_STOCK);
    }
  }
}
