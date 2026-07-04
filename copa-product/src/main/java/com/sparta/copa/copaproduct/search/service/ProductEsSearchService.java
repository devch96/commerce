package com.sparta.copa.copaproduct.search.service;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.sparta.copa.copaproduct.common.exception.BusinessException;
import com.sparta.copa.copaproduct.common.exception.ErrorCode;
import com.sparta.copa.copaproduct.search.document.ProductDocument;
import com.sparta.copa.copaproduct.search.dto.request.ProductEsSearchCondition;
import com.sparta.copa.copaproduct.search.dto.response.ProductAggregationResponse;
import com.sparta.copa.copaproduct.search.dto.response.ProductSearchResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Elasticsearch 전문 검색·집계. 설계 문서(09)의 bool 쿼리를 그대로 구현한다:
 * <ul>
 *   <li>{@code must}: keyword를 상품명(가중 ^2)·설명에 multi_match(오타 교정 fuzziness=AUTO)</li>
 *   <li>{@code filter}: 노출 상태(SALE·SOLD_OUT)·카테고리·가격 range</li>
 *   <li>{@code aggs}: 가격 avg/min/max, 카테고리별 상품 수(terms)</li>
 * </ul>
 * 색인은 Kafka product-search-events 구독(색인기)으로 채워진다.
 */
@Service
@RequiredArgsConstructor
public class ProductEsSearchService {

  // 공개 카탈로그 노출 상태. HIDDEN·DISCONTINUED는 색인돼 있어도 검색에서 제외한다.
  private static final List<FieldValue> VISIBLE_STATUSES =
      List.of(FieldValue.of("SALE"), FieldValue.of("SOLD_OUT"));
  private static final String FIELD_STATUS = "status";
  private static final String FIELD_PRICE = "price";
  private static final String FIELD_CATEGORY_IDS = "categoryIds";
  private static final String AGG_AVG_PRICE = "avg_price";
  private static final String AGG_MIN_PRICE = "min_price";
  private static final String AGG_MAX_PRICE = "max_price";
  private static final String AGG_BY_CATEGORY = "by_category";

  // ES 정렬 허용 필드(QueryDSL 경로와 동일 정책 — 허용 밖 필드는 무시). name은 text 필드라 정렬 불가.
  private static final Set<String> SORTABLE_FIELDS = Set.of("price", "createdAt");

  private final ElasticsearchOperations elasticsearchOperations;

  public Page<ProductSearchResult> search(ProductEsSearchCondition condition, Pageable pageable) {
    validatePriceRange(condition);
    Pageable sanitized = whitelistSort(pageable);
    NativeQuery query = NativeQuery.builder()
        .withQuery(buildQuery(condition))
        .withPageable(sanitized)
        .build();
    SearchHits<ProductDocument> hits = elasticsearchOperations.search(query, ProductDocument.class);
    List<ProductSearchResult> results = hits.getSearchHits().stream()
        .map(hit -> ProductSearchResult.from(hit.getContent(), hit.getScore()))
        .toList();
    return new PageImpl<>(results, sanitized, hits.getTotalHits());
  }

  public ProductAggregationResponse aggregate(ProductEsSearchCondition condition) {
    validatePriceRange(condition);
    NativeQuery query = NativeQuery.builder()
        .withQuery(buildQuery(condition))
        // 집계만 필요하므로 히트는 받지 않는다(size=0).
        .withMaxResults(0)
        .withAggregation(AGG_AVG_PRICE, Aggregation.of(a -> a.avg(v -> v.field(FIELD_PRICE))))
        .withAggregation(AGG_MIN_PRICE, Aggregation.of(a -> a.min(v -> v.field(FIELD_PRICE))))
        .withAggregation(AGG_MAX_PRICE, Aggregation.of(a -> a.max(v -> v.field(FIELD_PRICE))))
        .withAggregation(AGG_BY_CATEGORY,
            Aggregation.of(a -> a.terms(t -> t.field(FIELD_CATEGORY_IDS).size(50))))
        .build();
    SearchHits<ProductDocument> hits = elasticsearchOperations.search(query, ProductDocument.class);
    ElasticsearchAggregations aggregations = (ElasticsearchAggregations) hits.getAggregations();
    return toAggregationResponse(hits.getTotalHits(), aggregations);
  }

