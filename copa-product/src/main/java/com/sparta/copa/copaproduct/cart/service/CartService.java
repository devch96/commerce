package com.sparta.copa.copaproduct.cart.service;

import com.sparta.copa.copaproduct.cart.domain.CartItem;
import com.sparta.copa.copaproduct.cart.dto.response.CartItemResponse;
import com.sparta.copa.copaproduct.cart.dto.response.CartResponse;
import com.sparta.copa.copaproduct.cart.repository.CartItemRepository;
import com.sparta.copa.copaproduct.common.exception.BusinessException;
import com.sparta.copa.copaproduct.common.exception.ErrorCode;
import com.sparta.copa.copaproduct.product.domain.Product;
import com.sparta.copa.copaproduct.product.service.ProductQueryService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartService {

  private final CartItemRepository cartItemRepository;
  private final ProductQueryService productQueryService;

  // 장바구니엔 상품 참조 + 수량만 두고, 조회 시 현재 상품 정보로 enrich한다(가격은 항상 현재 기준).
  @Transactional(readOnly = true)
  public CartResponse getCart(Long userId) {
    List<CartItemResponse> responses = new ArrayList<>();
    for (CartItem item : cartItemRepository.findWithProductByUserId(userId)) {
      responses.add(CartItemResponse.from(item));
    }
    return CartResponse.of(responses);
  }

  // 담기: 상품 존재 + 판매중 검증 후, 이미 담겨 있으면 수량 누적, 없으면 새로 담는다.
  @Transactional
  public void addItem(Long userId, Long productId, int quantity) {
    Product product = productQueryService.findById(productId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    if (!product.isPurchasable()) {
      throw new BusinessException(ErrorCode.PRODUCT_NOT_PURCHASABLE);
    }

    CartItem existing = cartItemRepository.findByUserIdAndProduct_Id(userId, productId).orElse(null);
    if (existing != null) {
      existing.addQuantity(quantity);
    } else {
      cartItemRepository.save(CartItem.create(userId, product, quantity));
    }
  }

  @Transactional
  public void changeQuantity(Long userId, Long productId, int quantity) {
    CartItem item = cartItemRepository.findByUserIdAndProduct_Id(userId, productId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));
    item.changeQuantity(quantity);
  }

  @Transactional
  public void removeItem(Long userId, Long productId) {
    CartItem item = cartItemRepository.findByUserIdAndProduct_Id(userId, productId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));
    cartItemRepository.delete(item);
  }

  @Transactional
  public void clear(Long userId) {
    cartItemRepository.deleteByUserId(userId);
  }
}
