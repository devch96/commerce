package com.sparta.copa.copaticket.ticket.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.copa.copaticket.ticket.event.TicketIssuedEvent;
import com.sparta.copa.copaticket.ticket.service.TicketIssueService;
import com.sparta.copa.copaticket.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * {@code ticket-issued} 구독 → DB에 Ticket 적재. Redis에서 이미 선착순이 확정된 건이므로 여기선 영속화만 한다.
 * 발행은 at-least-once이므로 소비는 멱등이어야 한다((event_id,user_id) 유니크 + 선존재 검사).
 *
 * <p>테스트·브로커 부재 환경에서는 {@code copa.ticket.consumer.enabled=false}로 비활성화한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "copa.ticket.consumer.enabled", havingValue = "true",
    matchIfMissing = true)
public class TicketIssuedConsumer {

  private final ObjectMapper objectMapper;
  private final TicketService ticketService;

  @KafkaListener(
      topics = TicketIssueService.TOPIC_TICKET_ISSUED,
      groupId = "${copa.ticket.consumer.group-id:copa-ticket-issued}")
  public void onMessage(@Payload String payload) {
    TicketIssuedEvent event = deserialize(payload);
    if (event == null || event.getEventId() == null || event.getUserId() == null
        || event.getTicketNo() == null) {
      log.warn("발권 이벤트 파싱 실패 또는 필수값 누락 → 스킵: {}", payload);
      return;
    }
    ticketService.persistIssued(event.getEventId(), event.getUserId(), event.getTicketNo());
    log.debug("발권 DB 반영: eventId={}, userId={}, ticketNo={}",
        event.getEventId(), event.getUserId(), event.getTicketNo());
  }

  private TicketIssuedEvent deserialize(String payload) {
    try {
      return objectMapper.readValue(payload, TicketIssuedEvent.class);
    } catch (Exception e) {
      // 파싱 불가 메시지는 무한 재시도(포이즌)를 피하려 삼키고 로깅만 한다.
      log.error("발권 이벤트 역직렬화 실패: {}", payload, e);
      return null;
    }
  }
}