package com.sparta.copa.copaproduct.product.service;

import com.sparta.copa.copaproduct.category.service.CategoryService;
import com.sparta.copa.copaproduct.common.exception.BusinessException;
import com.sparta.copa.copaproduct.common.exception.ErrorCode;
import com.sparta.copa.copaproduct.product.domain.Product;
import com.sparta.copa.copaproduct.product.dto.request.ProductCreateRequest;
import com.sparta.copa.copaproduct.product.dto.request.ProductUpdateRequest;
import com.sparta.copa.copaproduct.product.dto.response.ProductResponse;
import com.sparta.copa.copaproduct.product.repository.ProductRepository;
import java.time.Year;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProductService {

  private final ProductRepository productRepository;
  private final CategoryService categoryService;
  private final ProductCacheService productCacheService;

  @Transactional
  public ProductResponse createProduct(Long sellerId, ProductCreateRequest request) {
    List<String> categoryPathIds = categoryService.expandWithAncestors(request.getCategoryIds());
    Product product = Product.create(
        sellerId,
        generateProductCode(),
        request.getName(),
        request.getPrice(),
        request.getCategoryIds(),
        categoryPathIds,
        request.getStockQuantity(),
        request.getDescription(),
        request.getSpecs());
    return ProductResponse.from(productRepository.save(product));
  }

  @Transactional(readOnly = true)
  public Page<ProductResponse> getProducts(String categoryId, Pageable pageable) {
    Page<Product> products = StringUtils.hasText(categoryId)
        ? productRepository.findByCategoryPathIdsContaining(categoryId, pageable)
        : productRepository.findAll(pageable);
    return products.map(ProductResponse::from);
  }

  // Look-Aside: 캐시 HIT이면 그대로, MISS면 DB 조회 후 캐시에 적재한다.
  @Transactional(readOnly = true)
  public ProductResponse getProduct(String productId) {
    return productCacheService.find(productId)
        .orElseGet(() -> {
          ProductResponse response = ProductResponse.from(getById(productId));
          productCacheService.put(response);
          return response;
        });
  }

  @Transactional
  public ProductResponse updateProduct(String productId, Long userId, boolean isAdmin,
      ProductUpdateRequest request) {
    Product product = getById(productId);
    checkModifiable(product, userId, isAdmin);
    List<String> categoryPathIds = categoryService.expandWithAncestors(request.getCategoryIds());
    product.update(
        request.getName(),
        request.getPrice(),
        request.getCategoryIds(),
        categoryPathIds,
        request.getStockQuantity(),
        request.getDescription(),
        request.getSpecs());
    if (request.getStatus() != null) {
      product.changeStatus(request.getStatus());
    }
    evictCacheAfterCommit(productId);
    return ProductResponse.from(product);
  }

  @Transactional
  public void deleteProduct(String productId, Long userId, boolean isAdmin) {
    Product product = getById(productId);
    checkModifiable(product, userId, isAdmin);
    productRepository.delete(product);
    evictCacheAfterCommit(productId);
  }

  // 캐시 무효화는 DB 커밋이 끝난 뒤 수행한다. 커밋 전에 지우면 그 틈의 조회가 옛 값을
  // 다시 적재해 stale이 남을 수 있기 때문. (트랜잭션이 없으면 즉시 삭제)
  private void evictCacheAfterCommit(String productId) {
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

  private Product getById(String productId) {
    return productRepository.findById(productId)
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
