package com.sparta.copa.copaticket.ticket.service;

import com.sparta.copa.copaticket.common.exception.BusinessException;
import com.sparta.copa.copaticket.common.exception.ErrorCode;
import com.sparta.copa.copaticket.event.domain.TicketEvent;
import com.sparta.copa.copaticket.event.repository.TicketEventRepository;
import com.sparta.copa.copaticket.ticket.domain.Ticket;
import com.sparta.copa.copaticket.ticket.dto.response.TicketResponse;
import com.sparta.copa.copaticket.ticket.repository.TicketRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예매 DB 트랜잭션 전담 빈. Redis/Kafka 오케스트레이션(TicketIssueService)과 분리해
 * cross-bean 호출로 @Transactional 프록시가 항상 적용되게 한다.
 */
@Service
@RequiredArgsConstructor
public class TicketService {

  private final TicketRepository ticketRepository;
  private final TicketEventRepository eventRepository;

  /**
   * 선착순 통과분의 멱등 INSERT(컨슈머 경로). Redis가 이미 초과·중복을 막았으므로 여기선 영속화만 한다.
   * 재배달로 인한 정상 중복은 선존재 검사로 no-op, 드문 동시 경합은 (event_id,user_id) 유니크가 흡수한다
   * (같은 트랜잭션 안에서 catch-재시도하지 않는다 — rollback-only 함정, 17주차 5일차 결정).
   */
  @Transactional
  public void persistIssued(Long eventId, Long userId, String ticketNo) {
    if (ticketRepository.existsByEvent_IdAndUserId(eventId, userId)) {
      return;
    }
    TicketEvent event = eventRepository.findById(eventId)
        .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));
    ticketRepository.save(Ticket.issue(ticketNo, event, userId));
  }

  @Transactional(readOnly = true)
  public List<TicketResponse> getMyTickets(Long userId) {
    return ticketRepository.findByUserIdOrderByIdDesc(userId).stream()
        .map(TicketResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public TicketResponse getTicket(String ticketNo, Long userId) {
    return TicketResponse.from(findOwnedTicket(ticketNo, userId));
  }

  /**
   * 사용자 취소(DB 전이). 좌석의 Redis 복원은 호출자(TicketIssueService)가 이 메서드 성공 후 수행한다 —
   * 이미 취소된 건은 여기서 409로 끊겨 복원이 중복 실행(좌석 과복원)되지 않는다.
   *
   * @return 취소된 예매의 이벤트 ID(Redis 좌석 복원용)
   */
  @Transactional
  public Long cancel(String ticketNo, Long userId) {
    Ticket ticket = findOwnedTicket(ticketNo, userId);
    ticket.cancel(LocalDateTime.now());
    return ticket.getEvent().getId();
  }

  // 소유자 검증을 조회에 항상 동반한다(ticketNo 노출만으로 타인 예매 접근 불가 — IDOR 방지).
  private Ticket findOwnedTicket(String ticketNo, Long userId) {
    Ticket ticket = ticketRepository.findByTicketNo(ticketNo)
        .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND));
    if (!ticket.isOwnedBy(userId)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
    return ticket;
  }
}