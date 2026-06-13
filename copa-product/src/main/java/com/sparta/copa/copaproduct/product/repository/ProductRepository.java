package com.sparta.copa.copaproduct.product.repository;

import com.sparta.copa.copaproduct.product.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

  boolean existsByProductCode(String productCode);

  // 카탈로그 목록은 soft delete된 상품을 제외한다. (카테고리 필터는 ProductCategoryRepository가 담당)
  Page<Product> findByDeletedFalse(Pageable pageable);
}
