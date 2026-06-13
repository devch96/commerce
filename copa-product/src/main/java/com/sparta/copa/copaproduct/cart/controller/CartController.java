package com.sparta.copa.copaproduct.cart.controller;

import com.sparta.copa.copaproduct.cart.dto.request.AddCartItemRequest;
import com.sparta.copa.copaproduct.cart.dto.request.UpdateCartItemRequest;
import com.sparta.copa.copaproduct.cart.dto.response.CartResponse;
import com.sparta.copa.copaproduct.cart.service.CartService;
import com.sparta.copa.copaproduct.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 회원 전용 장바구니. 게이트웨이 인증으로 주입된 X-User-Id 기준(비로그인 비공개).
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

  private static final String USER_ID_HEADER = "X-User-Id";

  private final CartService cartService;

  @GetMapping
  public ApiResponse<CartResponse> getCart(@RequestHeader(USER_ID_HEADER) Long userId) {
    return ApiResponse.success(cartService.getCart(userId));
  }

  @PostMapping("/items")
  public ApiResponse<CartResponse> addItem(@RequestHeader(USER_ID_HEADER) Long userId,
      @Valid @RequestBody AddCartItemRequest request) {
    cartService.addItem(userId, request.getProductId(), request.getQuantity());
    return ApiResponse.success(cartService.getCart(userId));
  }

  @PatchMapping("/items/{productId}")
  public ApiResponse<CartResponse> changeQuantity(@RequestHeader(USER_ID_HEADER) Long userId,
      @PathVariable Long productId, @Valid @RequestBody UpdateCartItemRequest request) {
    cartService.changeQuantity(userId, productId, request.getQuantity());
    return ApiResponse.success(cartService.getCart(userId));
  }

  @DeleteMapping("/items/{productId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeItem(@RequestHeader(USER_ID_HEADER) Long userId,
      @PathVariable Long productId) {
    cartService.removeItem(userId, productId);
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void clear(@RequestHeader(USER_ID_HEADER) Long userId) {
    cartService.clear(userId);
  }
}
