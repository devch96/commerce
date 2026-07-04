package com.sparta.copa.copainventory.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sparta.copa.copainventory.common.exception.BusinessException;
import com.sparta.copa.copainventory.common.exception.ErrorCode;
import com.sparta.copa.copainventory.inventory.dto.request.RegisterInventoryRequest;
import com.sparta.copa.copainventory.inventory.dto.request.ReserveItemRequest;
import com.sparta.copa.copainventory.inventory.dto.request.ReserveRequest;
import com.sparta.copa.copainventory.inventory.repository.InventoryRepository;
import com.sparta.copa.copainventory.inventory.repository.StockReservationRepository;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 동시 예약 직렬화 검증: 재고 1개를 두 주문이 동시에 예약하면 정확히 하나만 성공하고
 * 오버셀링(재고 음수)이 발생하지 않는다(비관적 락).
 */
@SpringBootTest
class InventoryConcurrencyTest {

  @Autowired
  private InventoryService inventoryService;
  @Autowired
  private InventoryRepository inventoryRepository;
  @Autowired
  private StockReservationRepository reservationRepository;

  @BeforeEach
  void setUp() {
    reservationRepository.deleteAll();
    inventoryRepository.deleteAll();
    inventoryService.register(RegisterInventoryRequest.builder()
        .productId(100L).optionKey(null).stock(1).build());
  }

  @Test
  @DisplayName("재고 1개에 두 주문이 동시 예약하면 하나만 성공하고 오버셀링이 없다")
  void concurrentReserveDoesNotOversell() throws Exception {
    int threads = 2;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CyclicBarrier barrier = new CyclicBarrier(threads);
    AtomicInteger success = new AtomicInteger();
    AtomicInteger outOfStock = new AtomicInteger();

    Future<?>[] futures = new Future<?>[threads];
    for (int i = 0; i < threads; i++) {
      String orderId = "ORD-" + (i + 1);
      futures[i] = pool.submit(() -> {
        try {
          barrier.await();
          inventoryService.reserve(ReserveRequest.builder()
              .orderId(orderId)
              .items(List.of(ReserveItemRequest.builder()
                  .productId(100L).quantity(1).build()))
              .build());
          success.incrementAndGet();
        } catch (BusinessException e) {
          if (e.getErrorCode() == ErrorCode.OUT_OF_STOCK) {
            outOfStock.incrementAndGet();
          }
        } catch (Exception ignored) {
          // 락 경합으로 인한 다른 예외도 실패로 간주(성공 수만 정확하면 오버셀링은 없다).
        }
        return null;
      });
    }
    for (Future<?> future : futures) {
      future.get();
    }
    pool.shutdown();

    assertThat(success.get()).isEqualTo(1);
    assertThat(outOfStock.get()).isEqualTo(1);
    assertThat(inventoryRepository.findByProductIdAndOptionKey(100L, "").orElseThrow().getStock())
        .isZero();
    assertThat(reservationRepository.count()).isEqualTo(1);
  }
}
