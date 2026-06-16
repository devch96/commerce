package com.sparta.copa.copaorder.order.repository;

import com.sparta.copa.copaorder.common.enums.OrderStatus;
import com.sparta.copa.copaorder.order.domain.Order;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

  List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

  // 상태별 필터(날짜 필터·페이징 고도화는 QueryDSL로 추후).
  List<Order> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, OrderStatus status);
}
