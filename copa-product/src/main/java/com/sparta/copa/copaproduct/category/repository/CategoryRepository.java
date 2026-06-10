package com.sparta.copa.copaproduct.category.repository;

import com.sparta.copa.copaproduct.category.domain.Category;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CategoryRepository extends MongoRepository<Category, String> {

  boolean existsByParentId(String parentId);

  boolean existsByParentIdAndName(String parentId, String name);
}
