package com.sparta.copa.copaorder.order.service;

import com.sparta.copa.copaorder.common.enums.OrderStatus;
import com.sparta.copa.copaorder.common.exception.BusinessException;
import com.sparta.copa.copaorder.common.exception.ErrorCode;
import com.sparta.copa.copaorder.order.domain.Order;
import com.sparta.copa.copaorder.order.dto.response.OrderResponse;
import com.sparta.copa.copaorder.order.repository.OrderItemRepository;
import com.sparta.copa.copaorder.order.repository.OrderRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 조회(읽기 전용). 오케스트레이터(OrderService)와 분리해, 응답 조립을 호출할 때
 * 프록시를 거쳐 실제로 읽기 트랜잭션이 적용되도록 한다(self-invocation 회피).
 */
@Service
@RequiredArgsConstructor
public class OrderQueryService {

  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;

  // 소유자 검증 포함(사용자 대면 조회).
  @Transactional(readOnly = true)
  public OrderResponse getOwnedOrder(Long orderId, Long userId) {
    Order order = getOrder(orderId);
    if (!order.isOwnedBy(userId)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
    return toResponse(order);
  }

  // 소유자 검증 없음(어드민/내부 응답 조립).
  @Transactional(readOnly = true)
  public OrderResponse getOrderResponse(Long orderId) {
    return toResponse(getOrder(orderId));
  }

  @Transactional(readOnly = true)
  public List<OrderResponse> getMyOrders(Long userId, OrderStatus status) {
    List<Order> orders = (status == null)
        ? orderRepository.findByUserIdOrderByCreatedAtDesc(userId)
        : orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status);
    List<OrderResponse> responses = new ArrayList<>();
    for (Order order : orders) {
      responses.add(toResponse(order));
    }
    return responses;
  }

  private OrderResponse toResponse(Order order) {
    return OrderResponse.from(order, orderItemRepository.findByOrder_Id(order.getId()));
  }

  private Order getOrder(Long orderId) {
    return orderRepository.findById(orderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
  }
}
