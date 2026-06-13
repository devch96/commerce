package com.sparta.copa.copaproduct.product.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.copa.copaproduct.product.dto.response.ProductResponse;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 상품 상세 조회 Look-Aside 캐시. ProductResponse를 JSON 문자열로 저장한다.
 * 캐시 자체의 장애가 조회를 막지 않도록(가용성 우선), 직렬화/역직렬화 실패는 캐시 미스로 흡수한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCacheService {

  private static final String KEY_PREFIX = "product:";
  private static final Duration TTL = Duration.ofMinutes(10);

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  public Optional<ProductResponse> find(Long productId) {
    String json = redisTemplate.opsForValue().get(key(productId));
    if (json == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(objectMapper.readValue(json, ProductResponse.class));
    } catch (JsonProcessingException e) {
      // 포맷이 바뀌는 등으로 깨진 캐시는 폐기하고 미스로 처리해 DB에서 다시 적재되게 한다.
      log.warn("상품 캐시 역직렬화 실패, 폐기: {}", productId, e);
      redisTemplate.delete(key(productId));
      return Optional.empty();
    }
  }

  public void put(ProductResponse product) {
    try {
      redisTemplate.opsForValue().set(key(product.getId()), objectMapper.writeValueAsString(product), TTL);
    } catch (JsonProcessingException e) {
      log.warn("상품 캐시 직렬화 실패, 캐싱 건너뜀: {}", product.getId(), e);
    }
  }

  public void evict(Long productId) {
    redisTemplate.delete(key(productId));
  }

  private String key(Long productId) {
    return KEY_PREFIX + productId;
  }
}
