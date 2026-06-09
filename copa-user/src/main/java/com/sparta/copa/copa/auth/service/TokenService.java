package com.sparta.copa.copa.auth.service;

import com.sparta.copa.copa.auth.config.JwtProperties;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

  private static final String REFRESH_KEY_PREFIX = "refresh:";

  private final StringRedisTemplate redisTemplate;
  private final Duration refreshTtl;

  public TokenService(StringRedisTemplate redisTemplate, JwtProperties jwtProperties) {
    this.redisTemplate = redisTemplate;
    this.refreshTtl = Duration.ofMillis(jwtProperties.getRefreshExpirationMillis());
  }

  public void store(Long userId, String refreshToken) {
    redisTemplate.opsForValue().set(key(userId), refreshToken, refreshTtl);
  }

  public boolean matches(Long userId, String refreshToken) {
    String stored = redisTemplate.opsForValue().get(key(userId));
    return stored != null && stored.equals(refreshToken);
  }

  public void delete(Long userId) {
    redisTemplate.delete(key(userId));
  }

  private String key(Long userId) {
    return REFRESH_KEY_PREFIX + userId;
  }
}
