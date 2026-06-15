package com.sparta.copa.copaproduct.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.copa.copaproduct.cart.domain.CartItem;
import com.sparta.copa.copaproduct.cart.repository.CartItemRepository;
import com.sparta.copa.copaproduct.common.enums.ProductStatus;
import com.sparta.copa.copaproduct.common.exception.BusinessException;
import com.sparta.copa.copaproduct.common.exception.ErrorCode;
import com.sparta.copa.copaproduct.product.domain.Product;
import com.sparta.copa.copaproduct.product.service.ProductQueryService;
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
class CartServiceTest {

  @Mock
  private CartItemRepository cartItemRepository;
  @Mock
  private ProductQueryService productQueryService;

  @InjectMocks
  private CartService cartService;

  private Product purchasableProduct() {
    Product product = Product.create(1L, "PROD-2026-x", "상품", BigDecimal.valueOf(1000),
        10, "설명", List.of(), Map.of(), null, null);
    product.changeStatus(ProductStatus.SALE);
    return product;
  }

  // 옵션 상품: 색상:네이비/사이즈:M(10), L(5).
  private Product optionProduct() {
    Map<String, Object> options = Map.of(
        "색상", Map.of("네이비", Map.of("사이즈", Map.of("M", 10, "L", 5))));
    Product product = Product.create(1L, "PROD-2026-opt", "옵션상품", BigDecimal.valueOf(1000),
        null, "설명", List.of(), Map.of(), options, null);
    product.changeStatus(ProductStatus.SALE);
    return product;
  }

  @Test
  @DisplayName("판매 중인 상품을 처음 담으면 새 항목으로 저장한다")
  void addNewItem() {
    given(productQueryService.findById(100L)).willReturn(Optional.of(purchasableProduct()));
    given(cartItemRepository.findByUserIdAndProduct_IdAndOptionKey(1L, 100L, ""))
        .willReturn(Optional.empty());

    cartService.addItem(1L, 100L, null, 2);

    verify(cartItemRepository).save(any(CartItem.class));
  }

  @Test
  @DisplayName("이미 담긴 상품을 다시 담으면 수량을 누적한다")
  void accumulateQuantity() {
    CartItem existing = CartItem.create(1L, purchasableProduct(), "", 2);
    given(productQueryService.findById(100L)).willReturn(Optional.of(purchasableProduct()));
    given(cartItemRepository.findByUserIdAndProduct_IdAndOptionKey(1L, 100L, ""))
        .willReturn(Optional.of(existing));

    cartService.addItem(1L, 100L, null, 3);

    assertThat(existing.getQuantity()).isEqualTo(5);
    verify(cartItemRepository, never()).save(any());
  }

  @Test
  @DisplayName("옵션 상품은 유효한 옵션 경로로 담긴다")
  void addOptionItem() {
    given(productQueryService.findById(100L)).willReturn(Optional.of(optionProduct()));
    given(cartItemRepository.findByUserIdAndProduct_IdAndOptionKey(1L, 100L, "색상:네이비/사이즈:M"))
        .willReturn(Optional.empty());

    cartService.addItem(1L, 100L, "색상:네이비/사이즈:M", 1);

    verify(cartItemRepository).save(any(CartItem.class));
  }

  @Test
  @DisplayName("옵션 상품에 존재하지 않는 옵션은 담을 수 없다")
  void cannotAddUnknownOption() {
    given(productQueryService.findById(100L)).willReturn(Optional.of(optionProduct()));

    assertThatThrownBy(() -> cartService.addItem(1L, 100L, "색상:레드/사이즈:M", 1))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.OPTION_NOT_FOUND);
    verify(cartItemRepository, never()).save(any());
  }

  @Test
  @DisplayName("옵션 상품에 옵션을 지정하지 않으면 담을 수 없다")
  void cannotAddOptionProductWithoutOption() {
    given(productQueryService.findById(100L)).willReturn(Optional.of(optionProduct()));

    assertThatThrownBy(() -> cartService.addItem(1L, 100L, null, 1))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.OPTION_KEY_REQUIRED);
    verify(cartItemRepository, never()).save(any());
  }

  @Test
  @DisplayName("판매 중이 아닌 상품은 담을 수 없다")
  void cannotAddNotPurchasable() {
    Product hidden = Product.create(1L, "PROD-2026-y", "비노출", BigDecimal.valueOf(1000),
        10, "설명", List.of(), Map.of(), null, null); // 기본 HIDDEN
    given(productQueryService.findById(100L)).willReturn(Optional.of(hidden));

    assertThatThrownBy(() -> cartService.addItem(1L, 100L, null, 1))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_NOT_PURCHASABLE);
    verify(cartItemRepository, never()).save(any());
  }

  @Test
  @DisplayName("존재하지 않는 상품은 담을 수 없다")
  void cannotAddMissingProduct() {
    given(productQueryService.findById(100L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> cartService.addItem(1L, 100L, null, 1))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
  }

  @Test
  @DisplayName("옵션을 지정해 장바구니 항목을 삭제한다")
  void removeOptionItem() {
    CartItem item = CartItem.create(1L, optionProduct(), "색상:네이비/사이즈:M", 1);
    given(cartItemRepository.findByUserIdAndProduct_IdAndOptionKey(1L, 100L, "색상:네이비/사이즈:M"))
        .willReturn(Optional.of(item));

    cartService.removeItem(1L, 100L, "색상:네이비/사이즈:M");

    verify(cartItemRepository).delete(eq(item));
  }
}
