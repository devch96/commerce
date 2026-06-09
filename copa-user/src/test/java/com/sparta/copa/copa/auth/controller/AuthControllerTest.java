package com.sparta.copa.copa.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.copa.copa.auth.dto.LoginRequest;
import com.sparta.copa.copa.auth.dto.SignUpRequest;
import com.sparta.copa.copa.auth.dto.TokenResponse;
import com.sparta.copa.copa.auth.service.AuthService;
import com.sparta.copa.copa.common.exception.BusinessException;
import com.sparta.copa.copa.common.exception.ErrorCode;
import com.sparta.copa.copa.user.domain.UserRole;
import com.sparta.copa.copa.user.dto.response.UserResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private AuthService authService;

  @Test
  @DisplayName("POST /auth/signup - 가입 성공 시 201과 사용자 정보를 반환한다")
  void signup() throws Exception {
    SignUpRequest request = SignUpRequest.builder()
        .email("user@copa.com").password("password123").name("코파").build();
    given(authService.signup(any(SignUpRequest.class))).willReturn(
        UserResponse.builder().id(1L).email("user@copa.com").name("코파").role(UserRole.USER).build());

    mockMvc.perform(post("/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.email").value("user@copa.com"))
        .andExpect(jsonPath("$.data.role").value("USER"));
  }

  @Test
  @DisplayName("POST /auth/signup - 이메일 형식이 잘못되면 400을 반환한다")
  void signupValidationFails() throws Exception {
    SignUpRequest request = SignUpRequest.builder()
        .email("not-an-email").password("password123").name("코파").build();

    mockMvc.perform(post("/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  @DisplayName("POST /auth/login - 성공 시 토큰을 반환한다")
  void login() throws Exception {
    LoginRequest request = LoginRequest.builder()
        .email("user@copa.com").password("password123").build();
    given(authService.login(any(LoginRequest.class)))
        .willReturn(TokenResponse.of("access-token", "refresh-token"));

    mockMvc.perform(post("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").value("access-token"))
        .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
  }

  @Test
  @DisplayName("POST /auth/login - 자격 증명 오류는 401을 반환한다")
  void loginUnauthorized() throws Exception {
    LoginRequest request = LoginRequest.builder()
        .email("user@copa.com").password("wrong").build();
    given(authService.login(any(LoginRequest.class)))
        .willThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS));

    mockMvc.perform(post("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  @DisplayName("POST /auth/logout - X-User-Id 헤더로 로그아웃하면 204를 반환한다")
  void logout() throws Exception {
    mockMvc.perform(post("/auth/logout").header("X-User-Id", "1"))
        .andExpect(status().isNoContent());

    verify(authService).logout(1L);
  }
}
