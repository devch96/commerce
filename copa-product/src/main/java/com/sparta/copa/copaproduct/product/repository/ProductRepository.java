package com.sparta.copa.copaproduct.product.repository;

import com.sparta.copa.copaproduct.common.enums.ProductStatus;
import com.sparta.copa.copaproduct.product.domain.Product;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

  boolean existsByProductCode(String productCode);

  // 공개 카탈로그 목록: soft delete + 비공개 상태(HIDDEN·DISCONTINUED)를 제외하고 노출 상태만 보인다.
  // (카테고리 필터는 ProductCategoryRepository가 담당)
  Page<Product> findByDeletedFalseAndStatusIn(Collection<ProductStatus> statuses, Pageable pageable);
}
