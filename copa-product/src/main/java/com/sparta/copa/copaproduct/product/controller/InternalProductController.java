package com.sparta.copa.copaproduct.product.controller;

import com.sparta.copa.copaproduct.common.response.ApiResponse;
import com.sparta.copa.copaproduct.product.dto.response.ProductResponse;
import com.sparta.copa.copaproduct.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 서비스 간 내부 호출용. 게이트웨이를 거치지 않고 서비스 메시 내부에서만 접근한다.
@RestController
@RequestMapping("/internal/products")
@RequiredArgsConstructor
public class InternalProductController {

  private final ProductService productService;

  @GetMapping("/{productId}")
  public ApiResponse<ProductResponse> getProduct(@PathVariable Long productId) {
    return ApiResponse.success(productService.getProduct(productId));
  }
}
