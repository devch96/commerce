package com.sparta.copa.copaticket.ticket.dto.response;

import lombok.Getter;

/**
 * 발권 접수 응답. Redis에서 선착순은 확정됐지만 DB 반영은 비동기(Kafka)이므로
 * 상태를 ACCEPTED로 명시한다. 확정 내역 확인은 GET /tickets/my.
 */
@Getter
public class TicketIssueResponse {

  private final Long eventId;
  private final Long userId;
  private final String ticketNo;
  private final String status;
  private final long remainingSeats;

  private TicketIssueResponse(Long eventId, Long userId, String ticketNo, String status,
      long remainingSeats) {
    this.eventId = eventId;
    this.userId = userId;
    this.ticketNo = ticketNo;
    this.status = status;
    this.remainingSeats = remainingSeats;
  }

  public static TicketIssueResponse accepted(Long eventId, Long userId, String ticketNo,
      long remainingSeats) {
    return new TicketIssueResponse(eventId, userId, ticketNo, "ACCEPTED", remainingSeats);
  }
}