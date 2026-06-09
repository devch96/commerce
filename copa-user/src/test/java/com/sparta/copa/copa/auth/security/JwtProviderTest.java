package com.sparta.copa.copa.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.copa.copa.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

  private static final String SECRET =
      "copa-user-unit-test-secret-key-must-be-at-least-256-bits-long-00";

  private JwtProvider jwtProvider;

  @BeforeEach
  void setUp() {
    this.jwtProvider = new JwtProvider(new JwtProperties(SECRET, 1_800_000L, 1_209_600_000L));
  }

  @Test
  @DisplayName("Access Token에는 사용자 ID(subject)와 role 클레임이 담긴다")
  void createAccessTokenContainsSubjectAndRole() {
    String token = jwtProvider.createAccessToken(42L, "USER");

    Claims claims = jwtProvider.parse(token);
    assertThat(claims.getSubject()).isEqualTo("42");
    assertThat(claims.get("role", String.class)).isEqualTo("USER");
  }

  @Test
  @DisplayName("getUserId는 토큰의 subject를 Long으로 반환한다")
  void getUserIdReturnsSubjectAsLong() {
    String token = jwtProvider.createRefreshToken(7L, "ADMIN");

    assertThat(jwtProvider.getUserId(token)).isEqualTo(7L);
  }

  @Test
  @DisplayName("형식이 잘못된 토큰은 예외를 던진다")
  void parseMalformedTokenThrows() {
    assertThatThrownBy(() -> jwtProvider.parse("not-a-jwt"))
        .isInstanceOf(RuntimeException.class);
  }
}
