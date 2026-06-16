package com.sparta.copa.copaorder.order.service;

import com.sparta.copa.copaorder.common.enums.OrderStatus;
import com.sparta.copa.copaorder.common.exception.BusinessException;
import com.sparta.copa.copaorder.common.exception.ErrorCode;
import com.sparta.copa.copaorder.order.domain.Order;
import com.sparta.copa.copaorder.order.domain.OrderItem;
import com.sparta.copa.copaorder.order.domain.OrderStatusHistory;
import com.sparta.copa.copaorder.order.repository.OrderItemRepository;
import com.sparta.copa.copaorder.order.repository.OrderRepository;
import com.sparta.copa.copaorder.order.repository.OrderStatusHistoryRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 DB 변경(짧은 로컬 트랜잭션)만 담당. Saga 오케스트레이션(외부 호출)은 OrderService가 맡고,
 * 단계별로 이 서비스의 트랜잭션 메서드를 호출해 상태를 영속화한다(자기호출 프록시 우회 방지를 위해 분리).
 */
@Service
@RequiredArgsConstructor
public class OrderCommandService {

  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final OrderStatusHistoryRepository historyRepository;

  // 주문 생성(ORDER_PLACED) + 품목 + 최초 이력. 합계는 품목 스냅샷 단가의 합.
  @Transactional
  public Order createPlacedOrder(Long userId, List<PricedLine> lines, Long couponId) {
    BigDecimal total = lines.stream()
        .map(line -> line.getPrice().multiply(BigDecimal.valueOf(line.getQuantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    Order order = orderRepository.save(Order.place(userId, total, BigDecimal.ZERO, couponId));
    for (PricedLine line : lines) {
      orderItemRepository.save(OrderItem.of(
          order, line.getProductId(), line.getOptionKey(), line.getQuantity(), line.getPrice()));
    }
    historyRepository.save(
        OrderStatusHistory.of(order.getId(), null, OrderStatus.ORDER_PLACED, "주문 생성"));
    return order;
  }

  @Transactional
  public void markPaymentCompleted(Long orderId) {
    Order order = getOrder(orderId);
    OrderStatus from = order.getStatus();
    order.markPaymentCompleted();
    historyRepository.save(
        OrderStatusHistory.of(orderId, from, order.getStatus(), "결제 완료"));
  }

  @Transactional
  public void markCancelled(Long orderId, String reason) {
    Order order = getOrder(orderId);
    OrderStatus from = order.getStatus();
    if (from == OrderStatus.CANCELLED) {
      return; // 멱등
    }
    order.cancel();
    historyRepository.save(OrderStatusHistory.of(orderId, from, OrderStatus.CANCELLED, reason));
  }

  @Transactional
  public void changeShippingStatus(Long orderId, OrderStatus next) {
    Order order = getOrder(orderId);
    OrderStatus from = order.getStatus();
    order.changeShippingStatus(next);
    historyRepository.save(OrderStatusHistory.of(orderId, from, next, "어드민 상태 변경"));
  }

  private Order getOrder(Long orderId) {
    return orderRepository.findById(orderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
  }
}
