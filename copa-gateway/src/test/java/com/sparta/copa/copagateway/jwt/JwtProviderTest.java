package com.sparta.copa.copagateway.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.copa.copagateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

  private static final String SECRET =
      "copa-gateway-unit-test-secret-key-must-be-at-least-256-bits-long";
  private static final String OTHER_SECRET =
      "a-completely-different-256-bit-secret-key-for-tampered-token-test";

  private SecretKey key;
  private JwtProvider jwtProvider;

  @BeforeEach
  void setUp() {
    this.key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    this.jwtProvider = new JwtProvider(new JwtProperties(SECRET));
  }

  @Test
  @DisplayName("유효한 토큰을 파싱하면 subject와 role 클레임을 반환한다")
  void parseValidTokenReturnsClaims() {
    String token = Jwts.builder()
        .subject("42")
        .claim("role", "USER")
        .signWith(key)
        .compact();

    Claims claims = jwtProvider.parse(token);

    assertThat(claims.getSubject()).isEqualTo("42");
    assertThat(claims.get("role", String.class)).isEqualTo("USER");
  }

  @Test
  @DisplayName("다른 비밀키로 서명된 토큰은 검증에 실패해 예외를 던진다")
  void parseTokenSignedWithOtherKeyThrows() {
    SecretKey otherKey = Keys.hmacShaKeyFor(OTHER_SECRET.getBytes(StandardCharsets.UTF_8));
    String forged = Jwts.builder()
        .subject("42")
        .claim("role", "ADMIN")
        .signWith(otherKey)
        .compact();

    assertThatThrownBy(() -> jwtProvider.parse(forged))
        .isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("형식이 잘못된 토큰은 예외를 던진다")
  void parseMalformedTokenThrows() {
    assertThatThrownBy(() -> jwtProvider.parse("not-a-jwt"))
        .isInstanceOf(RuntimeException.class);
  }
}