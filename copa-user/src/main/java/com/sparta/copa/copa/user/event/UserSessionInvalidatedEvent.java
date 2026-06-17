package com.sparta.copa.copa.user.event;

import lombok.Getter;

/**
 * 비밀번호 변경·탈퇴 등 자격 변화로 기존 세션(Refresh Token)을 무효화해야 할 때 발행한다.
 * 토큰 저장소는 auth 패키지 소관이므로, user는 이벤트만 발행하고 무효화는 auth의 리스너가 수행한다
 * (auth→user 의존 방향 유지). 커밋 이후에만 무효화되도록 트랜잭션 이벤트로 소비한다.
 */
@Getter
public class UserSessionInvalidatedEvent {

  private final Long userId;
  private final String reason;

  public UserSessionInvalidatedEvent(Long userId, String reason) {
    this.userId = userId;
    this.reason = reason;
  }
}
