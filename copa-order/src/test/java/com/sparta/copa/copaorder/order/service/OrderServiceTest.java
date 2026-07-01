package com.sparta.copa.copaorder.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.copa.copaorder.common.enums.OrderStatus;
import com.sparta.copa.copaorder.common.enums.PgProvider;
import com.sparta.copa.copaorder.common.exception.BusinessException;
import com.sparta.copa.copaorder.common.exception.ErrorCode;
import com.sparta.copa.copaorder.order.client.CouponClient;
import com.sparta.copa.copaorder.order.client.InventoryClient;
import com.sparta.copa.copaorder.order.client.PaymentClient;
import com.sparta.copa.copaorder.order.client.ProductClient;
import com.sparta.copa.copaorder.order.client.dto.OptionPriceView;
import com.sparta.copa.copaorder.order.client.dto.PaymentView;
import com.sparta.copa.copaorder.order.client.dto.PgReadyView;
import com.sparta.copa.copaorder.order.domain.Order;
import com.sparta.copa.copaorder.order.dto.request.ConfirmPaymentRequest;
import com.sparta.copa.copaorder.order.dto.request.CreateOrderRequest;
import com.sparta.copa.copaorder.order.dto.request.OrderLineRequest;
import com.sparta.copa.copaorder.order.dto.response.OrderCheckoutResponse;
import com.sparta.copa.copaorder.order.dto.response.OrderResponse;
import com.sparta.copa.copaorder.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock
  private OrderCommandService commandService;
  @Mock
  private OrderRepository orderRepository;
  @Mock
  private OrderQueryService queryService;
  @Mock
  private ProductClient productClient;
  @Mock
  private InventoryClient inventoryClient;
  @Mock
  private PaymentClient paymentClient;
  @Mock
  private CouponClient couponClient;

  @InjectMocks
  private OrderService orderService;

  private CreateOrderRequest request(PgProvider provider) {
    return CreateOrderRequest.builder()
        .items(List.of(OrderLineRequest.builder().productId(100L).quantity(2).build()))
        .pgProvider(provider)
        .build();
  }

  private CreateOrderRequest requestWithCoupon(Long couponId, PgProvider provider) {
    return CreateOrderRequest.builder()
        .items(List.of(OrderLineRequest.builder().productId(100L).quantity(2).build()))
        .couponId(couponId)
        .pgProvider(provider)
        .build();
  }

  private ConfirmPaymentRequest tossConfirm() {
    return ConfirmPaymentRequest.builder().pgProvider(PgProvider.TOSS).paymentKey("pk").build();
  }

  private ConfirmPaymentRequest kakaoConfirm() {
    return ConfirmPaymentRequest.builder().pgProvider(PgProvider.KAKAO).pgToken("pt").build();
  }

  // 결제 대기(PENDING_PAYMENT) 주문. total=2000, discount 지정.
  private Order pendingOrder(BigDecimal discount, Long couponId) {
    Order order = Order.place(7L, BigDecimal.valueOf(2000), discount, couponId);
    ReflectionTestUtils.setField(order, "id", 1L);
    return order;
  }

  private OptionPriceView priced(long finalPrice) {
    OptionPriceView view = mock(OptionPriceView.class);
    given(view.getFinalPrice()).willReturn(BigDecimal.valueOf(finalPrice));
    return view;
  }

  private void givenPricedOrder(Long couponId) {
    OptionPriceView view = priced(1000);
    Order order = pendingOrder(BigDecimal.ZERO, couponId);
    given(productClient.getOptionPrice(100L, null)).willReturn(view);
    given(commandService.createPlacedOrder(eq(7L), anyList(),
        couponId == null ? isNull() : eq(couponId))).willReturn(order);
  }

  // ===== Phase 1 =====

  @Test
  @DisplayName("Phase1 토스: 재고 예약 후 결제창 정보 반환(결제 호출 없음)")
  void createOrderToss() {
    givenPricedOrder(null);

    OrderCheckoutResponse res = orderService.createOrder(7L, request(PgProvider.TOSS));

    assertThat(res.getOrderId()).isEqualTo(1L);
    assertThat(res.getPayableAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000));
    assertThat(res.getRedirectUrl()).isNull();
    verify(inventoryClient).reserve(eq(1L), anyList());
    verify(paymentClient, never()).kakaoReady(anyLong(), anyLong(), anyLong(), anyString());
    verify(inventoryClient, never()).confirm(anyLong());
  }

  @Test
  @DisplayName("Phase1 카카오: ready를 호출해 리다이렉트 URL을 반환한다")
  void createOrderKakaoCallsReady() {
    givenPricedOrder(null);
    PgReadyView ready = mock(PgReadyView.class);
    given(ready.getRedirectUrl()).willReturn("https://kakao/redirect");
    given(paymentClient.kakaoReady(eq(1L), eq(7L), eq(2000L), anyString())).willReturn(ready);

    OrderCheckoutResponse res = orderService.createOrder(7L, request(PgProvider.KAKAO));

    assertThat(res.getRedirectUrl()).isEqualTo("https://kakao/redirect");
    verify(inventoryClient).reserve(eq(1L), anyList());
  }

  @Test
  @DisplayName("Phase1 쿠폰: 선점→할인 반영→재고 예약")
  void createOrderCoupon() {
    givenPricedOrder(5L);
    given(couponClient.reserve(eq(5L), eq(7L), eq(1L), any())).willReturn(BigDecimal.valueOf(500));

    OrderCheckoutResponse res = orderService.createOrder(7L, requestWithCoupon(5L, PgProvider.TOSS));

    assertThat(res.getPayableAmount()).isEqualByComparingTo(BigDecimal.valueOf(1500));
    verify(commandService).applyCouponDiscount(1L, BigDecimal.valueOf(500));
    verify(inventoryClient).reserve(eq(1L), anyList());
  }

  @Test
  @DisplayName("Phase1 쿠폰 선점 실패: 재고 예약 전이라 해제 없이 주문만 취소")
  void createOrderCouponReserveFail() {
    givenPricedOrder(5L);
    given(couponClient.reserve(eq(5L), eq(7L), eq(1L), any()))
        .willThrow(new BusinessException(ErrorCode.COUPON_NOT_APPLICABLE));

    assertThatThrownBy(() -> orderService.createOrder(7L, requestWithCoupon(5L, PgProvider.TOSS)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.COUPON_NOT_APPLICABLE);

    verify(inventoryClient, never()).reserve(anyLong(), anyList());
    verify(couponClient, never()).release(anyLong());
    verify(commandService).markCancelled(eq(1L), anyString());
  }

  @Test
  @DisplayName("Phase1 재고 부족: 결제 준비 없이 주문 취소")
  void createOrderOutOfStock() {
    givenPricedOrder(null);
    willThrow(new BusinessException(ErrorCode.OUT_OF_STOCK))
        .given(inventoryClient).reserve(eq(1L), anyList());

    assertThatThrownBy(() -> orderService.createOrder(7L, request(PgProvider.TOSS)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.OUT_OF_STOCK);

    verify(inventoryClient, never()).release(anyLong());
    verify(commandService).markCancelled(eq(1L), anyString());
  }

  // ===== Phase 2 =====

  @Test
  @DisplayName("Phase2 토스 승인: 재고 확정 + 결제 완료")
  void confirmTossApproved() {
    Order order = pendingOrder(BigDecimal.ZERO, null);
    given(orderRepository.findById(1L)).willReturn(Optional.of(order));
    PaymentView approved = mock(PaymentView.class);
    given(approved.isApproved()).willReturn(true);
    given(paymentClient.tossConfirm(eq(1L), eq(7L), eq(2000L), eq("pk"))).willReturn(approved);
    given(queryService.getOwnedOrder(1L, 7L))
        .willReturn(OrderResponse.builder().id(1L).status(OrderStatus.PAYMENT_COMPLETED).build());

    orderService.confirmPayment(7L, 1L, tossConfirm());

    verify(inventoryClient).confirm(1L);
    verify(commandService).markPaymentCompleted(1L);
    verify(inventoryClient, never()).release(anyLong());
  }

  @Test
  @DisplayName("Phase2 카카오 승인: 재고 확정 + 결제 완료")
  void confirmKakaoApproved() {
    Order order = pendingOrder(BigDecimal.ZERO, null);
    given(orderRepository.findById(1L)).willReturn(Optional.of(order));
    PaymentView approved = mock(PaymentView.class);
    given(approved.isApproved()).willReturn(true);
    given(paymentClient.kakaoConfirm(1L, 7L, "pt")).willReturn(approved);
    given(queryService.getOwnedOrder(1L, 7L))
        .willReturn(OrderResponse.builder().id(1L).status(OrderStatus.PAYMENT_COMPLETED).build());

    orderService.confirmPayment(7L, 1L, kakaoConfirm());

    verify(inventoryClient).confirm(1L);
    verify(commandService).markPaymentCompleted(1L);
  }

  @Test
  @DisplayName("Phase2 결제 거절: 재고 예약 해제 + 주문 취소(환불 없음)")
  void confirmDeclinedCompensates() {
    Order order = pendingOrder(BigDecimal.ZERO, null);
    given(orderRepository.findById(1L)).willReturn(Optional.of(order));
    PaymentView declined = mock(PaymentView.class);
    given(declined.isApproved()).willReturn(false);
    given(paymentClient.tossConfirm(eq(1L), eq(7L), eq(2000L), eq("pk"))).willReturn(declined);

    assertThatThrownBy(() -> orderService.confirmPayment(7L, 1L, tossConfirm()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_FAILED);

    verify(inventoryClient).release(1L);
    verify(inventoryClient, never()).confirm(anyLong());
    verify(paymentClient, never()).cancel(anyLong());
    verify(commandService).markCancelled(eq(1L), anyString());
  }

  @Test
  @DisplayName("Phase2 쿠폰 주문 거절: 재고와 쿠폰을 모두 해제하고 취소")
  void confirmCouponDeclinedReleasesBoth() {
    Order order = pendingOrder(BigDecimal.valueOf(500), 5L);
    given(orderRepository.findById(1L)).willReturn(Optional.of(order));
    PaymentView declined = mock(PaymentView.class);
    given(declined.isApproved()).willReturn(false);
    given(paymentClient.tossConfirm(eq(1L), eq(7L), eq(1500L), eq("pk"))).willReturn(declined);

    assertThatThrownBy(() -> orderService.confirmPayment(7L, 1L, tossConfirm()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_FAILED);

    verify(inventoryClient).release(1L);
    verify(couponClient).release(1L);
    verify(couponClient, never()).confirm(anyLong());
    verify(commandService).markCancelled(eq(1L), anyString());
  }
}