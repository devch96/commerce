package com.sparta.copa.copaproduct.search.document;

import com.sparta.copa.copaproduct.product.event.ProductSearchEvent;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * Elasticsearch 상품 색인 문서(index=products). 색인기가 {@link ProductSearchEvent}로부터 upsert한다.
 *
 * <ul>
 *   <li>{@code name}·{@code description}은 {@code Text}(분석기 적용) → 전문 검색·relevance.</li>
 *   <li>{@code status}·{@code productCode}는 {@code Keyword} → 정확 일치 필터.</li>
 *   <li>{@code price}는 {@code Double} → range 쿼리·집계(avg/min/max).</li>
 *   <li>{@code categoryIds}는 {@code Long} 배열 → term 필터·집계.</li>
 * </ul>
 */
@Document(indexName = "products")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductDocument {

  // 상품 PK(Long)를 문자열 id로 쓴다(멱등 upsert·delete 키).
  @Id
  private String id;

  @Field(type = FieldType.Keyword)
  private String productCode;

  @Field(type = FieldType.Long)
  private Long sellerId;

  @Field(type = FieldType.Text)
  private String name;

  @Field(type = FieldType.Text)
  private String description;

  @Field(type = FieldType.Double)
  private Double price;

  @Field(type = FieldType.Keyword)
  private String status;

  @Field(type = FieldType.Integer)
  private Integer stockQuantity;

  @Field(type = FieldType.Long)
  private List<Long> categoryIds;

  @Field(type = FieldType.Date, format = DateFormat.date_optional_time)
  private LocalDateTime createdAt;

  public static ProductDocument from(ProductSearchEvent event) {
    return ProductDocument.builder()
        .id(String.valueOf(event.getProductId()))
        .productCode(event.getProductCode())
        .sellerId(event.getSellerId())
        .name(event.getName())
        .description(event.getDescription())
        .price(event.getPrice() == null ? null : event.getPrice().doubleValue())
        .status(event.getStatus())
        .stockQuantity(event.getStockQuantity())
        .categoryIds(event.getCategoryIds())
        .createdAt(event.getCreatedAt())
        .build();
  }
}