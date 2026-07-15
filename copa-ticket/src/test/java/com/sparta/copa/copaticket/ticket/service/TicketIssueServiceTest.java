package com.sparta.copa.copaticket.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.copa.copaticket.common.exception.BusinessException;
import com.sparta.copa.copaticket.common.exception.ErrorCode;
import com.sparta.copa.copaticket.event.domain.TicketEvent;
import com.sparta.copa.copaticket.event.service.TicketEventService;
import com.sparta.copa.copaticket.ticket.dto.response.TicketIssueResponse;
import com.sparta.copa.copaticket.ticket.support.TicketNoGenerator;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TicketIssueServiceTest {

  @Mock
  private StringRedisTemplate redisTemplate;
  @Mock
  private RedisScript<Long> ticketIssueScript;
  @Mock
  private KafkaTemplate<String, String> kafkaTemplate;
  @Mock
  private TicketEventService eventService;
  @Mock
  private TicketService ticketService;
  @Mock
  private TicketNoGenerator ticketNoGenerator;

  // 이벤트의 LocalDateTime 직렬화를 위해 JavaTime 모듈을 등록한다(운영은 Spring Boot가 자동 등록).
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  private TicketIssueService service() {
    TicketIssueService service = new TicketIssueService(redisTemplate, ticketIssueScript,
        kafkaTemplate, objectMapper, eventService, ticketService, ticketNoGenerator);
    // @Value 필드는 순수 단위 테스트에서 주입되지 않으므로 기본값을 직접 넣는다.
    ReflectionTestUtils.setField(service, "entryTtlSeconds", 300L);
    return service;
  }

  @Test
  @DisplayName("Lua가 성공(1)을 반환하면 ticket-issued로 발행하고 접수 응답을 반환한다")
  void issue_success_publishes() {
    TicketIssueService issue = service();
    given(redisTemplate.execute(eq(ticketIssueScript), anyList(), any())).willReturn(1L);
    given(ticketNoGenerator.generate()).willReturn("TKT-20260713-ABC234");
    given(kafkaTemplate.send(anyString(), anyString(), anyString()))
        .willReturn(CompletableFuture.completedFuture(null));
    ValueOperations<String, String> valueOps = mockValueOps();
    given(valueOps.get("ticket:1:stock")).willReturn("999");

    TicketIssueResponse response = issue.issue(1L, 100L);

    assertThat(response.getEventId()).isEqualTo(1L);
    assertThat(response.getTicketNo()).isEqualTo("TKT-20260713-ABC234");
    assertThat(response.getStatus()).isEqualTo("ACCEPTED");
    assertThat(response.getRemainingSeats()).isEqualTo(999);
    verify(kafkaTemplate).send(eq("ticket-issued"), eq("1"), anyString());
  }

  @Test
  @DisplayName("미오픈(-3)이면 발행하지 않고 EVENT_NOT_OPEN")
  void issue_notOpen() {
    TicketIssueService issue = service();
    given(redisTemplate.execute(eq(ticketIssueScript), anyList(), any())).willReturn(-3L);

    assertThatThrownBy(() -> issue.issue(1L, 100L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EVENT_NOT_OPEN);
    verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("중복 발권(-2)이면 TICKET_ALREADY_ISSUED")
  void issue_alreadyIssued() {
    TicketIssueService issue = service();
    given(redisTemplate.execute(eq(ticketIssueScript), anyList(), any())).willReturn(-2L);

    assertThatThrownBy(() -> issue.issue(1L, 100L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_ALREADY_ISSUED);
  }

  @Test
  @DisplayName("입장 미허가(-4)면 TICKET_NOT_ADMITTED — 대기열 우회 발권 차단")
  void issue_notAdmitted() {
    TicketIssueService issue = service();
    given(redisTemplate.execute(eq(ticketIssueScript), anyList(), any())).willReturn(-4L);

    assertThatThrownBy(() -> issue.issue(1L, 100L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_NOT_ADMITTED);
    verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("매진(-1)이면 TICKET_SOLD_OUT")
  void issue_soldOut() {
    TicketIssueService issue = service();
    given(redisTemplate.execute(eq(ticketIssueScript), anyList(), any())).willReturn(-1L);

    assertThatThrownBy(() -> issue.issue(1L, 100L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_SOLD_OUT);
  }

  @Test
  @DisplayName("발행 실패 시 Redis 보상(SREM+INCR+입장권 재부여)하고 TICKET_PUBLISH_FAILED")
  void issue_publishFail_compensates() {
    TicketIssueService issue = service();
    given(redisTemplate.execute(eq(ticketIssueScript), anyList(), any())).willReturn(1L);
    given(ticketNoGenerator.generate()).willReturn("TKT-20260713-ABC234");
    given(kafkaTemplate.send(anyString(), anyString(), anyString()))
        .willReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
    ValueOperations<String, String> valueOps = mockValueOps();
    SetOperations<String, String> setOps = mockSetOps();

    assertThatThrownBy(() -> issue.issue(1L, 100L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_PUBLISH_FAILED);

    // 보상: 발권자 집합 제거 + 좌석 원복 + 입장권 재부여(대기열 재진입 없이 재시도 가능).
    verify(setOps).remove("ticket:1:issued", "100");
    verify(valueOps).increment("ticket:1:stock");
    verify(valueOps).set("ticket:1:entry:100", "1", Duration.ofSeconds(300));
  }

  @Test
  @DisplayName("오픈 시 잔여 좌석(총 좌석 - 발권자 수)을 Redis에 시드한다 — 재오픈 초과 시드 방지")
  void open_seedsRemainingSeats() {
    TicketIssueService issue = service();
    TicketEvent event = TicketEvent.create("콘서트", "올림픽홀", new BigDecimal("99000"), 10, null);
    given(eventService.markOpen(1L)).willReturn(event);
    SetOperations<String, String> setOps = mockSetOps();
    given(setOps.size("ticket:1:issued")).willReturn(2L);
    ValueOperations<String, String> valueOps = mockValueOps();

    long seeded = issue.open(1L);

    assertThat(seeded).isEqualTo(8);
    verify(valueOps).set("ticket:1:stock", "8");
  }

  @Test
  @DisplayName("취소 시 DB 전이 성공 후 발권자 집합 제거 + 오픈 중이면 좌석을 풀로 복원한다")
  void cancel_restoresSeat() {
    TicketIssueService issue = service();
    given(ticketService.cancel("TKT-20260713-ABC234", 100L)).willReturn(1L);
    SetOperations<String, String> setOps = mockSetOps();
    given(redisTemplate.hasKey("ticket:1:stock")).willReturn(true);
    ValueOperations<String, String> valueOps = mockValueOps();

    issue.cancel("TKT-20260713-ABC234", 100L);

    verify(setOps).remove("ticket:1:issued", "100");
    verify(valueOps).increment("ticket:1:stock");
  }

  private ValueOperations<String, String> mockValueOps() {
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    given(redisTemplate.opsForValue()).willReturn(valueOps);
    return valueOps;
  }

  private SetOperations<String, String> mockSetOps() {
    @SuppressWarnings("unchecked")
    SetOperations<String, String> setOps = mock(SetOperations.class);
    given(redisTemplate.opsForSet()).willReturn(setOps);
    return setOps;
  }
}