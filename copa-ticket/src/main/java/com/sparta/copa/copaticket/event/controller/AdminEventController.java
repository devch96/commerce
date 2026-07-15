package com.sparta.copa.copaticket.event.controller;

import com.sparta.copa.copaticket.common.exception.BusinessException;
import com.sparta.copa.copaticket.common.exception.ErrorCode;
import com.sparta.copa.copaticket.common.response.ApiResponse;
import com.sparta.copa.copaticket.event.dto.request.EventCreateRequest;
import com.sparta.copa.copaticket.event.dto.response.EventResponse;
import com.sparta.copa.copaticket.event.service.TicketEventService;
import com.sparta.copa.copaticket.ticket.service.TicketIssueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이벤트 관리(ADMIN). 게이트웨이가 주입한 X-User-Role로 ADMIN 권한을 한 번 더 검증한다(방어적 설계).
 */
@RestController
@RequestMapping("/admin/events")
@RequiredArgsConstructor
public class AdminEventController {

  private static final String USER_ROLE_HEADER = "X-User-Role";
  private static final String ADMIN_ROLE = "ADMIN";

  private final TicketEventService eventService;
  private final TicketIssueService ticketIssueService;

  @PostMapping
  public ApiResponse<EventResponse> create(@RequestHeader(USER_ROLE_HEADER) String role,
      @Valid @RequestBody EventCreateRequest request) {
    requireAdmin(role);
    return ApiResponse.success(eventService.create(request));
  }

  // 예매 오픈: 상태 전이 + 잔여 좌석을 Redis에 시드. 응답은 시드된 좌석 수.
  @PostMapping("/{eventId}/open")
  public ApiResponse<Long> open(@RequestHeader(USER_ROLE_HEADER) String role,
      @PathVariable Long eventId) {
    requireAdmin(role);
    return ApiResponse.success(ticketIssueService.open(eventId));
  }

  // 예매 마감: 상태 전이 + Redis 재고·대기열 정리.
  @PostMapping("/{eventId}/close")
  public ApiResponse<Void> close(@RequestHeader(USER_ROLE_HEADER) String role,
      @PathVariable Long eventId) {
    requireAdmin(role);
    ticketIssueService.close(eventId);
    return ApiResponse.success();
  }

  private void requireAdmin(String role) {
    if (!ADMIN_ROLE.equals(role)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
  }
}