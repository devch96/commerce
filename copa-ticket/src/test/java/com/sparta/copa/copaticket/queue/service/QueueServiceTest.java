package com.sparta.copa.copaticket.queue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sparta.copa.copaticket.common.enums.QueueStatus;
import com.sparta.copa.copaticket.common.exception.BusinessException;
import com.sparta.copa.copaticket.common.exception.ErrorCode;
import com.sparta.copa.copaticket.queue.dto.QueueStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

  private static final String STOCK_KEY = "ticket:1:stock";
  private static final String ISSUED_KEY = "ticket:1:issued";
  private static final String QUEUE_KEY = "ticket:1:queue";
  private static final String ENTRY_KEY = "ticket:1:entry:100";

  @Mock
  private StringRedisTemplate redisTemplate;

  private QueueService queueService;

  @BeforeEach
  void setUp() {
    // 입장 배치 100명 / 1초 — 예상 대기·폴링 간격 계산의 기준.
    queueService = new QueueService(redisTemplate, 100, 1000);
  }

  @Test
  @DisplayName("미오픈 이벤트(재고 키 없음)의 대기열 진입은 EVENT_NOT_OPEN")
  void enter_notOpen() {
    given(redisTemplate.hasKey(STOCK_KEY)).willReturn(false);

    assertThatThrownBy(() -> queueService.enter(1L, 100L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EVENT_NOT_OPEN);
  }

  @Test
  @DisplayName("이미 발권한 사용자의 진입은 TICKET_ALREADY_ISSUED")
  void enter_alreadyIssued() {
    given(redisTemplate.hasKey(STOCK_KEY)).willReturn(true);
    SetOperations<String, String> setOps = mockSetOps();
    given(setOps.isMember(ISSUED_KEY, "100")).willReturn(true);

    assertThatThrownBy(() -> queueService.enter(1L, 100L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_ALREADY_ISSUED);
  }

  @Test
  @DisplayName("이미 입장 허가된 사용자의 재진입은 ADMITTED를 그대로 반환한다(멱등)")
  void enter_alreadyAdmitted() {
    given(redisTemplate.hasKey(STOCK_KEY)).willReturn(true);
    SetOperations<String, String> setOps = mockSetOps();
    given(setOps.isMember(ISSUED_KEY, "100")).willReturn(false);
    given(redisTemplate.hasKey(ENTRY_KEY)).willReturn(true);

    QueueStatusResponse response = queueService.enter(1L, 100L);

    assertThat(response.getStatus()).isEqualTo(QueueStatus.ADMITTED);
  }

  @Test
  @DisplayName("정상 진입은 ZADD NX 후 순번(rank+1)과 대기 총원을 반환한다")
  void enter_waiting() {
    given(redisTemplate.hasKey(STOCK_KEY)).willReturn(true);
    SetOperations<String, String> setOps = mockSetOps();
    given(setOps.isMember(ISSUED_KEY, "100")).willReturn(false);
    given(redisTemplate.hasKey(ENTRY_KEY)).willReturn(false);
    ZSetOperations<String, String> zSetOps = mockZSetOps();
    given(zSetOps.rank(QUEUE_KEY, "100")).willReturn(41L);
    given(zSetOps.zCard(QUEUE_KEY)).willReturn(1000L);

    QueueStatusResponse response = queueService.enter(1L, 100L);

    assertThat(response.getStatus()).isEqualTo(QueueStatus.WAITING);
    assertThat(response.getPosition()).isEqualTo(42);
    assertThat(response.getAhead()).isEqualTo(41);
    assertThat(response.getWaitingTotal()).isEqualTo(1000);
    // 42번째는 첫 배치(100명/1초)에 입장 → 예상 1초, 폴링 간격은 하한 1초로 클램프.
    assertThat(response.getEstimatedWaitSeconds()).isEqualTo(1);
    assertThat(response.getPollAfterMs()).isEqualTo(1000);
    // NX: 재요청해도 기존 score(순번)를 보존한다.
    verify(zSetOps).addIfAbsent(eq(QUEUE_KEY), eq("100"), anyDouble());
  }

  @Test
  @DisplayName("먼 순번일수록 예상 대기가 길고 서버 지시 폴링 간격도 길어진다(예상의 1/10, 최대 10s)")
  void status_farPosition_slowsPolling() {
    given(redisTemplate.hasKey(ENTRY_KEY)).willReturn(false);
    ZSetOperations<String, String> zSetOps = mockZSetOps();
    given(zSetOps.rank(QUEUE_KEY, "100")).willReturn(4999L);
    given(zSetOps.zCard(QUEUE_KEY)).willReturn(20000L);

    QueueStatusResponse response = queueService.status(1L, 100L);

    // 5000번째 = 50배치 × 1초 = 예상 50초 → 폴링 간격 5초.
    assertThat(response.getEstimatedWaitSeconds()).isEqualTo(50);
    assertThat(response.getPollAfterMs()).isEqualTo(5000);
  }

  @Test
  @DisplayName("상태 조회: 입장 허가 키가 있으면 ADMITTED")
  void status_admitted() {
    given(redisTemplate.hasKey(ENTRY_KEY)).willReturn(true);

    QueueStatusResponse response = queueService.status(1L, 100L);

    assertThat(response.getStatus()).isEqualTo(QueueStatus.ADMITTED);
  }

  @Test
  @DisplayName("상태 조회: 대기 중이면 WAITING과 순번")
  void status_waiting() {
    given(redisTemplate.hasKey(ENTRY_KEY)).willReturn(false);
    ZSetOperations<String, String> zSetOps = mockZSetOps();
    given(zSetOps.rank(QUEUE_KEY, "100")).willReturn(0L);
    given(zSetOps.zCard(QUEUE_KEY)).willReturn(10L);

    QueueStatusResponse response = queueService.status(1L, 100L);

    assertThat(response.getStatus()).isEqualTo(QueueStatus.WAITING);
    assertThat(response.getPosition()).isEqualTo(1);
  }

  @Test
  @DisplayName("상태 조회: 대기열에 없고 발권자 집합에 있으면 ISSUED")
  void status_issued() {
    given(redisTemplate.hasKey(ENTRY_KEY)).willReturn(false);
    ZSetOperations<String, String> zSetOps = mockZSetOps();
    given(zSetOps.rank(QUEUE_KEY, "100")).willReturn(null);
    SetOperations<String, String> setOps = mockSetOps();
    given(setOps.isMember(ISSUED_KEY, "100")).willReturn(true);

    QueueStatusResponse response = queueService.status(1L, 100L);

    assertThat(response.getStatus()).isEqualTo(QueueStatus.ISSUED);
  }

  @Test
  @DisplayName("상태 조회: 어디에도 없으면 NOT_IN_QUEUE")
  void status_notInQueue() {
    given(redisTemplate.hasKey(ENTRY_KEY)).willReturn(false);
    ZSetOperations<String, String> zSetOps = mockZSetOps();
    given(zSetOps.rank(QUEUE_KEY, "100")).willReturn(null);
    SetOperations<String, String> setOps = mockSetOps();
    given(setOps.isMember(ISSUED_KEY, "100")).willReturn(false);

    QueueStatusResponse response = queueService.status(1L, 100L);

    assertThat(response.getStatus()).isEqualTo(QueueStatus.NOT_IN_QUEUE);
  }

  private SetOperations<String, String> mockSetOps() {
    @SuppressWarnings("unchecked")
    SetOperations<String, String> setOps = mock(SetOperations.class);
    given(redisTemplate.opsForSet()).willReturn(setOps);
    return setOps;
  }

  private ZSetOperations<String, String> mockZSetOps() {
    @SuppressWarnings("unchecked")
    ZSetOperations<String, String> zSetOps = mock(ZSetOperations.class);
    given(redisTemplate.opsForZSet()).willReturn(zSetOps);
    return zSetOps;
  }
}