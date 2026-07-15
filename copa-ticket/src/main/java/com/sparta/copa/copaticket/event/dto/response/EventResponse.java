package com.sparta.copa.copaticket.event.dto.response;

import com.sparta.copa.copaticket.common.enums.EventStatus;
import com.sparta.copa.copaticket.event.domain.TicketEvent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class EventResponse {

  private final Long id;
  private final String name;
  private final String venue;
  private final BigDecimal price;
  private final int totalSeats;
  private final EventStatus status;
  private final LocalDateTime openAt;

  private EventResponse(Long id, String name, String venue, BigDecimal price, int totalSeats,
      EventStatus status, LocalDateTime openAt) {
    this.id = id;
    this.name = name;
    this.venue = venue;
    this.price = price;
    this.totalSeats = totalSeats;
    this.status = status;
    this.openAt = openAt;
  }

  public static EventResponse from(TicketEvent event) {
    return new EventResponse(event.getId(), event.getName(), event.getVenue(), event.getPrice(),
        event.getTotalSeats(), event.getStatus(), event.getOpenAt());
  }
}