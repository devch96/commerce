package com.sparta.copa.copaproduct.cart.repository;

import com.sparta.copa.copaproduct.cart.domain.CartItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

  // 장바구니 조회는 상품을 함께 fetch(N+1 방지). 상품은 soft delete라 항상 존재한다.
  @Query("select ci from CartItem ci join fetch ci.product"
      + " where ci.userId = :userId order by ci.addedAt asc")
  List<CartItem> findWithProductByUserId(@Param("userId") Long userId);

  Optional<CartItem> findByUserIdAndProduct_Id(Long userId, Long productId);

  void deleteByUserId(Long userId);
}
