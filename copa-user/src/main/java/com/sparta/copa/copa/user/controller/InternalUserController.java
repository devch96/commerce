package com.sparta.copa.copa.user.controller;

import com.sparta.copa.copa.common.response.ApiResponse;
import com.sparta.copa.copa.user.dto.response.UserResponse;
import com.sparta.copa.copa.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 서비스 간 내부 호출용. 게이트웨이를 거치지 않고 서비스 메시 내부에서만 접근한다.
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

  private final UserService userService;

  @GetMapping("/{userId}")
  public ApiResponse<UserResponse> getUser(@PathVariable Long userId) {
    return ApiResponse.success(userService.getProfile(userId));
  }
}
