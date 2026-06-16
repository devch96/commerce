package com.sparta.copa.copaorder.order.repository;

import com.sparta.copa.copaorder.order.domain.OrderStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

  List<OrderStatusHistory> findByOrderIdOrderByChangedAtAsc(Long orderId);
}
