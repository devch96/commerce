package com.sparta.copa.copainventory.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.copa.copainventory.common.enums.ReservationStatus;
import com.sparta.copa.copainventory.common.exception.BusinessException;
import com.sparta.copa.copainventory.common.exception.ErrorCode;
import com.sparta.copa.copainventory.inventory.domain.Inventory;
import com.sparta.copa.copainventory.inventory.domain.StockReservation;
import com.sparta.copa.copainventory.inventory.dto.request.ReserveItemRequest;
import com.sparta.copa.copainventory.inventory.dto.request.ReserveRequest;
import com.sparta.copa.copainventory.inventory.repository.InventoryRepository;
import com.sparta.copa.copainventory.inventory.repository.StockReservationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

  @Mock
  private InventoryRepository inventoryRepository;
  @Mock
  private StockReservationRepository reservationRepository;

  @InjectMocks
  private InventoryService inventoryService;

  private ReserveRequest reserveRequest(Long orderId, Long productId, String optionKey, int qty) {
    return ReserveRequest.builder()
        .orderId(orderId)
        .items(List.of(ReserveItemRequest.builder()
            .productId(productId).optionKey(optionKey).quantity(qty).build()))
        .build();
  }

  private StockReservation reserved(Long orderId, Long productId, String optionKey, int qty) {
    return StockReservation.reserve(orderId, productId, optionKey, qty,
        LocalDateTime.now().plusMinutes(5));
  }

  @Test
  @DisplayName("예약하면 가용 재고를 차감하고 예약 기록을 남긴다")
  void reserveDecreasesStock() {
    Inventory inventory = Inventory.create(100L, "", 10);
    given(reservationRepository.existsByOrderId(1L)).willReturn(false);
    given(inventoryRepository.findForUpdate(100L, "")).willReturn(Optional.of(inventory));

    inventoryService.reserve(reserveRequest(1L, 100L, null, 3));

    assertThat(inventory.getStock()).isEqualTo(7);
    verify(reservationRepository).save(any(StockReservation.class));
  }

  @Test
  @DisplayName("같은 주문으로 이미 예약했으면 멱등하게 건너뛴다")
  void reserveIsIdempotent() {
    given(reservationRepository.existsByOrderId(1L)).willReturn(true);

    inventoryService.reserve(reserveRequest(1L, 100L, null, 3));

    verify(inventoryRepository, never()).findForUpdate(any(), any());
    verify(reservationRepository, never()).save(any());
  }

  @Test
  @DisplayName("재고가 부족하면 OUT_OF_STOCK으로 예약을 거절한다")
  void reserveOutOfStock() {
    Inventory inventory = Inventory.create(100L, "", 2);
    given(reservationRepository.existsByOrderId(1L)).willReturn(false);
    given(inventoryRepository.findForUpdate(100L, "")).willReturn(Optional.of(inventory));

    assertThatThrownBy(() -> inventoryService.reserve(reserveRequest(1L, 100L, null, 3)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.OUT_OF_STOCK);
    assertThat(inventory.getStock()).isEqualTo(2);
    verify(reservationRepository, never()).save(any());
  }

  @Test
  @DisplayName("재고 행이 없으면 INVENTORY_NOT_FOUND")
  void reserveInventoryNotFound() {
    given(reservationRepository.existsByOrderId(1L)).willReturn(false);
    given(inventoryRepository.findForUpdate(100L, "")).willReturn(Optional.empty());

    assertThatThrownBy(() -> inventoryService.reserve(reserveRequest(1L, 100L, null, 1)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.INVENTORY_NOT_FOUND);
  }

  @Test
  @DisplayName("확정하면 예약을 CONFIRMED로 전환한다")
  void confirmTransitionsStatus() {
    StockReservation reservation = reserved(1L, 100L, "", 3);
    given(reservationRepository.findForUpdateByOrderIdAndStatus(1L, ReservationStatus.RESERVED))
        .willReturn(List.of(reservation));

    inventoryService.confirm(1L);

    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
  }

  @Test
  @DisplayName("해제하면 가용 재고를 복원하고 예약을 RELEASED로 전환한다")
  void releaseRestoresStock() {
    StockReservation reservation = reserved(1L, 100L, "", 3);
    Inventory inventory = Inventory.create(100L, "", 7);
    given(reservationRepository.findForUpdateByOrderIdAndStatus(1L, ReservationStatus.RESERVED))
        .willReturn(List.of(reservation));
    given(inventoryRepository.findForUpdate(100L, "")).willReturn(Optional.of(inventory));

    inventoryService.release(1L);

    assertThat(inventory.getStock()).isEqualTo(10);
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
  }

  @Test
  @DisplayName("RESERVED 예약이 없으면 확정은 멱등하게 아무것도 하지 않는다")
  void confirmIdempotentWhenNothingReserved() {
    given(reservationRepository.findForUpdateByOrderIdAndStatus(1L, ReservationStatus.RESERVED))
        .willReturn(List.of());

    inventoryService.confirm(1L);

    verify(inventoryRepository, never()).findForUpdate(eq(100L), any());
  }
}
