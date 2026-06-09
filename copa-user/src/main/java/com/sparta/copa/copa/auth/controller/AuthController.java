package com.sparta.copa.copa.auth.controller;

import com.sparta.copa.copa.auth.dto.LoginRequest;
import com.sparta.copa.copa.auth.dto.ReissueRequest;
import com.sparta.copa.copa.auth.dto.SignUpRequest;
import com.sparta.copa.copa.auth.dto.TokenResponse;
import com.sparta.copa.copa.auth.service.AuthService;
import com.sparta.copa.copa.common.response.ApiResponse;
import com.sparta.copa.copa.user.dto.response.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

  private static final String USER_ID_HEADER = "X-User-Id";

  private final AuthService authService;

  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<UserResponse> signup(@Valid @RequestBody SignUpRequest request) {
    return ApiResponse.success(authService.signup(request));
  }

  @PostMapping("/login")
  public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
    return ApiResponse.success(authService.login(request));
  }

  @PostMapping("/reissue")
  public ApiResponse<TokenResponse> reissue(@Valid @RequestBody ReissueRequest request) {
    return ApiResponse.success(authService.reissue(request.getRefreshToken()));
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@RequestHeader(USER_ID_HEADER) Long userId) {
    authService.logout(userId);
  }
}
