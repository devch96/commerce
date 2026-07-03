package com.sparta.copa.copaproduct.product.dto.request;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * QueryDSL 동적 검색 조건. 모든 필드는 선택적이며, 값이 있는 것만 술어(predicate)로 조합된다.
 * 카테고리는 하위 트리까지 펼쳐 매칭한다(상위 카테고리로 검색해도 하위 상품이 잡힌다).
 *
 * <p>불변(@Getter + public 생성자, setter 없음) DTO로, 컨트롤러가 별도 애노테이션 없이
 * 생성자 바인딩으로 쿼리 파라미터를 그대로 받는다(파라미터명 = 필드명).
 */
@Getter
@Builder
@AllArgsConstructor
public class ProductSearchCondition {

  // 상품명·설명에 대한 부분 일치(대소문자 무시). null/빈 값이면 조건 미적용.
  private final String keyword;
  // 이 카테고리와 모든 하위 카테고리에 속한 상품으로 한정.
  private final Long categoryId;
  // 가격 하한/상한(포함). BigDecimal로 통화 규약(.clauderules)을 지킨다.
  private final BigDecimal minPrice;
  private final BigDecimal maxPrice;
}