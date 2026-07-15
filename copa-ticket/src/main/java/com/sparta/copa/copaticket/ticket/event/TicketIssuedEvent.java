package com.sparta.copa.copaticket.ticket.event;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Redis에서 선착순이 확정된 발권 건. Kafka {@code ticket-issued}로 발행되어 컨슈머가 DB에 반영한다.
 * ticketNo를 발행 시점에 확정해 실어 보내므로, 재배달(at-least-once)에도 같은 번호로 멱등 INSERT 된다.
 */
@Getter
@NoArgsConstructor
public class TicketIssuedEvent {

  private String messageId;   // 메시지 자체의 식별자(UUID)
  private Long eventId;       // 예매 이벤트 PK
  private Long userId;
  private String ticketNo;
  private LocalDateTime issuedAt;

  public TicketIssuedEvent(String messageId, Long eventId, Long userId, String ticketNo,
      LocalDateTime issuedAt) {
    this.messageId = messageId;
    this.eventId = eventId;
    this.userId = userId;
    this.ticketNo = ticketNo;
    this.issuedAt = issuedAt;
  }
}