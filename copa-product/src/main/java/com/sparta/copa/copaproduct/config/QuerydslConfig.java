package com.sparta.copa.copaproduct.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * QueryDSL 동적 쿼리를 위한 {@link JPAQueryFactory} 빈. 상품 목록/검색의
 * 선택적 필터(keyword·가격범위·카테고리·정렬)를 컴파일 타임 타입 안전하게 조합한다.
 */
@Configuration
public class QuerydslConfig {

  @PersistenceContext
  private EntityManager entityManager;

  @Bean
  public JPAQueryFactory jpaQueryFactory() {
    return new JPAQueryFactory(entityManager);
  }
}