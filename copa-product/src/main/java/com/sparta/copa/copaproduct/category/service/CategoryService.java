package com.sparta.copa.copaproduct.category.service;

import com.sparta.copa.copaproduct.category.domain.Category;
import com.sparta.copa.copaproduct.category.dto.response.CategoryResponse;
import com.sparta.copa.copaproduct.category.repository.CategoryRepository;
import com.sparta.copa.copaproduct.common.exception.BusinessException;
import com.sparta.copa.copaproduct.common.exception.ErrorCode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

  private final CategoryRepository categoryRepository;

  @Transactional
  public CategoryResponse create(String name, String parentId) {
    if (parentId != null) {
      requireExists(parentId);
    }
    if (categoryRepository.existsByParentIdAndName(parentId, name)) {
      throw new BusinessException(ErrorCode.DUPLICATE_CATEGORY);
    }
    Category saved = categoryRepository.save(Category.create(name, parentId));
    return CategoryResponse.of(saved, List.of());
  }

  @Transactional(readOnly = true)
  public List<CategoryResponse> getTree() {
    Map<String, List<Category>> byParent = new HashMap<>();
    for (Category category : categoryRepository.findAll()) {
      byParent.computeIfAbsent(category.getParentId(), key -> new ArrayList<>()).add(category);
    }
    return buildChildren(null, byParent);
  }

  @Transactional
  public CategoryResponse update(String categoryId, String name, String newParentId) {
    Category category = getById(categoryId);
    if (!Objects.equals(category.getParentId(), newParentId)) {
      if (newParentId != null) {
        requireExists(newParentId);
        if (wouldCreateCycle(categoryId, newParentId)) {
          throw new BusinessException(ErrorCode.CATEGORY_CYCLE);
        }
      }
      category.moveTo(newParentId);
    }
    category.rename(name);
    return CategoryResponse.of(category, List.of());
  }

  @Transactional
  public void delete(String categoryId) {
    Category category = getById(categoryId);
    if (categoryRepository.existsByParentId(categoryId)) {
      throw new BusinessException(ErrorCode.CATEGORY_HAS_CHILDREN);
    }
    categoryRepository.delete(category);
  }

  // 선택한 카테고리들을 그 조상 전체까지 펼친 클로저(중복 제거)로 반환한다.
  // 무한 트리에서 상위 카테고리로 필터해도 하위 상품이 잡히도록 상품에 비정규화 저장하기 위함.
  // 존재하지 않는 카테고리 id가 섞여 있으면 INVALID_CATEGORY로 막는다(검증 겸용).
  @Transactional(readOnly = true)
  public List<String> expandWithAncestors(Collection<String> categoryIds) {
    Map<String, String> parentOf = new HashMap<>();
    for (Category category : categoryRepository.findAll()) {
      parentOf.put(category.getId(), category.getParentId());
    }

    Set<String> closure = new LinkedHashSet<>();
    for (String categoryId : categoryIds) {
      if (!parentOf.containsKey(categoryId)) {
        throw new BusinessException(ErrorCode.INVALID_CATEGORY);
      }
      String cursor = categoryId;
      // 이미 클로저에 있으면 그 조상도 이미 포함된 것이므로 중단(add가 false 반환).
      while (cursor != null && closure.add(cursor)) {
        cursor = parentOf.get(cursor);
      }
    }
    return new ArrayList<>(closure);
  }

  private List<CategoryResponse> buildChildren(String parentId,
      Map<String, List<Category>> byParent) {
    List<CategoryResponse> result = new ArrayList<>();
    for (Category category : byParent.getOrDefault(parentId, List.of())) {
      result.add(CategoryResponse.of(category, buildChildren(category.getId(), byParent)));
    }
    return result;
  }

  // newParentId의 조상 체인에 categoryId가 있으면 자기 자신/하위로의 이동 → 사이클.
  private boolean wouldCreateCycle(String categoryId, String newParentId) {
    String cursor = newParentId;
    while (cursor != null) {
      if (cursor.equals(categoryId)) {
        return true;
      }
      cursor = categoryRepository.findById(cursor).map(Category::getParentId).orElse(null);
    }
    return false;
  }

  private void requireExists(String categoryId) {
    if (!categoryRepository.existsById(categoryId)) {
      throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
    }
  }

  private Category getById(String categoryId) {
    return categoryRepository.findById(categoryId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
  }
}
