package com.sparta.copa.copaproduct.category.repository;

import com.sparta.copa.copaproduct.category.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

  boolean existsByParentId(Long parentId);

  boolean existsByParentIdAndName(Long parentId, String name);
}
