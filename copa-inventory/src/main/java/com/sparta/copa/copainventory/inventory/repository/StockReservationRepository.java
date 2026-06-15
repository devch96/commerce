package com.sparta.copa.copainventory.inventory.repository;

import com.sparta.copa.copainventory.common.enums.ReservationStatus;
import com.sparta.copa.copainventory.inventory.domain.StockReservation;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

  // 멱등성 판단: 같은 주문으로 이미 예약이 있으면 재처리하지 않는다.
  boolean existsByOrderId(Long orderId);

  List<StockReservation> findByOrderId(Long orderId);

  List<StockReservation> findByOrderIdAndStatus(Long orderId, ReservationStatus status);

  // TTL 만료 스윕: 결제를 방치한 RESERVED 예약을 찾아 해제한다.
  List<StockReservation> findByStatusAndExpiresAtBefore(
      ReservationStatus status, LocalDateTime threshold);
}
