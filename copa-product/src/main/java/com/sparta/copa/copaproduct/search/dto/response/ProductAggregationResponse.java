package com.sparta.copa.copaproduct.search.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * Elasticsearch 집계 결과. 현재 검색 조건에 매칭되는 상품들의 가격 통계(avg/min/max)와
 * 카테고리별 상품 수(terms)를 담는다.
 */
@Getter
@Builder
public class ProductAggregationResponse {

  private final long totalCount;
  private final Double avgPrice;
  private final Double minPrice;
  private final Double maxPrice;
  private final List<CategoryBucket> categories;

  @Getter
  @Builder
  public static class CategoryBucket {

    private final Long categoryId;
    private final long count;
  }
}