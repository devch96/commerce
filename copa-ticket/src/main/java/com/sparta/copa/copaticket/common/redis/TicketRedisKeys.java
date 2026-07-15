package com.sparta.copa.copaticket.common.redis;

/**
 * 예매 Redis 키 규약(단일 정의). 발권 Lua·대기열·스케줄러·보상이 모두 이 키를 공유한다.
 */
public final class TicketRedisKeys {

  private TicketRedisKeys() {
  }

  // 좌석 재고(String 카운터). 존재 자체가 "예매 오픈"의 신호다.
  public static String stockKey(Long eventId) {
    return "ticket:" + eventId + ":stock";
  }

  // 발권자 집합(1인 1매). 재오픈 시에도 보존해 중복 발권을 막는다.
  public static String issuedKey(Long eventId) {
    return "ticket:" + eventId + ":issued";
  }

  // 대기열 ZSET(score=진입 시각 millis).
  public static String queueKey(Long eventId) {
    return "ticket:" + eventId + ":queue";
  }

  // 입장 허가 키(TTL). 인증된 userId로 키가 결정되므로 별도 토큰 값이 필요 없다.
  public static String entryKey(Long eventId, Long userId) {
    return entryKeyPrefix(eventId) + userId;
  }

  public static String entryKeyPrefix(Long eventId) {
    return "ticket:" + eventId + ":entry:";
  }
}