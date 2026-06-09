package com.sparta.copa.copagateway.jwt;

import com.sparta.copa.copagateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

  private final SecretKey key;

  public JwtProvider(JwtProperties properties) {
    this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
  }

  public Claims parse(String token) {
    return Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }
}
