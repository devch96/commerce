package com.sparta.copa.copaproduct.search.service;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.sparta.copa.copaproduct.search.document.ProductDocument;
import com.sparta.copa.copaproduct.search.dto.request.ProductEsSearchCondition;
import com.sparta.copa.copaproduct.search.dto.response.ProductAggregationResponse;
import com.sparta.copa.copaproduct.search.dto.response.ProductSearchResult;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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

  private final ElasticsearchOperations elasticsearchOperations;

  public Page<ProductSearchResult> search(ProductEsSearchCondition condition, Pageable pageable) {
    NativeQuery query = NativeQuery.builder()
        .withQuery(buildQuery(condition))
        .withPageable(pageable)
        .build();
    SearchHits<ProductDocument> hits = elasticsearchOperations.search(query, ProductDocument.class);
    List<ProductSearchResult> results = hits.getSearchHits().stream()
        .map(hit -> ProductSearchResult.from(hit.getContent(), hit.getScore()))
        .toList();
    return new PageImpl<>(results, pageable, hits.getTotalHits());
  }

  public ProductAggregationResponse aggregate(ProductEsSearchCondition condition) {
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
}