package com.sparta.copa.copa.auth.event;

import com.sparta.copa.copa.auth.service.TokenService;
import com.sparta.copa.copa.user.event.UserSessionInvalidatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 자격 변화(비밀번호 변경·탈퇴) 커밋 이후 해당 사용자의 Refresh Token을 폐기한다.
 * 변경이 실제로 영속화된 뒤에만(AFTER_COMMIT) 무효화하여, 롤백 시 세션이 잘못 끊기는 것을 막는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionInvalidationListener {

  private final TokenService tokenService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onSessionInvalidated(UserSessionInvalidatedEvent event) {
    tokenService.delete(event.getUserId());
    log.info("세션 무효화: userId={}, reason={}", event.getUserId(), event.getReason());
  }
}
