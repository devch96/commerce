package com.sparta.copa.copaproduct.product.controller;

import com.sparta.copa.copaproduct.common.response.ApiResponse;
import com.sparta.copa.copaproduct.product.dto.request.ProductSearchCondition;
import com.sparta.copa.copaproduct.product.dto.response.ProductResponse;
import com.sparta.copa.copaproduct.product.service.ProductService;
import com.sparta.copa.copaproduct.search.dto.request.ProductEsSearchCondition;
import com.sparta.copa.copaproduct.search.dto.response.ProductAggregationResponse;
import com.sparta.copa.copaproduct.search.dto.response.ProductSearchResult;
import com.sparta.copa.copaproduct.search.service.ProductEsSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상품 검색 엔드포인트(비로그인 공개, 게이트웨이 화이트리스트 {@code GET /products/**}).
 *
 * <ul>
 *   <li>{@code GET /products/search} — QueryDSL 동적 검색(MySQL 정형 필터·정렬)</li>
 *   <li>{@code GET /products/search/es} — Elasticsearch 전문 검색(relevance·오타 교정)</li>
 *   <li>{@code GET /products/search/es/aggregations} — Elasticsearch 집계(가격 통계·카테고리 분포)</li>
 * </ul>
 */
@RestController
@RequestMapping("/products/search")
@RequiredArgsConstructor
public class ProductSearchController {

  private final ProductService productService;
  private final ProductEsSearchService productEsSearchService;

  // QueryDSL 동적 검색: 조건은 쿼리 파라미터(keyword·categoryId·minPrice·maxPrice)를 DTO 생성자로 바인딩,
  // 정렬은 ?sort=price,desc 처럼 price·name·createdAt만 허용된다.
  @GetMapping
  public ApiResponse<Page<ProductResponse>> search(
      ProductSearchCondition condition,
      @PageableDefault(size = 20) Pageable pageable) {
    return ApiResponse.success(productService.searchProducts(condition, pageable));
  }

  // Elasticsearch 전문 검색: keyword를 상품명·설명에 매칭하고 relevance 점수순으로 정렬한다.
  @GetMapping("/es")
  public ApiResponse<Page<ProductSearchResult>> searchByElasticsearch(
      ProductEsSearchCondition condition,
      @PageableDefault(size = 20) Pageable pageable) {
    return ApiResponse.success(productEsSearchService.search(condition, pageable));
  }

  // Elasticsearch 집계: 현재 검색 조건에 매칭되는 상품들의 가격 통계·카테고리 분포.
  @GetMapping("/es/aggregations")
  public ApiResponse<ProductAggregationResponse> aggregations(
      ProductEsSearchCondition condition) {
    return ApiResponse.success(productEsSearchService.aggregate(condition));
  }
}