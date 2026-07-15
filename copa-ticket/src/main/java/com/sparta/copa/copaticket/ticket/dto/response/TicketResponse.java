package com.sparta.copa.copaticket.ticket.dto.response;

import com.sparta.copa.copaticket.common.enums.TicketStatus;
import com.sparta.copa.copaticket.ticket.domain.Ticket;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class TicketResponse {

  private final String ticketNo;
  private final Long eventId;
  private final String eventName;
  private final String venue;
  private final BigDecimal price;
  private final TicketStatus status;
  private final LocalDateTime issuedAt;
  private final LocalDateTime cancelledAt;

  private TicketResponse(String ticketNo, Long eventId, String eventName, String venue,
      BigDecimal price, TicketStatus status, LocalDateTime issuedAt, LocalDateTime cancelledAt) {
    this.ticketNo = ticketNo;
    this.eventId = eventId;
    this.eventName = eventName;
    this.venue = venue;
    this.price = price;
    this.status = status;
    this.issuedAt = issuedAt;
    this.cancelledAt = cancelledAt;
  }

  // 트랜잭션 안에서 호출해야 한다(event LAZY 로딩).
  public static TicketResponse from(Ticket ticket) {
    return new TicketResponse(ticket.getTicketNo(), ticket.getEvent().getId(),
        ticket.getEvent().getName(), ticket.getEvent().getVenue(), ticket.getEvent().getPrice(),
        ticket.getStatus(), ticket.getIssuedAt(), ticket.getCancelledAt());
  }
}