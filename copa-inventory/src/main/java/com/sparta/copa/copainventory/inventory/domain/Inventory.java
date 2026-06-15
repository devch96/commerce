package com.sparta.copa.copainventory.inventory.domain;

import com.sparta.copa.copainventory.common.exception.BusinessException;
import com.sparta.copa.copainventory.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 상품(옵션)별 가용 재고. 재고의 권위 원천(authority).
 * 상품 서비스의 옵션 leaf({@code optionKey})와 1:1로 매핑되며, 예약 시 가용 재고를 차감한다.
 */
@Entity
@Getter
@Table(name = "inventory")
@EntityListeners(AuditingEntityListener.class)
@DynamicInsert
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inventory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  // 옵션 경로(예: 색상:네이비/사이즈:M). 옵션 없는 상품은 빈 문자열("").
  @Column(name = "option_key", nullable = false, length = 255)
  private String optionKey;

  // 가용 재고(예약 시 즉시 감소). 확정은 추가 차감 없음, 해제는 복원.
  @Column(name = "stock", nullable = false)
  private Integer stock;

  // 낙관적 락. 일반 상품의 동시 갱신을 직렬화한다(경합이 큰 한정 수량은 비관적 락 조회로 보강).
  @Version
  private Long version;

  @CreatedDate
  @Column(updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  private LocalDateTime updatedAt;

  @Builder
  private Inventory(Long productId, String optionKey, Integer stock) {
    this.productId = productId;
    this.optionKey = optionKey;
    this.stock = stock;
  }

  public static Inventory create(Long productId, String optionKey, int stock) {
    validateStock(stock);
    return Inventory.builder()
        .productId(productId)
        .optionKey(optionKey == null ? "" : optionKey)
        .stock(stock)
        .build();
  }

  // 가용 재고 차감(예약). 부족하면 OUT_OF_STOCK.
  public void decrease(int quantity) {
    if (stock < quantity) {
      throw new BusinessException(ErrorCode.OUT_OF_STOCK);
    }
    this.stock -= quantity;
  }

  // 가용 재고 복원(예약 해제).
  public void increase(int quantity) {
    this.stock += quantity;
  }

  // 재고 시드/보정(절대값 설정).
  public void changeStock(int newStock) {
    validateStock(newStock);
    this.stock = newStock;
  }

  private static void validateStock(int stock) {
    if (stock < 0) {
      throw new BusinessException(ErrorCode.INVALID_STOCK);
    }
  }
}
