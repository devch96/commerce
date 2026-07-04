package com.sparta.copa.copaproduct.category.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.copa.copaproduct.category.dto.CategorySnapshot;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 카테고리 전체 스냅샷 Look-Aside 캐시. 트리·하위 트리 조회가 매 요청 findAll을 치던 것을 대체한다.
 * 카테고리는 초소형·저빈도 변경이라 전체를 한 키에 통째로 캐시하고, 관리자 변경 시 무효화한다.
 * 캐시 장애가 조회를 막지 않도록(가용성 우선) 직렬화/역직렬화 실패는 캐시 미스로 흡수한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryCacheService {

  private static final String KEY = "categories:all";
  // 관리자 변경 시 즉시 무효화하지만, 무효화 누락에 대비한 안전망 TTL.
  private static final Duration TTL = Duration.ofMinutes(30);

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  public Optional<List<CategorySnapshot>> find() {
    String json = redisTemplate.opsForValue().get(KEY);
    if (json == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(objectMapper.readValue(json, new TypeReference<List<CategorySnapshot>>() {}));
    } catch (JsonProcessingException e) {
      // 포맷이 바뀌는 등으로 깨진 캐시는 폐기하고 미스로 처리해 DB에서 다시 적재되게 한다.
      log.warn("카테고리 캐시 역직렬화 실패, 폐기", e);
      redisTemplate.delete(KEY);
      return Optional.empty();
    }
  }

  public void put(List<CategorySnapshot> categories) {
    try {
      redisTemplate.opsForValue().set(KEY, objectMapper.writeValueAsString(categories), TTL);
    } catch (JsonProcessingException e) {
      log.warn("카테고리 캐시 직렬화 실패, 캐싱 건너뜀", e);
    }
  }

  public void evict() {
    redisTemplate.delete(KEY);
  }
}
