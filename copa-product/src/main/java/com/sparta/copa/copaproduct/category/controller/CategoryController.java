package com.sparta.copa.copaproduct.category.controller;

import com.sparta.copa.copaproduct.category.dto.request.CategoryCreateRequest;
import com.sparta.copa.copaproduct.category.dto.request.CategoryUpdateRequest;
import com.sparta.copa.copaproduct.category.dto.response.CategoryResponse;
import com.sparta.copa.copaproduct.category.service.CategoryService;
import com.sparta.copa.copaproduct.common.exception.BusinessException;
import com.sparta.copa.copaproduct.common.exception.ErrorCode;
import com.sparta.copa.copaproduct.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

  private static final String USER_ROLE_HEADER = "X-User-Role";
  private static final String ADMIN_ROLE = "ADMIN";

  private final CategoryService categoryService;

  // 카테고리 조회는 비로그인 공개(상품 탐색용).
  @GetMapping
  public ApiResponse<List<CategoryResponse>> tree() {
    return ApiResponse.success(categoryService.getTree());
  }

  // 카테고리 생성/수정/삭제는 운영자(ADMIN) 전용.
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<CategoryResponse> create(
      @RequestHeader(value = USER_ROLE_HEADER, required = false) String role,
      @Valid @RequestBody CategoryCreateRequest request) {
    verifyAdmin(role);
    return ApiResponse.success(categoryService.create(request.getName(), request.getParentId()));
  }

  @PutMapping("/{categoryId}")
  public ApiResponse<CategoryResponse> update(
      @RequestHeader(value = USER_ROLE_HEADER, required = false) String role,
      @PathVariable Long categoryId,
      @Valid @RequestBody CategoryUpdateRequest request) {
    verifyAdmin(role);
    return ApiResponse.success(
        categoryService.update(categoryId, request.getName(), request.getParentId()));
  }

  @DeleteMapping("/{categoryId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @RequestHeader(value = USER_ROLE_HEADER, required = false) String role,
      @PathVariable Long categoryId) {
    verifyAdmin(role);
    categoryService.delete(categoryId);
  }

  private void verifyAdmin(String role) {
    if (!ADMIN_ROLE.equals(role)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
  }
}
