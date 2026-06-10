package com.sparta.copa.copa.user.controller;

import com.sparta.copa.copa.common.response.ApiResponse;
import com.sparta.copa.copa.user.dto.request.ChangePasswordRequest;
import com.sparta.copa.copa.user.dto.request.UpdateProfileRequest;
import com.sparta.copa.copa.user.dto.request.WithdrawRequest;
import com.sparta.copa.copa.user.dto.response.UserResponse;
import com.sparta.copa.copa.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

  private static final String USER_ID_HEADER = "X-User-Id";

  private final UserService userService;

  @GetMapping("/me")
  public ApiResponse<UserResponse> me(@RequestHeader(USER_ID_HEADER) Long userId) {
    return ApiResponse.success(userService.getProfile(userId));
  }

  @PutMapping("/me")
  public ApiResponse<UserResponse> updateMe(@RequestHeader(USER_ID_HEADER) Long userId,
      @Valid @RequestBody UpdateProfileRequest request) {
    return ApiResponse.success(
        userService.updateProfile(userId, request.getName(), request.getPhone()));
  }

  @PatchMapping("/me/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(@RequestHeader(USER_ID_HEADER) Long userId,
      @Valid @RequestBody ChangePasswordRequest request) {
    userService.changePassword(userId, request.getCurrentPassword(), request.getNewPassword());
  }

  @DeleteMapping("/me")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void withdraw(@RequestHeader(USER_ID_HEADER) Long userId,
      @Valid @RequestBody WithdrawRequest request) {
    userService.withdraw(userId, request.getPassword());
  }
}
