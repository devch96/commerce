package com.sparta.copa.copaticket.event.controller;

import com.sparta.copa.copaticket.common.response.ApiResponse;
import com.sparta.copa.copaticket.event.dto.response.EventResponse;
import com.sparta.copa.copaticket.event.service.TicketEventService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이벤트 조회(비로그인 공개 — 게이트웨이 화이트리스트 GET /events, GET /events/*).
 * 대기열·발권 등 하위 경로는 인증이 필요하다.
 */
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

  private final TicketEventService eventService;

  @GetMapping
  public ApiResponse<List<EventResponse>> list() {
    return ApiResponse.success(eventService.getEvents());
  }

  @GetMapping("/{eventId}")
  public ApiResponse<EventResponse> get(@PathVariable Long eventId) {
    return ApiResponse.success(eventService.getEvent(eventId));
  }
}