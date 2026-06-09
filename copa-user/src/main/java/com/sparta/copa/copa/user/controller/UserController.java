package com.sparta.copa.copa.user.controller;

import com.sparta.copa.copa.common.response.ApiResponse;
import com.sparta.copa.copa.user.dto.response.UserResponse;
import com.sparta.copa.copa.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController {

  private static final String USER_ID_HEADER = "X-User-Id";

  private final UserService userService;

  @GetMapping("/users/me")
  public ApiResponse<UserResponse> me(@RequestHeader(USER_ID_HEADER) Long userId) {
    return ApiResponse.success(userService.getProfile(userId));
  }

  // 서비스 간 내부 호출용. 게이트웨이를 거치지 않고 서비스 메시 내부에서만 접근한다.
  @GetMapping("/internal/users/{userId}")
  public ApiResponse<UserResponse> getUser(@PathVariable Long userId) {
    return ApiResponse.success(userService.getProfile(userId));
  }
}
