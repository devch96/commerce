package com.sparta.copa.copaproduct.product.service;

import com.sparta.copa.copaproduct.category.domain.Category;
import com.sparta.copa.copaproduct.category.service.CategoryService;
import com.sparta.copa.copaproduct.common.exception.BusinessException;
import com.sparta.copa.copaproduct.common.exception.ErrorCode;
import com.sparta.copa.copaproduct.product.domain.Product;
import com.sparta.copa.copaproduct.product.domain.ProductCategory;
import com.sparta.copa.copaproduct.product.dto.request.ProductCreateRequest;
import com.sparta.copa.copaproduct.product.dto.request.ProductUpdateRequest;
import com.sparta.copa.copaproduct.product.dto.response.ProductResponse;
import com.sparta.copa.copaproduct.product.repository.ProductCategoryRepository;
import com.sparta.copa.copaproduct.product.repository.ProductRepository;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class ProductService {

  private final ProductRepository productRepository;
  private final ProductCategoryRepository productCategoryRepository;
  private final CategoryService categoryService;
  private final ProductCacheService productCacheService;

  @Transactional
  public ProductResponse createProduct(Long sellerId, ProductCreateRequest request) {
    List<Category> categories = categoryService.getAllByIds(request.getCategoryIds());
    Product product = productRepository.save(Product.create(
        sellerId,
        generateProductCode(),
        request.getName(),
        request.getPrice(),
        request.getStockQuantity(),
        request.getDescription(),
        request.getImages(),
        request.getSpecs()));
    linkCategories(product, categories);
    return ProductResponse.from(product, request.getCategoryIds());
  }

  @Transactional(readOnly = true)
  public Page<ProductResponse> getProducts(Long categoryId, Pageable pageable) {
    Page<Product> products = categoryId != null
        ? productCategoryRepository.findProductsByCategoryIds(
            categoryService.collectSubtreeIds(categoryId), pageable)
        : productRepository.findByDeletedFalse(pageable);

    Map<Long, List<Long>> categoryIdsByProduct = categoryIdsByProduct(
        products.getContent().stream().map(Product::getId).toList());
    return products.map(
        product -> ProductResponse.from(product, categoryIdsByProduct.getOrDefault(product.getId(), List.of())));
  }

  // Look-Aside: 캐시 HIT이면 그대로, MISS면 DB 조회 후 캐시에 적재한다.
  @Transactional(readOnly = true)
  public ProductResponse getProduct(Long productId) {
    return productCacheService.find(productId)
        .orElseGet(() -> {
          Product product = getById(productId);
          ProductResponse response = ProductResponse.from(product, categoryIdsOf(productId));
          productCacheService.put(response);
          return response;
        });
  }

  @Transactional
  public ProductResponse updateProduct(Long productId, Long userId, boolean isAdmin,
      ProductUpdateRequest request) {
    Product product = getById(productId);
    checkModifiable(product, userId, isAdmin);
    List<Category> categories = categoryService.getAllByIds(request.getCategoryIds());
    product.update(
        request.getName(),
        request.getPrice(),
        request.getStockQuantity(),
        request.getDescription(),
        request.getImages(),
        request.getSpecs());
    if (request.getStatus() != null) {
      product.changeStatus(request.getStatus());
    }
    // 카테고리 링크는 통째로 교체한다.
    productCategoryRepository.deleteByProductId(productId);
    linkCategories(product, categories);
    evictCacheAfterCommit(productId);
    return ProductResponse.from(product, request.getCategoryIds());
  }

  @Transactional
  public void deleteProduct(Long productId, Long userId, boolean isAdmin) {
    Product product = getById(productId);
    checkModifiable(product, userId, isAdmin);
    product.softDelete();
    evictCacheAfterCommit(productId);
  }

  private void linkCategories(Product product, List<Category> categories) {
    for (Category category : categories) {
      productCategoryRepository.save(ProductCategory.of(product, category));
    }
  }

  private List<Long> categoryIdsOf(Long productId) {
    List<Long> ids = new ArrayList<>();
    for (ProductCategory link : productCategoryRepository.findByProduct_Id(productId)) {
      ids.add(link.getCategory().getId());
    }
    return ids;
  }

  private Map<Long, List<Long>> categoryIdsByProduct(List<Long> productIds) {
    Map<Long, List<Long>> result = new HashMap<>();
    if (productIds.isEmpty()) {
      return result;
    }
    for (ProductCategory link : productCategoryRepository.findByProduct_IdIn(productIds)) {
      result.computeIfAbsent(link.getProduct().getId(), key -> new ArrayList<>())
          .add(link.getCategory().getId());
    }
    return result;
  }

  // 캐시 무효화는 DB 커밋이 끝난 뒤 수행한다.
  private void evictCacheAfterCommit(Long productId) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          productCacheService.evict(productId);
        }
      });
    } else {
      productCacheService.evict(productId);
    }
  }

  // 등록한 판매자 본인 또는 ADMIN만 수정/삭제할 수 있다.
  private void checkModifiable(Product product, Long userId, boolean isAdmin) {
    if (!isAdmin && !product.isOwnedBy(userId)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
  }

  // soft delete된 상품은 없는 것으로 취급(조회·수정·삭제 모두 404).
  private Product getById(Long productId) {
    return productRepository.findById(productId)
        .filter(product -> !product.isDeleted())
        .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
  }

  // 'PROD-<연도>-<UUID>' 규격. UUID 충돌은 사실상 없으나 방어적으로 중복 시 재생성한다.
  private String generateProductCode() {
    String code;
    do {
      code = "PROD-" + Year.now().getValue() + "-" + UUID.randomUUID();
    } while (productRepository.existsByProductCode(code));
    return code;
  }
}
