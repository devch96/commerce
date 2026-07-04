package com.sparta.copa.copaorder.order.repository;

import com.sparta.copa.copaorder.common.enums.OrderStatus;
import com.sparta.copa.copaorder.order.domain.Order;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

  // 외부 식별자(주문번호) 조회. 클라이언트 API·Saga는 PK 대신 orderNo로 접근한다.
  Optional<Order> findByOrderNo(String orderNo);

  Page<Order> findByUserId(Long userId, Pageable pageable);

  // 상태별 필터(날짜 필터·검색 고도화는 QueryDSL로 추후).
  Page<Order> findByUserIdAndStatus(Long userId, OrderStatus status, Pageable pageable);
}
