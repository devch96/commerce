package com.sparta.copa.copaproduct.search.dto.request;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Elasticsearch 전문 검색 조건. keyword는 상품명·설명에 대한 multi_match(오타 교정 fuzziness)로
 * relevance 점수 기반 검색을 하고, 나머지(카테고리·가격범위)는 filter로 좁힌다.
 *
 * <p>불변 DTO로, 컨트롤러가 별도 애노테이션 없이 생성자 바인딩으로 쿼리 파라미터를 받는다.
 */
@Getter
@Builder
@AllArgsConstructor
public class ProductEsSearchCondition {

  private final String keyword;
  private final Long categoryId;
  private final BigDecimal minPrice;
  private final BigDecimal maxPrice;
}