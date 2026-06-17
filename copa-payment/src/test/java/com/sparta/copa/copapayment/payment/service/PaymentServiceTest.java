package com.sparta.copa.copapayment.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.copa.copapayment.common.enums.PaymentStatus;
import com.sparta.copa.copapayment.common.exception.BusinessException;
import com.sparta.copa.copapayment.common.exception.ErrorCode;
import com.sparta.copa.copapayment.payment.domain.Payment;
import com.sparta.copa.copapayment.payment.dto.request.PaymentRequest;
import com.sparta.copa.copapayment.payment.dto.response.PaymentResponse;
import com.sparta.copa.copapayment.payment.gateway.PaymentGateway;
import com.sparta.copa.copapayment.payment.gateway.PgApproval;
import com.sparta.copa.copapayment.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

  @Mock
  private PaymentCommandService commandService;
  @Mock
  private PaymentRepository paymentRepository;
  @Mock
  private PaymentGateway paymentGateway;

  @InjectMocks
  private PaymentService paymentService;

  private PaymentRequest request() {
    return PaymentRequest.builder().orderId(1L).userId(7L).amount(BigDecimal.valueOf(10000)).build();
  }

  @Test
  @DisplayName("신규 주문이면 결제 생성에 위임한다")
  void payDelegatesWhenNew() {
    Payment approved = Payment.request(1L, 7L, BigDecimal.valueOf(10000));
    approved.approve("PG-xyz");
    given(paymentRepository.findByOrderId(1L)).willReturn(Optional.empty());
    given(commandService.process(any(PaymentRequest.class)))
        .willReturn(PaymentResponse.from(approved));

    PaymentResponse response = paymentService.pay(request());

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    assertThat(response.getPgTransactionId()).isEqualTo("PG-xyz");
  }

  @Test
  @DisplayName("같은 주문 재결제는 기존 결제를 멱등하게 반환하고 생성에 위임하지 않는다")
  void payIdempotent() {
    Payment existing = Payment.request(1L, 7L, BigDecimal.valueOf(10000));
    existing.approve("PG-old");
    given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(existing));

    PaymentResponse response = paymentService.pay(request());

    assertThat(response.getPgTransactionId()).isEqualTo("PG-old");
    verify(commandService, never()).process(any());
  }

  @Test
  @DisplayName("동시 중복 요청으로 유니크 충돌이 나면 먼저 성공한 결제를 멱등하게 반환한다")
  void payIdempotentOnConflict() {
    Payment winner = Payment.request(1L, 7L, BigDecimal.valueOf(10000));
    winner.approve("PG-winner");
    given(paymentRepository.findByOrderId(1L))
        .willReturn(Optional.empty())
        .willReturn(Optional.of(winner));
    given(commandService.process(any(PaymentRequest.class)))
        .willThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));

    PaymentResponse response = paymentService.pay(request());

    assertThat(response.getPgTransactionId()).isEqualTo("PG-winner");
  }

  @Test
  @DisplayName("승인된 결제는 취소되어 CANCELLED·전액 환불된다")
  void cancelApproved() {
    Payment payment = Payment.request(1L, 7L, BigDecimal.valueOf(10000));
    payment.approve("PG-xyz");
    given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(payment));

    PaymentResponse response = paymentService.cancel(1L);

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    assertThat(response.getRefundedAmount()).isEqualByComparingTo("10000");
    verify(paymentGateway).cancel("PG-xyz");
  }

  @Test
  @DisplayName("결제 기록이 없으면 취소는 PAYMENT_NOT_FOUND")
  void cancelMissing() {
    given(paymentRepository.findByOrderId(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> paymentService.cancel(1L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
  }
}
