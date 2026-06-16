package com.sparta.copa.copaorder.order.dto.response;

import com.sparta.copa.copaorder.common.enums.OrderStatus;
import com.sparta.copa.copaorder.order.domain.Order;
import com.sparta.copa.copaorder.order.domain.OrderItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderResponse {

  private final Long id;
  private final Long userId;
  private final OrderStatus status;
  private final BigDecimal totalAmount;
  private final BigDecimal discountAmount;
  private final BigDecimal payableAmount;
  private final BigDecimal refundedAmount;
  private final Long couponId;
  private final LocalDateTime createdAt;
  private final List<OrderItemResponse> items;

  public static OrderResponse from(Order order, List<OrderItem> items) {
    return OrderResponse.builder()
        .id(order.getId())
        .userId(order.getUserId())
        .status(order.getStatus())
        .totalAmount(order.getTotalAmount())
        .discountAmount(order.getDiscountAmount())
        .payableAmount(order.payableAmount())
        .refundedAmount(order.getRefundedAmount())
        .couponId(order.getCouponId())
        .createdAt(order.getCreatedAt())
        .items(items.stream().map(OrderItemResponse::from).toList())
        .build();
  }
}
