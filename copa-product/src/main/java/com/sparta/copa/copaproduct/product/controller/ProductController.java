package com.sparta.copa.copaproduct.product.controller;

import com.sparta.copa.copaproduct.common.exception.BusinessException;
import com.sparta.copa.copaproduct.common.exception.ErrorCode;
import com.sparta.copa.copaproduct.common.response.ApiResponse;
import com.sparta.copa.copaproduct.product.dto.request.ProductCreateRequest;
import com.sparta.copa.copaproduct.product.dto.request.ProductUpdateRequest;
import com.sparta.copa.copaproduct.product.dto.response.ProductResponse;
import com.sparta.copa.copaproduct.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

  private static final String USER_ID_HEADER = "X-User-Id";
  private static final String USER_ROLE_HEADER = "X-User-Role";
  private static final String ADMIN_ROLE = "ADMIN";
  private static final String SELLER_ROLE = "SELLER";

  private final ProductService productService;

  // 상품 조회는 비로그인 공개.
  @GetMapping
  public ApiResponse<Page<ProductResponse>> getProducts(
      @RequestParam(required = false) Long categoryId,
      @PageableDefault(size = 20) Pageable pageable) {
    return ApiResponse.success(productService.getProducts(categoryId, pageable));
  }

  @GetMapping("/{productId}")
  public ApiResponse<ProductResponse> getProduct(@PathVariable Long productId) {
    return ApiResponse.success(productService.getProduct(productId));
  }

  // 상품 등록은 SELLER 이상. 등록자가 곧 소유 판매자(sellerId)가 된다.
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<ProductResponse> create(
      @RequestHeader(value = USER_ID_HEADER, required = false) Long userId,
      @RequestHeader(value = USER_ROLE_HEADER, required = false) String role,
      @Valid @RequestBody ProductCreateRequest request) {
    requireSeller(role);
    return ApiResponse.success(productService.createProduct(userId, request));
  }

  @PutMapping("/{productId}")
  public ApiResponse<ProductResponse> update(
      @RequestHeader(value = USER_ID_HEADER, required = false) Long userId,
      @RequestHeader(value = USER_ROLE_HEADER, required = false) String role,
      @PathVariable Long productId,
      @Valid @RequestBody ProductUpdateRequest request) {
    requireSeller(role);
    return ApiResponse.success(
        productService.updateProduct(productId, userId, isAdmin(role), request));
  }

  @DeleteMapping("/{productId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @RequestHeader(value = USER_ID_HEADER, required = false) Long userId,
      @RequestHeader(value = USER_ROLE_HEADER, required = false) String role,
      @PathVariable Long productId) {
    requireSeller(role);
    productService.deleteProduct(productId, userId, isAdmin(role));
  }

  private boolean isAdmin(String role) {
    return ADMIN_ROLE.equals(role);
  }

  // 게이트웨이가 주입한 역할 헤더로 SELLER 이상인지 방어적으로 검증한다(소유권은 서비스에서 재확인).
  private void requireSeller(String role) {
    if (!SELLER_ROLE.equals(role) && !ADMIN_ROLE.equals(role)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
  }
}
