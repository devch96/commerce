package com.sparta.copa.copaticket.queue.controller;

import com.sparta.copa.copaticket.common.response.ApiResponse;
import com.sparta.copa.copaticket.queue.dto.QueueStatusResponse;
import com.sparta.copa.copaticket.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대기열 진입·상태 폴링. 인증은 게이트웨이가 처리하고 X-User-Id를 주입한다.
 */
@RestController
@RequestMapping("/events/{eventId}/queue")
@RequiredArgsConstructor
public class QueueController {

  private static final String USER_ID_HEADER = "X-User-Id";

  private final QueueService queueService;

  @PostMapping
  public ApiResponse<QueueStatusResponse> enter(@RequestHeader(USER_ID_HEADER) Long userId,
      @PathVariable Long eventId) {
    return ApiResponse.success(queueService.enter(eventId, userId));
  }

  @GetMapping("/status")
  public ApiResponse<QueueStatusResponse> status(@RequestHeader(USER_ID_HEADER) Long userId,
      @PathVariable Long eventId) {
    return ApiResponse.success(queueService.status(eventId, userId));
  }
}