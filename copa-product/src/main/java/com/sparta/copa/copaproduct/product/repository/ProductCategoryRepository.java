package com.sparta.copa.copaproduct.product.repository;

import com.sparta.copa.copaproduct.product.domain.Product;
import com.sparta.copa.copaproduct.product.domain.ProductCategory;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

  List<ProductCategory> findByProduct_Id(Long productId);

  List<ProductCategory> findByProduct_IdIn(Collection<Long> productIds);

  // 상품 수정 시 카테고리 링크를 통째로 교체하기 위한 즉시 벌크 삭제.
  @Modifying
  @Query("delete from ProductCategory pc where pc.product.id = :productId")
  void deleteByProductId(@Param("productId") Long productId);

  // 검색: 주어진 카테고리 id 집합(검색 카테고리 + 하위 트리) 중 하나라도 속한 상품(중복 제거).
  @Query(value = "select distinct pc.product from ProductCategory pc"
      + " where pc.category.id in :categoryIds and pc.product.deleted = false",
      countQuery = "select count(distinct pc.product) from ProductCategory pc"
          + " where pc.category.id in :categoryIds and pc.product.deleted = false")
  Page<Product> findProductsByCategoryIds(@Param("categoryIds") Collection<Long> categoryIds,
      Pageable pageable);
}
