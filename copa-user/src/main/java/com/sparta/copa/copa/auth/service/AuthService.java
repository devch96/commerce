package com.sparta.copa.copa.auth.service;

import com.sparta.copa.copa.auth.dto.LoginRequest;
import com.sparta.copa.copa.auth.dto.SignUpRequest;
import com.sparta.copa.copa.auth.dto.TokenResponse;
import com.sparta.copa.copa.auth.security.JwtProvider;
import com.sparta.copa.copa.common.exception.BusinessException;
import com.sparta.copa.copa.common.exception.ErrorCode;
import com.sparta.copa.copa.user.domain.User;
import com.sparta.copa.copa.user.dto.response.UserResponse;
import com.sparta.copa.copa.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserService userService;
  private final PasswordEncoder passwordEncoder;
  private final JwtProvider jwtProvider;
  private final TokenService tokenService;

  // BCrypt 해싱은 비용이 크므로 트랜잭션 밖에서 처리하고, 영속화는 UserService에 위임한다.
  public UserResponse signup(SignUpRequest request) {
    String encodedPassword = passwordEncoder.encode(request.getPassword());
    User saved = userService.register(
        request.getEmail(), encodedPassword, request.getName(), request.getPhone());
    return UserResponse.from(saved);
  }

  @Transactional(readOnly = true)
  public TokenResponse login(LoginRequest request) {
    User user = userService.findByEmail(request.getEmail())
        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }
    if (!user.getIsActive()) {
      throw new BusinessException(ErrorCode.ACCOUNT_DEACTIVATED);
    }
    return issueTokens(user);
  }

  // 재발급 시 새 Refresh Token으로 교체(Rotation)하고 이전 토큰은 즉시 무효화한다.
  public TokenResponse reissue(String refreshToken) {
    Long userId = parseUserId(refreshToken);
    if (!tokenService.matches(userId, refreshToken)) {
      throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
    }
    User user = userService.getById(userId);
    // 탈퇴/비활성 계정은 토큰만 있어도 재발급으로 access를 무한 연장할 수 없도록 차단하고 세션을 정리한다.
    if (!user.getIsActive()) {
      tokenService.delete(userId);
      throw new BusinessException(ErrorCode.ACCOUNT_DEACTIVATED);
    }
    return issueTokens(user);
  }

  public void logout(Long userId) {
    tokenService.delete(userId);
  }

  private TokenResponse issueTokens(User user) {
    String role = user.getRole().name();
    String accessToken = jwtProvider.createAccessToken(user.getId(), role);
    String refreshToken = jwtProvider.createRefreshToken(user.getId(), role);
    tokenService.store(user.getId(), refreshToken);
    return TokenResponse.of(accessToken, refreshToken);
  }

  private Long parseUserId(String refreshToken) {
    try {
      return jwtProvider.getUserId(refreshToken);
    } catch (RuntimeException e) {
      throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
    }
  }
}
