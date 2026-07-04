package com.sparta.copa.copacoupon.fcfs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.copa.copacoupon.common.exception.BusinessException;
import com.sparta.copa.copacoupon.common.exception.ErrorCode;
import com.sparta.copa.copacoupon.coupon.repository.CouponRepository;
import com.sparta.copa.copacoupon.fcfs.dto.FcfsIssueResponse;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class FcfsCouponServiceTest {

  @Mock
  private StringRedisTemplate redisTemplate;
  @Mock
  private RedisScript<Long> couponIssueScript;
  @Mock
  private KafkaTemplate<String, String> kafkaTemplate;
  @Mock
  private CouponRepository couponRepository;

  // 이벤트의 LocalDateTime 직렬화를 위해 JavaTime 모듈을 등록한다(운영은 Spring Boot가 자동 등록).
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  private FcfsCouponService service() {
    // ObjectMapper는 mock이 아닌 실제 인스턴스가 필요하므로 직접 생성자로 조립한다.
    return new FcfsCouponService(redisTemplate, couponIssueScript, kafkaTemplate, objectMapper,
        couponRepository);
  }

  @Test
  @DisplayName("Lua가 성공(1)을 반환하면 coupon-issued로 발행하고 접수 응답을 반환한다")
  void issue_success_publishes() {
    FcfsCouponService fcfs = service();
    given(redisTemplate.execute(eq(couponIssueScript), anyList(), any()))
        .willReturn(1L);
    given(kafkaTemplate.send(anyString(), anyString(), anyString()))
        .willReturn(CompletableFuture.completedFuture(null));
    ValueOperations<String, String> valueOps = mockValueOps();
    given(valueOps.get(anyString())).willReturn("999");

    FcfsIssueResponse response = fcfs.issue(1L, 100L);

    assertThat(response.getCouponId()).isEqualTo(1L);
    assertThat(response.getRemainingStock()).isEqualTo(999);
    verify(kafkaTemplate).send(eq("coupon-issued"), eq("1"), anyString());
  }

  @Test
  @DisplayName("재고 미오픈(-3)이면 발행하지 않고 COUPON_FCFS_NOT_OPEN")
  void issue_notOpen() {
    FcfsCouponService fcfs = service();
    given(redisTemplate.execute(eq(couponIssueScript), anyList(), any())).willReturn(-3L);

    assertThatThrownBy(() -> fcfs.issue(1L, 100L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_FCFS_NOT_OPEN);
    verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("중복 발급(-2)이면 COUPON_ALREADY_ISSUED")
  void issue_alreadyIssued() {
    FcfsCouponService fcfs = service();
    given(redisTemplate.execute(eq(couponIssueScript), anyList(), any())).willReturn(-2L);

    assertThatThrownBy(() -> fcfs.issue(1L, 100L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_ALREADY_ISSUED);
  }

  @Test
  @DisplayName("품절(-1)이면 COUPON_OUT_OF_STOCK")
  void issue_soldOut() {
    FcfsCouponService fcfs = service();
    given(redisTemplate.execute(eq(couponIssueScript), anyList(), any())).willReturn(-1L);

    assertThatThrownBy(() -> fcfs.issue(1L, 100L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_OUT_OF_STOCK);
  }

  @Test
  @DisplayName("발행 실패 시 Redis 발급 효과를 보상(SREM+INCR)하고 COUPON_FCFS_PUBLISH_FAILED")
  void issue_publishFail_compensates() {
    FcfsCouponService fcfs = service();
    given(redisTemplate.execute(eq(couponIssueScript), anyList(), any())).willReturn(1L);
    given(kafkaTemplate.send(anyString(), anyString(), anyString()))
        .willReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
    ValueOperations<String, String> valueOps = mockValueOps();
    @SuppressWarnings("unchecked")
    org.springframework.data.redis.core.SetOperations<String, String> setOps =
        org.mockito.Mockito.mock(org.springframework.data.redis.core.SetOperations.class);
    given(redisTemplate.opsForSet()).willReturn(setOps);

    assertThatThrownBy(() -> fcfs.issue(1L, 100L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_FCFS_PUBLISH_FAILED);

    // 보상: 발급자 집합에서 제거 + 재고 원복.
    verify(setOps).remove("coupon:1:issued", "100");
    verify(valueOps).increment("coupon:1:stock");
  }

  private ValueOperations<String, String> mockValueOps() {
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> valueOps = org.mockito.Mockito.mock(ValueOperations.class);
    given(redisTemplate.opsForValue()).willReturn(valueOps);
    return valueOps;
  }
}
