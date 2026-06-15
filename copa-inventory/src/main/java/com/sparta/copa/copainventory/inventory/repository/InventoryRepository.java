package com.sparta.copa.copainventory.inventory.repository;

import com.sparta.copa.copainventory.inventory.domain.Inventory;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

  Optional<Inventory> findByProductIdAndOptionKey(Long productId, String optionKey);

  // 예약 핫패스: 경합이 큰 행을 비관적 쓰기 락으로 직렬화한다(오버셀링 0).
  // @Version(낙관적)은 확정/해제 등 일반 갱신을 보강한다.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from Inventory i where i.productId = :productId and i.optionKey = :optionKey")
  Optional<Inventory> findForUpdate(@Param("productId") Long productId,
      @Param("optionKey") String optionKey);
}
