package com.sparta.copa.copaproduct.product.dto.response;

import com.sparta.copa.copaproduct.common.enums.ProductStatus;
import com.sparta.copa.copaproduct.product.domain.Product;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

// @Jacksonized: Redis 캐시 JSON → ProductResponse 역직렬화를 위해 빌더를 Jackson이 쓸 수 있게 한다.
@Getter
@Builder
@Jacksonized
public class ProductResponse {

  private final Long id;
  private final String productCode;
  private final Long sellerId;
  private final String name;
  private final BigDecimal price;
  private final List<Long> categoryIds;
  private final ProductStatus status;
  private final Integer stockQuantity;
  private final String description;
  private final List<String> images;
  private final Map<String, String> specs;
  private final LocalDateTime createdAt;
  private final LocalDateTime updatedAt;

  // 카테고리 id는 조인 엔티티(ProductCategory)에서 조회해 주입한다(Product가 직접 들지 않음).
  public static ProductResponse from(Product product, List<Long> categoryIds) {
    return ProductResponse.builder()
        .id(product.getId())
        .productCode(product.getProductCode())
        .sellerId(product.getSellerId())
        .name(product.getName())
        .price(product.getPrice())
        .categoryIds(categoryIds)
        .status(product.getStatus())
        .stockQuantity(product.getStockQuantity())
        .description(product.getDescription())
        .images(product.getImages())
        .specs(product.getSpecs())
        .createdAt(product.getCreatedAt())
        .updatedAt(product.getUpdatedAt())
        .build();
  }
}
