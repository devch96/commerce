package com.sparta.copa.copainventory.inventory.scheduler;

import com.sparta.copa.copainventory.common.enums.ReservationStatus;
import com.sparta.copa.copainventory.inventory.domain.StockReservation;
import com.sparta.copa.copainventory.inventory.repository.StockReservationRepository;
import com.sparta.copa.copainventory.inventory.service.InventoryService;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * TTL 만료 자동 해제. 결제를 방치한 RESERVED 예약을 주기적으로 찾아 해제(release)한다.
 * 결제 결과 이벤트가 유실되어도 재고가 영원히 묶이지 않도록 하는 안전망(Kafka 연동은 11주차).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpiryScheduler {

  private final StockReservationRepository reservationRepository;
  private final InventoryService inventoryService;

  @Scheduled(fixedDelayString = "${inventory.reservation.sweep-interval-ms:60000}")
  public void releaseExpiredReservations() {
    Set<String> expiredOrderIds = new LinkedHashSet<>();
    for (StockReservation reservation : reservationRepository
        .findByStatusAndExpiresAtBefore(ReservationStatus.RESERVED, LocalDateTime.now())) {
      expiredOrderIds.add(reservation.getOrderId());
    }
    if (expiredOrderIds.isEmpty()) {
      return;
    }
    log.info("TTL 만료 예약 해제 대상 주문 {}건", expiredOrderIds.size());
    // 주문 단위로 각자 트랜잭션에서 해제(한 건 실패가 나머지를 막지 않게).
    for (String orderId : expiredOrderIds) {
      try {
        inventoryService.release(orderId);
      } catch (RuntimeException e) {
        log.warn("TTL 만료 예약 해제 실패, 다음 스윕에서 재시도: orderId={}", orderId, e);
      }
    }
  }
}
