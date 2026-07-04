package com.sparta.copa.copaorder.order.service;

import com.sparta.copa.copaorder.common.enums.OrderStatus;
import com.sparta.copa.copaorder.common.enums.PgProvider;
import com.sparta.copa.copaorder.common.exception.BusinessException;
import com.sparta.copa.copaorder.common.exception.ErrorCode;
import com.sparta.copa.copaorder.order.domain.Order;
import com.sparta.copa.copaorder.order.domain.OrderItem;
import com.sparta.copa.copaorder.order.domain.OrderStatusHistory;
import com.sparta.copa.copaorder.order.repository.OrderItemRepository;
import com.sparta.copa.copaorder.order.repository.OrderRepository;
import com.sparta.copa.copaorder.order.repository.OrderStatusHistoryRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

  // 주문번호 난수부 문자셋: 혼동 문자(0/O, 1/I)를 뺀 Crockford 유사 32문자.
  private static final char[] ORDER_NO_ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
  private static final int ORDER_NO_RANDOM_LENGTH = 6;
  private static final DateTimeFormatter ORDER_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
  private static final SecureRandom RANDOM = new SecureRandom();

  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final OrderStatusHistoryRepository historyRepository;

  // 주문 생성(PENDING_PAYMENT) + 품목 + 최초 이력. 합계는 품목 스냅샷 단가의 합.
  // 주문번호 충돌은 일 단위 7억+ 조합으로 사실상 없고, DB 유니크가 최종 방어선이다
  // (트랜잭션 내 catch-재시도는 rollback-only 마킹으로 커밋이 깨져 쓰지 않는다).
  @Transactional
  public Order createPlacedOrder(Long userId, List<PricedLine> lines, Long couponId,
      PgProvider pgProvider) {
    BigDecimal total = lines.stream()
        .map(line -> line.getPrice().multiply(BigDecimal.valueOf(line.getQuantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    Order order = orderRepository.save(
        Order.place(generateOrderNo(), userId, total, BigDecimal.ZERO, couponId, pgProvider));

    for (PricedLine line : lines) {
      orderItemRepository.save(OrderItem.of(
          order, line.getProductId(), line.getOptionKey(), line.getQuantity(), line.getPrice()));
    }
    historyRepository.save(
        OrderStatusHistory.of(order.getId(), null, OrderStatus.PENDING_PAYMENT, "주문 생성"));
    return order;
  }

  // 외부 노출용 주문번호: ORD-yyyyMMdd-XXXXXX (난수 6자, DB 유니크가 최종 방어선).
  private String generateOrderNo() {
    StringBuilder sb = new StringBuilder("ORD-")
        .append(LocalDate.now().format(ORDER_NO_DATE))
        .append('-');
    for (int i = 0; i < ORDER_NO_RANDOM_LENGTH; i++) {
      sb.append(ORDER_NO_ALPHABET[RANDOM.nextInt(ORDER_NO_ALPHABET.length)]);
    }
    return sb.toString();
  }

  // 쿠폰 선점으로 확정된 할인액을 주문에 반영(결제 전).
  @Transactional
  public void applyCouponDiscount(Long orderId, BigDecimal discount) {
    getOrder(orderId).applyCouponDiscount(discount);
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
