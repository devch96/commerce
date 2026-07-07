package com.sparta.copa.copagateway.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 게이트웨이가 사용자 JWT를 검증한 뒤, 내부 서비스가 신뢰할 수 있도록 서명한 단명(短命) 신원 토큰을 발급한다.
// 하위 서비스는 이 토큰의 서명을 검증한 값만 신뢰하고, 클라이언트가 직접 붙인 X-User-* 헤더는 무시한다.
@Component
public class InternalTokenIssuer {

  private static final String TYPE_CLAIM = "typ";
  private static final String ROLE_CLAIM = "role";
  private static final String TYPE_IDENTITY = "identity";
  // 신원 토큰은 게이트웨이→하위 서비스 1홉 전파용이라 수명이 짧아도 충분하다(재생 공격 창 최소화).
  private static final Duration TTL = Duration.ofSeconds(60);

  private final SecretKey key;

  public InternalTokenIssuer(
      @Value("${copa.internal.secret:local-dev-internal-secret-please-change-this-to-a-256-bit-value}")
      String secret) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  public String issueIdentity(String userId, String role) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(userId)
        .claim(ROLE_CLAIM, role)
        .claim(TYPE_CLAIM, TYPE_IDENTITY)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(TTL)))
        .signWith(key)
        .compact();
  }
}