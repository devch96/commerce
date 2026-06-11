package com.sparta.copa.copaproduct.product.dto.response;

import com.sparta.copa.copaproduct.common.enums.ProductStatus;
import com.sparta.copa.copaproduct.product.domain.Product;
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

  private final String id;
  private final String productCode;
  private final Long sellerId;
  private final String name;
  private final Long price;
  private final List<String> categoryIds;
  private final ProductStatus status;
  private final Integer stockQuantity;
  private final String description;
  private final Map<String, String> specs;
  private final LocalDateTime createdAt;
  private final LocalDateTime updatedAt;

  public static ProductResponse from(Product product) {
    return ProductResponse.builder()
        .id(product.getId())
        .productCode(product.getProductCode())
        .sellerId(product.getSellerId())
        .name(product.getName())
        .price(product.getPrice())
        .categoryIds(product.getCategoryIds())
        .status(product.getStatus())
        .stockQuantity(product.getStockQuantity())
        .description(product.getDescription())
        .specs(product.getSpecs())
        .createdAt(product.getCreatedAt())
        .updatedAt(product.getUpdatedAt())
        .build();
  }
}
