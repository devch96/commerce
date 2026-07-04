package com.sparta.copa.copainventory.inventory.service;

import com.sparta.copa.copainventory.common.enums.ReservationStatus;
import com.sparta.copa.copainventory.common.exception.BusinessException;
import com.sparta.copa.copainventory.common.exception.ErrorCode;
import com.sparta.copa.copainventory.inventory.domain.Inventory;
import com.sparta.copa.copainventory.inventory.domain.StockReservation;
import com.sparta.copa.copainventory.inventory.dto.request.RegisterInventoryRequest;
import com.sparta.copa.copainventory.inventory.dto.request.ReserveItemRequest;
import com.sparta.copa.copainventory.inventory.dto.request.ReserveRequest;
import com.sparta.copa.copainventory.inventory.dto.response.InventoryResponse;
import com.sparta.copa.copainventory.inventory.repository.InventoryRepository;
import com.sparta.copa.copainventory.inventory.repository.StockReservationRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재고 예약/확정/해제. 핵심 원칙: "결제 전 예약, 결제 후 확정".
 * 예약은 가용 재고를 원자적으로 차감하고, 예약에 성공한 주문만 결제로 진입한다(오버셀링 방지).
 * 결제 실패/타임아웃/TTL 만료 시 해제로 보상한다. reserve/confirm/release 모두 멱등하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

  // 예약 후 결제 미완료 시 자동 해제까지의 유예(분).
  private static final long RESERVATION_TTL_MINUTES = 5;

  // 같은 재고 행을 항상 동일 순서로 잠가 reserve/release/restore 간 락 교차로 인한 데드락을 방지한다.
  private static final Comparator<ReserveItemRequest> ITEM_LOCK_ORDER =
      Comparator.comparing(ReserveItemRequest::getProductId)
          .thenComparing(i -> normalize(i.getOptionKey()));
  private static final Comparator<StockReservation> RESERVATION_LOCK_ORDER =
      Comparator.comparing(StockReservation::getProductId)
          .thenComparing(StockReservation::getOptionKey);

  private final InventoryRepository inventoryRepository;
  private final StockReservationRepository reservationRepository;

  // 재고 시드/보정: 옵션 leaf를 (productId, optionKey) 기준 재고로 등록(없으면 생성, 있으면 절대값 설정).
  @Transactional
  public InventoryResponse register(RegisterInventoryRequest request) {
    String optionKey = normalize(request.getOptionKey());
    Inventory inventory = inventoryRepository
        .findByProductIdAndOptionKey(request.getProductId(), optionKey)
        .orElse(null);
    if (inventory == null) {
      inventory = inventoryRepository.save(
          Inventory.create(request.getProductId(), optionKey, request.getStock()));
    } else {
      inventory.changeStock(request.getStock());
    }
    return InventoryResponse.from(inventory);
  }

  /**
   * 멱등 시드(상품 생성 이벤트 소비). 해당 (productId, optionKey) 재고가 없을 때만 생성한다.
   * 이미 있으면 절대 덮어쓰지 않는다 — 이벤트 중복 전달로 예약/차감된 재고가 초기값으로 리셋되는 것을 막는다.
   */
  @Transactional
  public void seedIfAbsent(Long productId, String optionKey, int stock) {
    String key = normalize(optionKey);
    if (inventoryRepository.findByProductIdAndOptionKey(productId, key).isPresent()) {
      log.debug("재고 이미 존재, 시드 건너뜀: productId={}, optionKey='{}'", productId, key);
      return;
    }
    inventoryRepository.save(Inventory.create(productId, key, stock));
    log.debug("재고 시드: productId={}, optionKey='{}', stock={}", productId, key, stock);
  }

  @Transactional(readOnly = true)
  public InventoryResponse getInventory(Long productId, String optionKey) {
    Inventory inventory = inventoryRepository
        .findByProductIdAndOptionKey(productId, normalize(optionKey))
        .orElseThrow(() -> new BusinessException(ErrorCode.INVENTORY_NOT_FOUND));
    return InventoryResponse.from(inventory);
  }

  /**
   * 예약(결제 전). 주문의 모든 품목을 한 트랜잭션에서 원자적으로 차감한다.
   * 한 품목이라도 재고가 부족하면 전체 롤백 → 아무것도 예약되지 않는다(부분 예약 없음).
   * 같은 orderId로 이미 예약이 있으면 멱등하게 건너뛴다.
   */
  @Transactional
  public void reserve(ReserveRequest request) {
    if (reservationRepository.existsByOrderId(request.getOrderId())) {
      log.debug("이미 예약된 주문, 멱등 처리: orderId={}", request.getOrderId());
      return;
    }
    LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(RESERVATION_TTL_MINUTES);
    // 재고 행을 항상 동일 순서로 잠가 동시 주문 간 락 교차 데드락을 방지한다.
    List<ReserveItemRequest> items = request.getItems().stream().sorted(ITEM_LOCK_ORDER).toList();
    for (ReserveItemRequest item : items) {
      if (item.getQuantity() == null || item.getQuantity() < 1) {
        throw new BusinessException(ErrorCode.INVALID_QUANTITY);
      }
      String optionKey = normalize(item.getOptionKey());
      // 비관적 쓰기 락으로 같은 행에 대한 동시 예약을 직렬화 → 오버셀링 0.
      Inventory inventory = inventoryRepository
          .findForUpdate(item.getProductId(), optionKey)
          .orElseThrow(() -> new BusinessException(ErrorCode.INVENTORY_NOT_FOUND));
      inventory.decrease(item.getQuantity());
      reservationRepository.save(StockReservation.reserve(
          request.getOrderId(), item.getProductId(), optionKey, item.getQuantity(), expiresAt));
    }
  }

  /**
   * 확정(결제 성공). 가용 재고는 예약 시 이미 차감되어 추가 차감 없음. 멱등(이미 CONFIRMED면 no-op).
   * 예약 행을 잠가 동시 TTL 해제와 상호 배타로 만든다(먼저 전이한 쪽이 이김).
   *
   * <p>살아있는(RESERVED/CONFIRMED) 예약이 하나도 없으면 명시적으로 실패시킨다 — TTL 만료로 이미
   * 해제(재고 복원)된 뒤 조용히 성공하면 재고 차감 없이 주문이 완료되는 오버셀이 생기기 때문.
   * 이 실패를 받은 주문 Saga는 완료 처리를 멈추고 복구 경로(ORDER_COMPLETION_FAILED)로 남긴다.
   */
  @Transactional
  public void confirm(String orderId) {
    boolean alive = false;
    for (StockReservation reservation : reservationRepository.findForUpdateByOrderId(orderId)) {
      if (reservation.getStatus() == ReservationStatus.RESERVED) {
        reservation.confirm();
        alive = true;
      } else if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
        alive = true; // 이미 확정 → 멱등 no-op
      }
    }
    if (!alive) {
      throw new BusinessException(ErrorCode.RESERVATION_EXPIRED);
    }
  }

  // 해제(결제 실패/타임아웃/TTL 만료). 가용 재고를 복원하고 예약을 RELEASED로 표시. 멱등.
  // 예약 행을 먼저 잠가(상호 배타) confirm·다중 인스턴스 스케줄러와의 경합 및 재고 이중 복원을 막는다.
  @Transactional
  public void release(String orderId) {
    List<StockReservation> reservations = reservationRepository
        .findForUpdateByOrderIdAndStatus(orderId, ReservationStatus.RESERVED)
        .stream().sorted(RESERVATION_LOCK_ORDER).toList();
    for (StockReservation reservation : reservations) {
      Inventory inventory = inventoryRepository
          .findForUpdate(reservation.getProductId(), reservation.getOptionKey())
          .orElseThrow(() -> new BusinessException(ErrorCode.INVENTORY_NOT_FOUND));
      inventory.increase(reservation.getQuantity());
      reservation.release();
    }
  }

  // 복원(결제 완료 주문의 사용자 취소). 확정(CONFIRMED)·예약(RESERVED) 모두 가용 재고를 되돌리고 RELEASED로. 멱등.
  @Transactional
  public void restore(String orderId) {
    List<StockReservation> reservations = reservationRepository.findForUpdateByOrderId(orderId)
        .stream().sorted(RESERVATION_LOCK_ORDER).toList();
    for (StockReservation reservation : reservations) {
      if (reservation.isReleased()) {
        continue;
      }
      Inventory inventory = inventoryRepository
          .findForUpdate(reservation.getProductId(), reservation.getOptionKey())
          .orElseThrow(() -> new BusinessException(ErrorCode.INVENTORY_NOT_FOUND));
      inventory.increase(reservation.getQuantity());
      reservation.release();
    }
  }

  // 옵션 없는 상품/누락 시 빈 문자열로 정규화(상품·장바구니와 동일 규약).
  private static String normalize(String optionKey) {
    return (optionKey == null || optionKey.isBlank()) ? "" : optionKey;
  }
}
