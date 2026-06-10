package com.sparta.copa.copa.user.controller;

import com.sparta.copa.copa.common.exception.BusinessException;
import com.sparta.copa.copa.common.exception.ErrorCode;
import com.sparta.copa.copa.common.response.ApiResponse;
import com.sparta.copa.copa.user.dto.request.ChangeRoleRequest;
import com.sparta.copa.copa.user.dto.response.UserResponse;
import com.sparta.copa.copa.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 운영자 전용 회원 관리 API. 게이트웨이 인증 후 X-User-Role로 ADMIN 권한을 재검증한다.
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class AdminUserController {

  private static final String USER_ROLE_HEADER = "X-User-Role";
  private static final String ADMIN_ROLE = "ADMIN";

  private final UserService userService;

  @PatchMapping("/{userId}/role")
  public ApiResponse<UserResponse> changeRole(
      @RequestHeader(value = USER_ROLE_HEADER, required = false) String role,
      @PathVariable Long userId,
      @Valid @RequestBody ChangeRoleRequest request) {
    verifyAdmin(role);
    return ApiResponse.success(userService.changeRole(userId, request.getRole()));
  }

  private void verifyAdmin(String role) {
    if (!ADMIN_ROLE.equals(role)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
  }
}
