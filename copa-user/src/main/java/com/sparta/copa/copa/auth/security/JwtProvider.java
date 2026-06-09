package com.sparta.copa.copa.auth.security;

import com.sparta.copa.copa.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

  private static final String ROLE_CLAIM = "role";

  private final SecretKey key;
  private final Duration accessExpiration;
  private final Duration refreshExpiration;

  public JwtProvider(JwtProperties properties) {
    this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    this.accessExpiration = Duration.ofMillis(properties.getAccessExpirationMillis());
    this.refreshExpiration = Duration.ofMillis(properties.getRefreshExpirationMillis());
  }

  public String createAccessToken(Long userId, String role) {
    return buildToken(userId, role, accessExpiration);
  }

  public String createRefreshToken(Long userId, String role) {
    return buildToken(userId, role, refreshExpiration);
  }

  public Claims parse(String token) {
    return Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  public Long getUserId(String token) {
    return Long.valueOf(parse(token).getSubject());
  }

  private String buildToken(Long userId, String role, Duration expiration) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .claim(ROLE_CLAIM, role)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(expiration)))
        .signWith(key)
        .compact();
  }
}
