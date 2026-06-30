package com.sparta.copa.copapayment.payment.repository;

import com.sparta.copa.copapayment.payment.domain.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

  // 주문당 결제 1건 → 멱등성 판단·조회 기준.
  Optional<Payment> findByOrderId(String orderId);
}
