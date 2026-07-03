package com.sparta.copa.copaproduct.product.repository;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sparta.copa.copaproduct.common.enums.ProductStatus;
import com.sparta.copa.copaproduct.product.domain.Product;
import com.sparta.copa.copaproduct.product.domain.QProduct;
import com.sparta.copa.copaproduct.product.domain.QProductCategory;
import com.sparta.copa.copaproduct.product.dto.request.ProductSearchCondition;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 상품 목록/검색의 QueryDSL 동적 쿼리. 각 조건을 {@link BooleanExpression}을 반환하는 술어 메서드로
 * 분리하고, 조건이 없으면 {@code null}을 반환한다. {@code where(Predicate...)}는 null 술어를
 * 무시하므로, 선택적 필터(keyword·가격범위·카테고리)가 값이 있는 것만 자연스럽게 조합된다.
 *
 * <p>카테고리는 조인 엔티티({@link QProductCategory})에 대한 exists 서브쿼리로 필터한다
 * (Product가 카테고리 컬렉션을 들지 않으므로, .clauderules 규약을 지키면서 필터).
 */
@Repository
@RequiredArgsConstructor
public class ProductQueryRepository {

  private static final QProduct PRODUCT = QProduct.product;
  private static final QProductCategory PRODUCT_CATEGORY = QProductCategory.productCategory;

  private final JPAQueryFactory queryFactory;

  /**
   * @param condition   선택적 검색 조건(keyword·가격범위)
   * @param categoryIds 카테고리 필터에 쓸 id 집합(검색 카테고리 + 하위 트리). null/빈 값이면 카테고리 미적용
   * @param statuses    노출 대상 상태(공개 카탈로그는 SALE·SOLD_OUT)
   */
  public Page<Product> search(ProductSearchCondition condition, List<Long> categoryIds,
      Collection<ProductStatus> statuses, Pageable pageable) {
    List<Product> content = queryFactory
        .selectFrom(PRODUCT)
        .where(
            PRODUCT.deleted.isFalse(),
            statusIn(statuses),
            keywordContains(condition.getKeyword()),
            priceGoe(condition.getMinPrice()),
            priceLoe(condition.getMaxPrice()),
            categoryIn(categoryIds))
        .orderBy(toOrderSpecifiers(pageable.getSort()))
        .offset(pageable.getOffset())
        .limit(pageable.getPageSize())
        .fetch();

    JPAQuery<Long> countQuery = queryFactory
        .select(PRODUCT.count())
        .from(PRODUCT)
        .where(
            PRODUCT.deleted.isFalse(),
            statusIn(statuses),
            keywordContains(condition.getKeyword()),
            priceGoe(condition.getMinPrice()),
            priceLoe(condition.getMaxPrice()),
            categoryIn(categoryIds));

    return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
  }

  private BooleanExpression statusIn(Collection<ProductStatus> statuses) {
    return (statuses == null || statuses.isEmpty()) ? null : PRODUCT.status.in(statuses);
  }

  // 상품명 OR 설명 부분 일치(대소문자 무시). 공백/빈 값이면 조건 미적용.
  private BooleanExpression keywordContains(String keyword) {
    if (!StringUtils.hasText(keyword)) {
      return null;
    }
    String trimmed = keyword.trim();
    return PRODUCT.name.containsIgnoreCase(trimmed)
        .or(PRODUCT.description.containsIgnoreCase(trimmed));
  }

  private BooleanExpression priceGoe(BigDecimal minPrice) {
    return minPrice == null ? null : PRODUCT.price.goe(minPrice);
  }

  private BooleanExpression priceLoe(BigDecimal maxPrice) {
    return maxPrice == null ? null : PRODUCT.price.loe(maxPrice);
  }

  // 주어진 카테고리 id 집합(하위 트리 포함) 중 하나라도 걸린 상품(조인 엔티티 exists 서브쿼리).
  private BooleanExpression categoryIn(List<Long> categoryIds) {
    if (categoryIds == null || categoryIds.isEmpty()) {
      return null;
    }
    return JPAExpressions.selectOne()
        .from(PRODUCT_CATEGORY)
        .where(PRODUCT_CATEGORY.product.eq(PRODUCT),
            PRODUCT_CATEGORY.category.id.in(categoryIds))
        .exists();
  }

  // 정렬은 화이트리스트(price·name·createdAt)만 허용. 지정이 없으면 최신순.
  private OrderSpecifier<?>[] toOrderSpecifiers(Sort sort) {
    List<OrderSpecifier<?>> orders = new ArrayList<>();
    for (Sort.Order order : sort) {
      Order direction = order.isAscending() ? Order.ASC : Order.DESC;
      switch (order.getProperty()) {
        case "price" -> orders.add(new OrderSpecifier<>(direction, PRODUCT.price));
        case "name" -> orders.add(new OrderSpecifier<>(direction, PRODUCT.name));
        case "createdAt" -> orders.add(new OrderSpecifier<>(direction, PRODUCT.createdAt));
        default -> {
          // 허용되지 않은 정렬 필드는 무시한다(인젝션·예기치 않은 정렬 방지).
        }
      }
    }
    if (orders.isEmpty()) {
      orders.add(new OrderSpecifier<>(Order.DESC, PRODUCT.createdAt));
    }
    return orders.toArray(new OrderSpecifier[0]);
  }
}