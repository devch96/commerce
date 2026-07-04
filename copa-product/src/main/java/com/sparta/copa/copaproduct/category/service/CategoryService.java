package com.sparta.copa.copaproduct.category.service;

import com.sparta.copa.copaproduct.category.domain.Category;
import com.sparta.copa.copaproduct.category.dto.CategorySnapshot;
import com.sparta.copa.copaproduct.category.dto.response.CategoryResponse;
import com.sparta.copa.copaproduct.category.repository.CategoryRepository;
import com.sparta.copa.copaproduct.common.exception.BusinessException;
import com.sparta.copa.copaproduct.common.exception.ErrorCode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class CategoryService {

  private final CategoryRepository categoryRepository;
  private final CategoryCacheService categoryCacheService;

  @Transactional
  public CategoryResponse create(String name, Long parentId) {
    if (parentId != null) {
      requireExists(parentId);
    }
    if (categoryRepository.existsByParentIdAndName(parentId, name)) {
      throw new BusinessException(ErrorCode.DUPLICATE_CATEGORY);
    }
    Category saved = categoryRepository.save(Category.create(name, parentId));
    evictCacheAfterCommit();
    return CategoryResponse.of(saved, List.of());
  }

  @Transactional(readOnly = true)
  public List<CategoryResponse> getTree() {
    Map<Long, List<CategorySnapshot>> byParent = new HashMap<>();
    for (CategorySnapshot category : getCachedCategories()) {
      byParent.computeIfAbsent(category.getParentId(), key -> new ArrayList<>()).add(category);
    }
    return buildChildren(null, byParent);
  }

  @Transactional
  public CategoryResponse update(Long categoryId, String name, Long newParentId) {
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
    evictCacheAfterCommit();
    return CategoryResponse.of(category, List.of());
  }

  @Transactional
  public void delete(Long categoryId) {
    Category category = getById(categoryId);
    if (categoryRepository.existsByParentId(categoryId)) {
      throw new BusinessException(ErrorCode.CATEGORY_HAS_CHILDREN);
    }
    categoryRepository.delete(category);
    evictCacheAfterCommit();
  }

  // 상품에 붙일 카테고리 엔티티들을 조회한다. 존재하지 않는 id가 섞여 있으면 INVALID_CATEGORY(검증 겸용).
  @Transactional(readOnly = true)
  public List<Category> getAllByIds(Collection<Long> categoryIds) {
    List<Category> categories = categoryRepository.findAllById(categoryIds);
    if (categories.size() != new HashSet<>(categoryIds).size()) {
      throw new BusinessException(ErrorCode.INVALID_CATEGORY);
    }
    return categories;
  }

  // 검색용: 주어진 카테고리와 그 하위(자식·손자…) 전체 id를 모은다.
  // 조상 클로저를 상품에 저장하지 않고, 검색 시점에 하위 트리를 펼쳐 productRepository.findByCategoryIds로 넘긴다.
  @Transactional(readOnly = true)
  public List<Long> collectSubtreeIds(Long categoryId) {
    requireExists(categoryId);
    Map<Long, List<Long>> childrenOf = new HashMap<>();
    for (CategorySnapshot category : getCachedCategories()) {
      childrenOf.computeIfAbsent(category.getParentId(), key -> new ArrayList<>())
          .add(category.getId());
    }

    List<Long> result = new ArrayList<>();
    Deque<Long> stack = new ArrayDeque<>();
    stack.push(categoryId);
    while (!stack.isEmpty()) {
      Long current = stack.pop();
      result.add(current);
      for (Long child : childrenOf.getOrDefault(current, List.of())) {
        stack.push(child);
      }
    }
    return result;
  }

  private List<CategoryResponse> buildChildren(Long parentId,
      Map<Long, List<CategorySnapshot>> byParent) {
    List<CategoryResponse> result = new ArrayList<>();
    for (CategorySnapshot category : byParent.getOrDefault(parentId, List.of())) {
      result.add(CategoryResponse.of(category, buildChildren(category.getId(), byParent)));
    }
    return result;
  }

  // 카테고리 전체 스냅샷을 캐시에서 읽고, 미스면 DB에서 로드해 채운다(Look-Aside).
  private List<CategorySnapshot> getCachedCategories() {
    return categoryCacheService.find().orElseGet(() -> {
      List<CategorySnapshot> snapshots = categoryRepository.findAll().stream()
          .map(CategorySnapshot::from)
          .toList();
      categoryCacheService.put(snapshots);
      return snapshots;
    });
  }

  // 카테고리 변경은 DB 커밋이 끝난 뒤 캐시를 무효화한다(롤백 시 캐시가 잘못 비워지지 않게).
  private void evictCacheAfterCommit() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          categoryCacheService.evict();
        }
      });
    } else {
      categoryCacheService.evict();
    }
  }

  // newParentId의 조상 체인에 categoryId가 있으면 자기 자신/하위로의 이동 → 사이클.
  private boolean wouldCreateCycle(Long categoryId, Long newParentId) {
    Long cursor = newParentId;
    while (cursor != null) {
      if (cursor.equals(categoryId)) {
        return true;
      }
      cursor = categoryRepository.findById(cursor).map(Category::getParentId).orElse(null);
    }
    return false;
  }

  private void requireExists(Long categoryId) {
    if (!categoryRepository.existsById(categoryId)) {
      throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
    }
  }

  private Category getById(Long categoryId) {
    return categoryRepository.findById(categoryId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
  }
}
