package com.sparta.copa.copaproduct.search.dto.response;

import com.sparta.copa.copaproduct.search.document.ProductDocument;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * Elasticsearch 전문 검색 결과 항목. ES 문서에 relevance {@code score}를 덧붙인다.
 */
@Getter
@Builder
public class ProductSearchResult {

  private final Long id;
  private final String productCode;
  private final Long sellerId;
  private final String name;
  private final String description;
  private final BigDecimal price;
  private final String status;
  private final Integer stockQuantity;
  private final List<Long> categoryIds;
  private final LocalDateTime createdAt;
  // Elasticsearch relevance 점수(정렬 근거). 높을수록 질의와 더 잘 맞는다.
  private final Float score;

  public static ProductSearchResult from(ProductDocument document, Float score) {
    return ProductSearchResult.builder()
        .id(document.getId() == null ? null : Long.valueOf(document.getId()))
        .productCode(document.getProductCode())
        .sellerId(document.getSellerId())
        .name(document.getName())
        .description(document.getDescription())
        .price(document.getPrice() == null ? null : BigDecimal.valueOf(document.getPrice()))
        .status(document.getStatus())
        .stockQuantity(document.getStockQuantity())
        .categoryIds(document.getCategoryIds())
        .createdAt(document.getCreatedAt())
        .score(score)
        .build();
  }
}