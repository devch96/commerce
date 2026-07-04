package com.sparta.copa.copaorder.order.service;

import com.sparta.copa.copaorder.common.enums.OrderStatus;
import com.sparta.copa.copaorder.common.exception.BusinessException;
import com.sparta.copa.copaorder.common.exception.ErrorCode;
import com.sparta.copa.copaorder.order.domain.Order;
import com.sparta.copa.copaorder.order.dto.response.OrderResponse;
import com.sparta.copa.copaorder.order.repository.OrderItemRepository;
import com.sparta.copa.copaorder.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

  // 소유자 검증 포함(사용자 대면 조회). 외부 식별자(orderNo)로 접근한다.
  @Transactional(readOnly = true)
  public OrderResponse getOwnedOrder(String orderNo, Long userId) {
    Order order = getOrder(orderNo);
    if (!order.isOwnedBy(userId)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
    return toResponse(order);
  }

  // 소유자 검증 없음(어드민/내부 응답 조립).
  @Transactional(readOnly = true)
  public OrderResponse getOrderResponse(String orderNo) {
    return toResponse(getOrder(orderNo));
  }

  // 내 주문 목록(페이징). 정렬 기본값은 컨트롤러 @PageableDefault(createdAt desc)가 정한다.
  @Transactional(readOnly = true)
  public Page<OrderResponse> getMyOrders(Long userId, OrderStatus status, Pageable pageable) {
    Page<Order> orders = (status == null)
        ? orderRepository.findByUserId(userId, pageable)
        : orderRepository.findByUserIdAndStatus(userId, status, pageable);
    return orders.map(this::toResponse);
  }

  private OrderResponse toResponse(Order order) {
    return OrderResponse.from(order, orderItemRepository.findByOrder_Id(order.getId()));
  }

  private Order getOrder(String orderNo) {
    return orderRepository.findByOrderNo(orderNo)
        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
  }
}
