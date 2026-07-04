package com.sparta.copa.copaorder.order.controller;

import com.sparta.copa.copaorder.common.exception.BusinessException;
import com.sparta.copa.copaorder.common.exception.ErrorCode;
import com.sparta.copa.copaorder.common.response.ApiResponse;
import com.sparta.copa.copaorder.order.dto.request.AdminStatusRequest;
import com.sparta.copa.copaorder.order.dto.response.OrderResponse;
import com.sparta.copa.copaorder.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 어드민 주문 상태 변경. 게이트웨이가 주입한 X-User-Role로 ADMIN을 방어적으로 재검증.
@RestController
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

  private static final String USER_ROLE_HEADER = "X-User-Role";
  private static final String ADMIN_ROLE = "ADMIN";

  private final OrderService orderService;

  @PatchMapping("/{orderNo}/status")
  public ApiResponse<OrderResponse> changeStatus(
      @RequestHeader(value = USER_ROLE_HEADER, required = false) String role,
      @PathVariable String orderNo,
      @Valid @RequestBody AdminStatusRequest request) {
    if (!ADMIN_ROLE.equals(role)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
    return ApiResponse.success(orderService.changeStatus(orderNo, request.getStatus()));
  }
}
