package com.sparta.copa.copacoupon.fcfs.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.copa.copacoupon.common.enums.CouponStatus;
import com.sparta.copa.copacoupon.common.exception.BusinessException;
import com.sparta.copa.copacoupon.common.exception.ErrorCode;
import com.sparta.copa.copacoupon.coupon.domain.Coupon;
import com.sparta.copa.copacoupon.coupon.repository.CouponRepository;
import com.sparta.copa.copacoupon.fcfs.dto.FcfsIssueResponse;
import com.sparta.copa.copacoupon.fcfs.event.CouponIssuedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 선착순 쿠폰 발급의 1차 관문(source of truth = Redis).
 *
 * <p>발급 흐름: (1) 관리자가 재고를 Redis에 시드({@link #open}) → (2) 사용자 요청을 Lua로 원자 발급
 * (재고 차감 + 1인 1매 집합) → (3) 통과분만 Kafka {@code coupon-issued}로 발행 → (4) 컨슈머가 DB에 적재.
 * DB 락에 트래픽이 몰리는 병목을 없애고, DB의 (coupon_id,user_id) 유니크가 최종 방어선이 된다(설계 08-B).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FcfsCouponService {

  public static final String TOPIC_COUPON_ISSUED = "coupon-issued";
  private static final String STOCK_KEY = "coupon:%d:stock";
  private static final String ISSUED_KEY = "coupon:%d:issued";
  private static final long PUBLISH_TIMEOUT_SECONDS = 5;

  // Lua 반환 코드.
  private static final long RESULT_SUCCESS = 1;
  private static final long RESULT_SOLD_OUT = -1;
  private static final long RESULT_ALREADY_ISSUED = -2;
  private static final long RESULT_NOT_OPEN = -3;

  // 요청 시 임시 발급 상한(설계 요청: 1000명). 관리자가 명시하지 않으면 이 값을 시드한다.
  @Value("${copa.coupon.fcfs.default-quantity:1000}")
  private int defaultQuantity;

  private final StringRedisTemplate redisTemplate;
  private final RedisScript<Long> couponIssueScript;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;
  private final CouponRepository couponRepository;

  /**
   * 선착순 이벤트 오픈: 재고를 Redis에 시드한다(관리자). 재시드는 재고만 덮어쓰고 발급자 집합은 보존해
   * 이미 받은 사용자가 재오픈으로 중복 발급되지 않게 한다.
   */
  @Transactional(readOnly = true)
  public long open(Long couponId, Integer quantity) {
    Coupon coupon = couponRepository.findById(couponId)
        .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));
    if (coupon.getStatus() != CouponStatus.ACTIVE) {
      throw new BusinessException(ErrorCode.COUPON_NOT_ISSUABLE);
    }
    long stock = quantity == null ? defaultQuantity : quantity;
    if (stock <= 0) {
      throw new BusinessException(ErrorCode.INVALID_COUPON_DEFINITION);
    }
    redisTemplate.opsForValue().set(stockKey(couponId), String.valueOf(stock));
    log.info("선착순 쿠폰 오픈: couponId={}, stock={}", couponId, stock);
    return stock;
  }

  /**
   * 선착순 발급. Lua 원자 연산으로 재고 차감 + 1인 1매를 통제하고, 성공분만 Kafka로 발행한다.
   * 발행이 실패하면 Redis 효과(재고·발급자)를 되돌려(보상) 재시도 가능한 상태로 남긴다.
   */
  public FcfsIssueResponse issue(Long couponId, Long userId) {
    Long raw = redisTemplate.execute(couponIssueScript,
        List.of(stockKey(couponId), issuedKey(couponId)), String.valueOf(userId));
    long code = raw == null ? RESULT_SOLD_OUT : raw;

    if (code == RESULT_NOT_OPEN) {
      throw new BusinessException(ErrorCode.COUPON_FCFS_NOT_OPEN);
    }
    if (code == RESULT_ALREADY_ISSUED) {
      throw new BusinessException(ErrorCode.COUPON_ALREADY_ISSUED);
    }
    if (code == RESULT_SOLD_OUT) {
      throw new BusinessException(ErrorCode.COUPON_OUT_OF_STOCK);
    }
    if (code != RESULT_SUCCESS) {
      throw new BusinessException(ErrorCode.COUPON_NOT_ISSUABLE);
    }

    publishOrCompensate(couponId, userId);
    return FcfsIssueResponse.accepted(couponId, userId, remainingStock(couponId));
  }

  // 성공분 발행. 발행 실패 시 Redis 발급 효과를 롤백해 재고를 회수하고 사용자가 다시 시도할 수 있게 한다.
  private void publishOrCompensate(Long couponId, Long userId) {
    try {
      CouponIssuedEvent event = new CouponIssuedEvent(
          UUID.randomUUID().toString(), couponId, userId, LocalDateTime.now());
      String payload = objectMapper.writeValueAsString(event);
      kafkaTemplate.send(TOPIC_COUPON_ISSUED, String.valueOf(couponId), payload)
          .get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      compensate(couponId, userId);
      throw new BusinessException(ErrorCode.COUPON_FCFS_PUBLISH_FAILED);
    } catch (JsonProcessingException e) {
      compensate(couponId, userId);
      throw new BusinessException(ErrorCode.COUPON_FCFS_PUBLISH_FAILED);
    } catch (Exception e) {
      log.error("선착순 발급 이벤트 발행 실패, Redis 보상 수행: couponId={}, userId={}", couponId, userId, e);
      compensate(couponId, userId);
      throw new BusinessException(ErrorCode.COUPON_FCFS_PUBLISH_FAILED);
    }
  }

  // 발행 실패 보상: 발급자 집합에서 제거 + 재고 원복. 발급이 확정되지 않았으므로 재고 풀에 되돌린다.
  private void compensate(Long couponId, Long userId) {
    try {
      redisTemplate.opsForSet().remove(issuedKey(couponId), String.valueOf(userId));
      redisTemplate.opsForValue().increment(stockKey(couponId));
    } catch (Exception e) {
      // 보상까지 실패하면 재고가 한 장 잠긴다(오버셀링은 아님). 대사(reconciliation)로 정정한다(설계 08-F).
      log.error("선착순 발급 보상 실패(재고 1건 잠김 가능): couponId={}, userId={}", couponId, userId, e);
    }
  }

  private long remainingStock(Long couponId) {
    String value = redisTemplate.opsForValue().get(stockKey(couponId));
    return value == null ? 0 : Long.parseLong(value);
  }

  private String stockKey(Long couponId) {
    return String.format(STOCK_KEY, couponId);
  }

  private String issuedKey(Long couponId) {
    return String.format(ISSUED_KEY, couponId);
  }
}
