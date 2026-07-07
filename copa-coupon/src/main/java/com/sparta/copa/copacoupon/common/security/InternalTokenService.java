package com.sparta.copa.copacoupon.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 게이트웨이/내부 서비스가 발급한 서명 토큰을 검증한다(HS256, 공유 시크릿).
// - 신원 토큰(typ=identity): 게이트웨이가 발급, 사용자 uid/role을 담는다.
// - 서비스 토큰(typ=service): /internal 호출자가 발급, 호출 서비스명을 담는다.
@Component
public class InternalTokenService {

  public static final String IDENTITY_HEADER = "X-Copa-Identity";
  public static final String SERVICE_HEADER = "X-Copa-Service";
  public static final String ROLE_CLAIM = "role";

  private static final String TYPE_CLAIM = "typ";
  private static final String SVC_CLAIM = "svc";
  private static final String TYPE_IDENTITY = "identity";
  private static final String TYPE_SERVICE = "service";
  private static final Duration TTL = Duration.ofSeconds(60);

  private final SecretKey key;

  public InternalTokenService(
      @Value("${copa.internal.secret:local-dev-internal-secret-please-change-this-to-a-256-bit-value}")
      String secret) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  // /internal 호출자가 자신을 증명하기 위해 붙이는 서비스 토큰. (호출하는 서비스에서만 사용)
  public String issueService(String serviceName) {
    Instant now = Instant.now();
    return Jwts.builder()
        .claim(SVC_CLAIM, serviceName)
        .claim(TYPE_CLAIM, TYPE_SERVICE)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(TTL)))
        .signWith(key)
        .compact();
  }

  public Claims verifyIdentity(String token) {
    return verify(token, TYPE_IDENTITY);
  }

  public Claims verifyService(String token) {
    return verify(token, TYPE_SERVICE);
  }

  // 서명·만료·타입이 모두 유효하면 클레임을 반환하고, 그렇지 않으면 null(호출 측에서 401 처리).
  private Claims verify(String token, String expectedType) {
    if (token == null || token.isBlank()) {
      return null;
    }
    try {
      Claims claims = Jwts.parser()
          .verifyWith(key)
          .build()
          .parseSignedClaims(token)
          .getPayload();
      if (!expectedType.equals(claims.get(TYPE_CLAIM, String.class))) {
        return null;
      }
      return claims;
    } catch (JwtException | IllegalArgumentException e) {
      return null;
    }
  }
}