  private Query buildQuery(ProductEsSearchCondition condition) {
    BoolQuery.Builder bool = new BoolQuery.Builder();

    // 노출 상태만.
    bool.filter(f -> f.terms(t -> t.field(FIELD_STATUS)
        .terms(values -> values.value(VISIBLE_STATUSES))));

    // keyword가 있으면 relevance 검색(multi_match + fuzziness), 없으면 전체 매칭.
    if (StringUtils.hasText(condition.getKeyword())) {
      String keyword = condition.getKeyword().trim();
      bool.must(m -> m.multiMatch(mm -> mm.query(keyword)
          .fields("name^2", "description")
          .fuzziness("AUTO")));
    } else {
      bool.must(m -> m.matchAll(all -> all));
    }

    if (condition.getCategoryId() != null) {
      bool.filter(f -> f.term(t -> t.field(FIELD_CATEGORY_IDS)
          .value(FieldValue.of(condition.getCategoryId()))));
    }

    if (condition.getMinPrice() != null || condition.getMaxPrice() != null) {
      // ES 8.15+ RangeQuery는 variant union → 숫자 필드는 number 변형으로 gte/lte(double)를 준다.
      bool.filter(f -> f.range(r -> r.number(n -> {
        n.field(FIELD_PRICE);
        if (condition.getMinPrice() != null) {
          n.gte(condition.getMinPrice().doubleValue());
        }
        if (condition.getMaxPrice() != null) {
          n.lte(condition.getMaxPrice().doubleValue());
        }
        return n;
      })));
    }

    return Query.of(q -> q.bool(bool.build()));
  }

  private ProductAggregationResponse toAggregationResponse(long total,
      ElasticsearchAggregations aggregations) {
    Map<String, ElasticsearchAggregation> byName = aggregations.aggregationsAsMap();
    List<ProductAggregationResponse.CategoryBucket> categories =
        byName.get(AGG_BY_CATEGORY).aggregation().getAggregate().lterms().buckets().array().stream()
            .map(bucket -> ProductAggregationResponse.CategoryBucket.builder()
                .categoryId(bucket.key())
                .count(bucket.docCount())
                .build())
            .toList();
    return ProductAggregationResponse.builder()
        .totalCount(total)
        .avgPrice(nullIfNaN(byName.get(AGG_AVG_PRICE).aggregation().getAggregate().avg().value()))
        .minPrice(nullIfNaN(byName.get(AGG_MIN_PRICE).aggregation().getAggregate().min().value()))
        .maxPrice(nullIfNaN(byName.get(AGG_MAX_PRICE).aggregation().getAggregate().max().value()))
        .categories(categories)
        .build();
  }

  // 매칭 문서가 없으면 avg/min/max는 NaN이므로 null로 정규화한다.
  private Double nullIfNaN(double value) {
    return Double.isNaN(value) ? null : value;
  }

  // 검색 가격 범위 정합성: 음수·역전(min>max)은 400으로 거절한다(QueryDSL 경로와 동일 정책).
  private void validatePriceRange(ProductEsSearchCondition condition) {
    boolean negative = (condition.getMinPrice() != null && condition.getMinPrice().signum() < 0)
        || (condition.getMaxPrice() != null && condition.getMaxPrice().signum() < 0);
    boolean inverted = condition.getMinPrice() != null && condition.getMaxPrice() != null
        && condition.getMinPrice().compareTo(condition.getMaxPrice()) > 0;
    if (negative || inverted) {
      throw new BusinessException(ErrorCode.INVALID_SEARCH_CONDITION);
    }
  }

  // 허용되지 않은 정렬 필드는 제거한다(존재하지 않는 필드 정렬로 인한 ES 500 방지).
  // 정렬이 모두 걸러지면 기본(relevance 점수순)으로 둔다.
  private Pageable whitelistSort(Pageable pageable) {
    List<Sort.Order> allowed = pageable.getSort().stream()
        .filter(order -> SORTABLE_FIELDS.contains(order.getProperty()))
        .toList();
    Sort sort = allowed.isEmpty() ? Sort.unsorted() : Sort.by(allowed);
    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
  }
}