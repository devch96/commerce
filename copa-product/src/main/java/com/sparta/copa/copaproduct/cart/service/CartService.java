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

  // 담기: 상품 존재 + 판매중 + 옵션 유효성 검증 후, 같은 상품·옵션이 있으면 수량 누적, 없으면 새로 담는다.
  @Transactional
  public void addItem(Long userId, Long productId, String optionKey, int quantity) {
    Product product = productQueryService.findById(productId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    if (!product.isPurchasable()) {
      throw new BusinessException(ErrorCode.PRODUCT_NOT_PURCHASABLE);
    }
    // 옵션 검증 + 정규화(옵션 없는 상품은 "", 옵션 상품은 유효한 leaf 경로만 통과).
    String resolvedKey = product.resolveOption(normalize(optionKey)).getOptionKey();

    CartItem existing = cartItemRepository
        .findByUserIdAndProduct_IdAndOptionKey(userId, productId, resolvedKey).orElse(null);
    if (existing != null) {
      existing.addQuantity(quantity);
    } else {
      cartItemRepository.save(CartItem.create(userId, product, resolvedKey, quantity));
    }
  }

  @Transactional
  public void changeQuantity(Long userId, Long productId, String optionKey, int quantity) {
    CartItem item = findItem(userId, productId, optionKey);
    item.changeQuantity(quantity);
  }

  @Transactional
  public void removeItem(Long userId, Long productId, String optionKey) {
    cartItemRepository.delete(findItem(userId, productId, optionKey));
  }

  private CartItem findItem(Long userId, Long productId, String optionKey) {
    return cartItemRepository
        .findByUserIdAndProduct_IdAndOptionKey(userId, productId, normalize(optionKey))
        .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));
  }

  // 옵션 없는 상품/누락 시 빈 문자열로 정규화한다(유니크 키 일관성).
  private String normalize(String optionKey) {
    return (optionKey == null || optionKey.isBlank()) ? "" : optionKey;
  }

  @Transactional
  public void clear(Long userId) {
    cartItemRepository.deleteByUserId(userId);
  }
}
