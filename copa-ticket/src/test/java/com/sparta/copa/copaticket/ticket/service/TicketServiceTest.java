package com.sparta.copa.copaticket.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.copa.copaticket.common.enums.TicketStatus;
import com.sparta.copa.copaticket.common.exception.BusinessException;
import com.sparta.copa.copaticket.common.exception.ErrorCode;
import com.sparta.copa.copaticket.event.domain.TicketEvent;
import com.sparta.copa.copaticket.event.repository.TicketEventRepository;
import com.sparta.copa.copaticket.ticket.domain.Ticket;
import com.sparta.copa.copaticket.ticket.repository.TicketRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

  private static final String TICKET_NO = "TKT-20260713-ABC234";

  @Mock
  private TicketRepository ticketRepository;
  @Mock
  private TicketEventRepository eventRepository;

  private TicketService ticketService;

  @BeforeEach
  void setUp() {
    ticketService = new TicketService(ticketRepository, eventRepository);
  }

  private TicketEvent event() {
    TicketEvent event = TicketEvent.create("콘서트", "올림픽홀", new BigDecimal("99000"), 10, null);
    ReflectionTestUtils.setField(event, "id", 1L);
    return event;
  }

  @Test
  @DisplayName("persistIssued는 선존재 검사로 멱등하다 — 재배달 중복은 no-op")
  void persistIssued_idempotent() {
    given(ticketRepository.existsByEvent_IdAndUserId(1L, 100L)).willReturn(true);

    ticketService.persistIssued(1L, 100L, TICKET_NO);

    verify(ticketRepository, never()).save(any());
  }

  @Test
  @DisplayName("persistIssued는 미존재 시 이벤트에 실려온 ticketNo로 INSERT한다")
  void persistIssued_inserts() {
    given(ticketRepository.existsByEvent_IdAndUserId(1L, 100L)).willReturn(false);
    given(eventRepository.findById(1L)).willReturn(Optional.of(event()));
    given(ticketRepository.save(any(Ticket.class))).willAnswer(inv -> inv.getArgument(0));

    ticketService.persistIssued(1L, 100L, TICKET_NO);

    verify(ticketRepository).save(any(Ticket.class));
  }

  @Test
  @DisplayName("타인의 예매 취소 시도는 ACCESS_DENIED — ticketNo 노출만으로 접근 불가(IDOR 방지)")
  void cancel_notOwner() {
    Ticket ticket = Ticket.issue(TICKET_NO, event(), 100L);
    given(ticketRepository.findByTicketNo(TICKET_NO)).willReturn(Optional.of(ticket));

    assertThatThrownBy(() -> ticketService.cancel(TICKET_NO, 999L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
  }

  @Test
  @DisplayName("이미 취소된 예매의 재취소는 TICKET_NOT_CANCELABLE — 좌석 복원 중복 실행 차단")
  void cancel_alreadyCancelled() {
    Ticket ticket = Ticket.issue(TICKET_NO, event(), 100L);
    ticket.cancel(LocalDateTime.now());
    given(ticketRepository.findByTicketNo(TICKET_NO)).willReturn(Optional.of(ticket));

    assertThatThrownBy(() -> ticketService.cancel(TICKET_NO, 100L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_NOT_CANCELABLE);
  }

  @Test
  @DisplayName("정상 취소는 CANCELLED 전이 후 Redis 복원용 이벤트 ID를 반환한다")
  void cancel_success() {
    Ticket ticket = Ticket.issue(TICKET_NO, event(), 100L);
    given(ticketRepository.findByTicketNo(TICKET_NO)).willReturn(Optional.of(ticket));

    Long eventId = ticketService.cancel(TICKET_NO, 100L);

    assertThat(eventId).isEqualTo(1L);
    assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
    assertThat(ticket.getCancelledAt()).isNotNull();
  }
}