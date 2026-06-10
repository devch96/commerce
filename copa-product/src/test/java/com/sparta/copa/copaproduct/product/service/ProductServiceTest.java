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
import com.sparta.copa.copaproduct.product.domain.Product;
import com.sparta.copa.copaproduct.product.dto.request.ProductUpdateRequest;
import com.sparta.copa.copaproduct.product.dto.response.ProductResponse;
import com.sparta.copa.copaproduct.product.repository.ProductRepository;
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
  private CategoryService categoryService;

  @InjectMocks
  private ProductService productService;

  private Product productOwnedBy(Long sellerId) {
    return Product.create(sellerId, "PROD-2026-x", "기존상품", 1000L, List.of("c1"), List.of("c1"),
        10, "설명", Map.of());
  }

  private ProductUpdateRequest updateRequest() {
    return ProductUpdateRequest.builder()
        .name("수정상품").price(2000L).categoryIds(List.of("c1")).stockQuantity(5).build();
  }

  @Test
  @DisplayName("등록한 판매자 본인은 상품을 수정할 수 있다")
  void ownerCanUpdate() {
    given(productRepository.findById("p1")).willReturn(Optional.of(productOwnedBy(1L)));

    ProductResponse response = productService.updateProduct("p1", 1L, false, updateRequest());

    assertThat(response.getName()).isEqualTo("수정상품");
  }

  @Test
  @DisplayName("소유자가 아닌 판매자는 상품을 수정할 수 없다")
  void nonOwnerCannotUpdate() {
    given(productRepository.findById("p1")).willReturn(Optional.of(productOwnedBy(1L)));

    assertThatThrownBy(() -> productService.updateProduct("p1", 2L, false, updateRequest()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
    verify(categoryService, never()).expandWithAncestors(any());
  }

  @Test
  @DisplayName("ADMIN은 소유자가 아니어도 상품을 수정할 수 있다")
  void adminCanUpdateOthersProduct() {
    given(productRepository.findById("p1")).willReturn(Optional.of(productOwnedBy(1L)));

    ProductResponse response = productService.updateProduct("p1", 999L, true, updateRequest());

    assertThat(response.getName()).isEqualTo("수정상품");
  }
}
