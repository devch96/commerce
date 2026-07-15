package com.sparta.copa.copaticket.event.service;

import com.sparta.copa.copaticket.common.exception.BusinessException;
import com.sparta.copa.copaticket.common.exception.ErrorCode;
import com.sparta.copa.copaticket.event.domain.TicketEvent;
import com.sparta.copa.copaticket.event.dto.request.EventCreateRequest;
import com.sparta.copa.copaticket.event.dto.response.EventResponse;
import com.sparta.copa.copaticket.event.repository.TicketEventRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이벤트 정의 CRUD와 상태 전이(DB 트랜잭션). Redis 좌석 시드/정리를 포함한 오픈·마감 오케스트레이션은
 * TicketIssueService가 담당하고, 여기의 markOpen/markClosed를 cross-bean으로 호출한다(자기호출 프록시 우회 방지).
 */
@Service
@RequiredArgsConstructor
public class TicketEventService {

  private final TicketEventRepository eventRepository;

  @Transactional
  public EventResponse create(EventCreateRequest request) {
    TicketEvent event = TicketEvent.create(request.getName(), request.getVenue(),
        request.getPrice(), request.getTotalSeats(), request.getOpenAt());
    return EventResponse.from(eventRepository.save(event));
  }

  @Transactional(readOnly = true)
  public List<EventResponse> getEvents() {
    return eventRepository.findAll().stream().map(EventResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public EventResponse getEvent(Long eventId) {
    return EventResponse.from(findEvent(eventId));
  }

  // 오픈 전이 후 좌석 시드에 필요한 정의를 반환한다(총 좌석).
  @Transactional
  public TicketEvent markOpen(Long eventId) {
    TicketEvent event = findEvent(eventId);
    event.open();
    return event;
  }

  @Transactional
  public TicketEvent markClosed(Long eventId) {
    TicketEvent event = findEvent(eventId);
    event.close();
    return event;
  }

  private TicketEvent findEvent(Long eventId) {
    return eventRepository.findById(eventId)
        .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));
  }
}