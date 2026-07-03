package com.sparta.copa.copaproduct.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.copa.copaproduct.category.service.CategoryService;
import com.sparta.copa.copaproduct.common.exception.BusinessException;
import com.sparta.copa.copaproduct.common.exception.ErrorCode;
import com.sparta.copa.copaproduct.outbox.OutboxRecorder;
import com.sparta.copa.copaproduct.product.domain.Product;
import com.sparta.copa.copaproduct.product.dto.request.ProductUpdateRequest;
import com.sparta.copa.copaproduct.product.dto.response.ProductResponse;
import com.sparta.copa.copaproduct.product.repository.ProductCategoryRepository;
import com.sparta.copa.copaproduct.product.repository.ProductQueryRepository;
import com.sparta.copa.copaproduct.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

  @Mock
  private ProductRepository productRepository;
  @Mock
  private ProductCategoryRepository productCategoryRepository;
  @Mock
  private ProductQueryRepository productQueryRepository;
  @Mock
  private CategoryService categoryService;
  @Mock
  private ProductCacheService productCacheService;
  @Mock
  private OutboxRecorder outboxRecorder;

  @InjectMocks
  private ProductService productService;

  private Product productOwnedBy(Long sellerId) {
    return Product.create(sellerId, "PROD-2026-x", "기존상품", BigDecimal.valueOf(1000),
        10, "설명", List.of("https://img/1.jpg"), Map.of(), null, null);
  }

  private ProductUpdateRequest updateRequest() {
    return ProductUpdateRequest.builder()
        .name("수정상품").price(BigDecimal.valueOf(2000)).categoryIds(List.of(1L)).stockQuantity(5)
        .build();
  }

  @Test
  @DisplayName("등록한 판매자 본인은 상품을 수정할 수 있다")
  void ownerCanUpdate() {
    given(productRepository.findById(10L)).willReturn(Optional.of(productOwnedBy(1L)));

    ProductResponse response = productService.updateProduct(10L, 1L, false, updateRequest());

    assertThat(response.getName()).isEqualTo("수정상품");
  }

  @Test
  @DisplayName("소유자가 아닌 판매자는 상품을 수정할 수 없다")
  void nonOwnerCannotUpdate() {
    given(productRepository.findById(10L)).willReturn(Optional.of(productOwnedBy(1L)));

    assertThatThrownBy(() -> productService.updateProduct(10L, 2L, false, updateRequest()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
    verify(categoryService, never()).getAllByIds(any());
  }

  @Test
  @DisplayName("ADMIN은 소유자가 아니어도 상품을 수정할 수 있다")
  void adminCanUpdateOthersProduct() {
    given(productRepository.findById(10L)).willReturn(Optional.of(productOwnedBy(1L)));

    ProductResponse response = productService.updateProduct(10L, 999L, true, updateRequest());

    assertThat(response.getName()).isEqualTo("수정상품");
  }

  @Test
  @DisplayName("캐시 HIT이면 DB를 조회하지 않고 캐시 값을 반환한다")
  void getProductCacheHit() {
    given(productCacheService.find(10L))
        .willReturn(Optional.of(ProductResponse.from(productOwnedBy(1L), List.of())));

    ProductResponse response = productService.getProduct(10L);

    assertThat(response.getName()).isEqualTo("기존상품");
    verify(productRepository, never()).findById(any());
  }

  @Test
  @DisplayName("캐시 MISS이면 DB 조회 후 캐시에 적재한다")
  void getProductCacheMiss() {
    given(productCacheService.find(10L)).willReturn(Optional.empty());
    given(productRepository.findById(10L)).willReturn(Optional.of(productOwnedBy(1L)));

    ProductResponse response = productService.getProduct(10L);

    assertThat(response.getName()).isEqualTo("기존상품");
    verify(productCacheService).put(any(ProductResponse.class));
  }
}
