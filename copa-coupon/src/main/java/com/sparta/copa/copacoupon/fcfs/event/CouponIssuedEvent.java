package com.sparta.copa.copacoupon.fcfs.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 선착순 발급 성공 이벤트. Redis 원자 발급을 통과한 건만 발행되고, 컨슈머가 이 payload로 DB에 UserCoupon을 적재한다.
 * eventId는 at-least-once 중복 배달 시 로깅·추적용(멱등 판단은 (couponId,userId) 유니크로 한다).
 */
@Getter
public class CouponIssuedEvent {

  private final String eventId;
  private final Long couponId;
  private final Long userId;
  private final LocalDateTime issuedAt;

  @JsonCreator
  public CouponIssuedEvent(
      @JsonProperty("eventId") String eventId,
      @JsonProperty("couponId") Long couponId,
      @JsonProperty("userId") Long userId,
      @JsonProperty("issuedAt") LocalDateTime issuedAt) {
    this.eventId = eventId;
    this.couponId = couponId;
    this.userId = userId;
    this.issuedAt = issuedAt;
  }
}
