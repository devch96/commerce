package com.sparta.copa.copa.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.copa.copa.auth.dto.LoginRequest;
import com.sparta.copa.copa.auth.dto.SignUpRequest;
import com.sparta.copa.copa.auth.dto.TokenResponse;
import com.sparta.copa.copa.auth.security.JwtProvider;
import com.sparta.copa.copa.common.exception.BusinessException;
import com.sparta.copa.copa.common.exception.ErrorCode;
import com.sparta.copa.copa.user.domain.User;
import com.sparta.copa.copa.user.dto.response.UserResponse;
import com.sparta.copa.copa.user.service.UserService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock
  private UserService userService;
  @Mock
  private PasswordEncoder passwordEncoder;
  @Mock
  private JwtProvider jwtProvider;
  @Mock
  private TokenService tokenService;

  @InjectMocks
  private AuthService authService;

  private User userWithId(Long id) {
    User user = User.create("user@copa.com", "encoded-password", "코파");
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  @Test
  @DisplayName("회원가입 시 비밀번호를 인코딩해 UserService에 위임하고 사용자 정보를 반환한다")
  void signupSuccess() {
    SignUpRequest request = SignUpRequest.builder()
        .email("user@copa.com").password("password123").name("코파").build();
    given(passwordEncoder.encode("password123")).willReturn("encoded-password");
    given(userService.register("user@copa.com", "encoded-password", "코파"))
        .willReturn(userWithId(1L));

    UserResponse response = authService.signup(request);

    assertThat(response.getEmail()).isEqualTo("user@copa.com");
    verify(passwordEncoder).encode("password123");
    verify(userService).register("user@copa.com", "encoded-password", "코파");
  }

  @Test
  @DisplayName("로그인 성공 시 토큰을 발급하고 Refresh Token을 저장한다")
  void loginSuccess() {
    LoginRequest request = LoginRequest.builder()
        .email("user@copa.com").password("password123").build();
    given(userService.findByEmail("user@copa.com")).willReturn(Optional.of(userWithId(1L)));
    given(passwordEncoder.matches("password123", "encoded-password")).willReturn(true);
    given(jwtProvider.createAccessToken(1L, "USER")).willReturn("access-token");
    given(jwtProvider.createRefreshToken(1L, "USER")).willReturn("refresh-token");

    TokenResponse response = authService.login(request);

    assertThat(response.getAccessToken()).isEqualTo("access-token");
    assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
    assertThat(response.getTokenType()).isEqualTo("Bearer");
    verify(tokenService).store(1L, "refresh-token");
  }

  @Test
  @DisplayName("비밀번호가 일치하지 않으면 로그인에 실패한다")
  void loginWrongPassword() {
    LoginRequest request = LoginRequest.builder()
        .email("user@copa.com").password("wrong").build();
    given(userService.findByEmail("user@copa.com")).willReturn(Optional.of(userWithId(1L)));
    given(passwordEncoder.matches("wrong", "encoded-password")).willReturn(false);

    assertThatThrownBy(() -> authService.login(request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    verify(tokenService, never()).store(anyLong(), anyString());
  }

  @Test
  @DisplayName("존재하지 않는 이메일로 로그인하면 자격 증명 오류를 던진다")
  void loginUserNotFound() {
    LoginRequest request = LoginRequest.builder()
        .email("none@copa.com").password("password123").build();
    given(userService.findByEmail("none@copa.com")).willReturn(Optional.empty());

    assertThatThrownBy(() -> authService.login(request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);
  }

  @Test
  @DisplayName("저장된 Refresh Token과 일치하면 토큰을 재발급한다")
  void reissueSuccess() {
    given(jwtProvider.getUserId("refresh-token")).willReturn(1L);
    given(tokenService.matches(1L, "refresh-token")).willReturn(true);
    given(userService.getById(1L)).willReturn(userWithId(1L));
    given(jwtProvider.createAccessToken(1L, "USER")).willReturn("new-access");
    given(jwtProvider.createRefreshToken(1L, "USER")).willReturn("new-refresh");

    TokenResponse response = authService.reissue("refresh-token");

    assertThat(response.getAccessToken()).isEqualTo("new-access");
    verify(tokenService).store(1L, "new-refresh");
  }

  @Test
  @DisplayName("저장된 Refresh Token과 일치하지 않으면 재발급에 실패한다")
  void reissueMismatch() {
    given(jwtProvider.getUserId("refresh-token")).willReturn(1L);
    given(tokenService.matches(1L, "refresh-token")).willReturn(false);

    assertThatThrownBy(() -> authService.reissue("refresh-token"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    verify(userService, never()).getById(anyLong());
  }

  @Test
  @DisplayName("파싱할 수 없는 Refresh Token이면 재발급에 실패한다")
  void reissueUnparsableToken() {
    given(jwtProvider.getUserId("bad")).willThrow(new IllegalArgumentException("invalid"));

    assertThatThrownBy(() -> authService.reissue("bad"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
  }

  @Test
  @DisplayName("로그아웃하면 저장된 Refresh Token을 삭제한다")
  void logout() {
    authService.logout(1L);

    verify(tokenService).delete(1L);
  }
}
