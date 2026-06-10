package com.sparta.copa.copaproduct.product.repository;

import com.sparta.copa.copaproduct.product.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductRepository extends MongoRepository<Product, String> {

  boolean existsByProductCode(String productCode);

  Page<Product> findByCategoryPathIdsContaining(String categoryId, Pageable pageable);
}
