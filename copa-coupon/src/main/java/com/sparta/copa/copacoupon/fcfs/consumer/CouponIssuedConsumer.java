package com.sparta.copa.copacoupon.fcfs.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.copa.copacoupon.coupon.service.CouponService;
import com.sparta.copa.copacoupon.fcfs.event.CouponIssuedEvent;
import com.sparta.copa.copacoupon.fcfs.service.FcfsCouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * {@code coupon-issued} 구독 → DB에 UserCoupon 적재. Redis에서 이미 선착순이 확정된 건이므로 여기선 영속화만 한다.
 * 발행은 at-least-once이므로 소비는 멱등이어야 한다((coupon_id,user_id) 유니크 + 선존재 검사).
 *
 * <p>테스트·브로커 부재 환경에서는 {@code copa.coupon.fcfs.consumer.enabled=false}로 비활성화한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "copa.coupon.fcfs.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class CouponIssuedConsumer {

  private final ObjectMapper objectMapper;
  private final CouponService couponService;

  @KafkaListener(
      topics = FcfsCouponService.TOPIC_COUPON_ISSUED,
      groupId = "${copa.coupon.fcfs.consumer.group-id:copa-coupon-issued}")
  public void onMessage(@Payload String payload) {
    CouponIssuedEvent event = deserialize(payload);
    if (event == null || event.getCouponId() == null || event.getUserId() == null) {
      log.warn("선착순 발급 이벤트 파싱 실패 또는 필수값 누락 → 스킵: {}", payload);
      return;
    }
    couponService.persistFcfsIssued(event.getCouponId(), event.getUserId());
    log.debug("선착순 발급 DB 반영: couponId={}, userId={}", event.getCouponId(), event.getUserId());
  }

  private CouponIssuedEvent deserialize(String payload) {
    try {
      return objectMapper.readValue(payload, CouponIssuedEvent.class);
    } catch (Exception e) {
      // 파싱 불가 메시지는 무한 재시도(포이즌)를 피하려 삼키고 로깅만 한다.
      log.error("선착순 발급 이벤트 역직렬화 실패: {}", payload, e);
      return null;
    }
  }
}
