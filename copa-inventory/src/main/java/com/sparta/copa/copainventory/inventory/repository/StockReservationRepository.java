package com.sparta.copa.copainventory.inventory.repository;

import com.sparta.copa.copainventory.common.enums.ReservationStatus;
import com.sparta.copa.copainventory.inventory.domain.StockReservation;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

  // 멱등성 판단: 같은 주문으로 이미 예약이 있으면 재처리하지 않는다.
  boolean existsByOrderId(Long orderId);

  // TTL 만료 스윕: 결제를 방치한 RESERVED 예약을 찾아 해제한다.
  List<StockReservation> findByStatusAndExpiresAtBefore(
      ReservationStatus status, LocalDateTime threshold);

  /**
   * 확정/해제용 예약 행을 비관적 락으로 잠근다. RESERVED→(CONFIRMED|RELEASED) 전이를 상호 배타로 만들어,
   * 결제 성공 confirm과 TTL 만료 release(또는 다중 인스턴스 스케줄러)가 동시에 같은 예약을 처리하는 경합과
   * 재고 이중 복원을 막는다. 락 획득 후 status=RESERVED가 그대로면 처리, 이미 전이됐으면 빈 목록(멱등 no-op).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from StockReservation r where r.orderId = :orderId and r.status = :status")
  List<StockReservation> findForUpdateByOrderIdAndStatus(
      @Param("orderId") Long orderId, @Param("status") ReservationStatus status);

  // 사용자 취소 복원용: 주문의 모든 예약 행을 잠근다(동시 복원 중복 차단).
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from StockReservation r where r.orderId = :orderId")
  List<StockReservation> findForUpdateByOrderId(@Param("orderId") Long orderId);
}
