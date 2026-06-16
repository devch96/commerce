package com.sparta.copa.copaorder.order.controller;

import com.sparta.copa.copaorder.common.enums.OrderStatus;
import com.sparta.copa.copaorder.common.response.ApiResponse;
import com.sparta.copa.copaorder.order.dto.request.CreateOrderRequest;
import com.sparta.copa.copaorder.order.dto.response.OrderResponse;
import com.sparta.copa.copaorder.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 주문은 로그인 필요(게이트웨이가 X-User-Id 주입).
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

  private static final String USER_ID_HEADER = "X-User-Id";

  private final OrderService orderService;

  // 주문 생성(Saga 시작). 가격 스냅샷→예약→결제→확정/보상.
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<OrderResponse> create(@RequestHeader(USER_ID_HEADER) Long userId,
      @Valid @RequestBody CreateOrderRequest request) {
    return ApiResponse.success(orderService.createOrder(userId, request));
  }

  // 내 주문 목록(상태별 필터 선택).
  @GetMapping
  public ApiResponse<List<OrderResponse>> myOrders(@RequestHeader(USER_ID_HEADER) Long userId,
      @RequestParam(required = false) OrderStatus status) {
    return ApiResponse.success(orderService.getMyOrders(userId, status));
  }

  @GetMapping("/{orderId}")
  public ApiResponse<OrderResponse> getOrder(@RequestHeader(USER_ID_HEADER) Long userId,
      @PathVariable Long orderId) {
    return ApiResponse.success(orderService.getOrder(orderId, userId));
  }

  // 사용자 취소(배송 시작 전). 결제 환불 + 재고 복원.
  @PostMapping("/{orderId}/cancel")
  public ApiResponse<OrderResponse> cancel(@RequestHeader(USER_ID_HEADER) Long userId,
      @PathVariable Long orderId) {
    return ApiResponse.success(orderService.cancelOrder(orderId, userId));
  }
}
