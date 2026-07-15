package com.sparta.copa.copaticket.ticket.controller;

import com.sparta.copa.copaticket.common.response.ApiResponse;
import com.sparta.copa.copaticket.ticket.dto.response.TicketIssueResponse;
import com.sparta.copa.copaticket.ticket.dto.response.TicketResponse;
import com.sparta.copa.copaticket.ticket.service.TicketIssueService;
import com.sparta.copa.copaticket.ticket.service.TicketService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 발권(선착순)·예매 조회·취소. 인증은 게이트웨이가 처리하고 X-User-Id를 주입한다.
 */
@RestController
@RequiredArgsConstructor
public class TicketController {

  private static final String USER_ID_HEADER = "X-User-Id";

  private final TicketIssueService ticketIssueService;
  private final TicketService ticketService;

  // 선착순 발권: 대기열 입장 허가(entry TTL) 상태에서만 성공한다. DB 반영은 Kafka로 비동기.
  @PostMapping("/events/{eventId}/tickets")
  public ApiResponse<TicketIssueResponse> issue(@RequestHeader(USER_ID_HEADER) Long userId,
      @PathVariable Long eventId) {
    return ApiResponse.success(ticketIssueService.issue(eventId, userId));
  }

  @GetMapping("/tickets/my")
  public ApiResponse<List<TicketResponse>> myTickets(@RequestHeader(USER_ID_HEADER) Long userId) {
    return ApiResponse.success(ticketService.getMyTickets(userId));
  }

  @GetMapping("/tickets/{ticketNo}")
  public ApiResponse<TicketResponse> get(@RequestHeader(USER_ID_HEADER) Long userId,
      @PathVariable String ticketNo) {
    return ApiResponse.success(ticketService.getTicket(ticketNo, userId));
  }

  // 사용자 취소: DB 전이 후 좌석을 Redis 풀로 복원(재판매 가능).
  @DeleteMapping("/tickets/{ticketNo}")
  public ApiResponse<Void> cancel(@RequestHeader(USER_ID_HEADER) Long userId,
      @PathVariable String ticketNo) {
    ticketIssueService.cancel(ticketNo, userId);
    return ApiResponse.success();
  }
}