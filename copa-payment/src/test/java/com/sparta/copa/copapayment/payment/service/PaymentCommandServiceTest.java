package com.sparta.copa.copapayment.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.sparta.copa.copapayment.common.enums.PaymentStatus;
import com.sparta.copa.copapayment.payment.domain.Payment;
import com.sparta.copa.copapayment.payment.dto.request.PaymentRequest;
import com.sparta.copa.copapayment.payment.dto.response.PaymentResponse;
import com.sparta.copa.copapayment.payment.gateway.PaymentGateway;
import com.sparta.copa.copapayment.payment.gateway.PgApproval;
import com.sparta.copa.copapayment.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentCommandServiceTest {

  @Mock
  private PaymentRepository paymentRepository;
  @Mock
  private PaymentGateway paymentGateway;

  @InjectMocks
  private PaymentCommandService commandService;

  private PaymentRequest request() {
    return PaymentRequest.builder().orderId(1L).userId(7L).amount(BigDecimal.valueOf(10000)).build();
  }

  @Test
  @DisplayName("PG 승인 시 APPROVED로 결제된다")
  void processApproved() {
    given(paymentRepository.saveAndFlush(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));
    given(paymentGateway.approve(eq(1L), any())).willReturn(new PgApproval(true, "PG-xyz"));

    PaymentResponse response = commandService.process(request());

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    assertThat(response.getPgTransactionId()).isEqualTo("PG-xyz");
  }

  @Test
  @DisplayName("PG 거절 시 FAILED로 기록되고 예외를 던지지 않는다")
  void processDeclined() {
    given(paymentRepository.saveAndFlush(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));
    given(paymentGateway.approve(eq(1L), any())).willReturn(new PgApproval(false, null));

    PaymentResponse response = commandService.process(request());

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
  }
}
