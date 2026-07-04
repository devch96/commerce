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

  private static final String ORDER_NO = "ORD-20260704-TEST01";

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
  private Order pendingOrder(BigDecimal discount, Long couponId, PgProvider provider) {
    Order order = Order.place(ORDER_NO, 7L, BigDecimal.valueOf(2000), discount, couponId, provider);
    ReflectionTestUtils.setField(order, "id", 1L);
    return order;
  }

  private OptionPriceView priced(long finalPrice) {
    OptionPriceView view = mock(OptionPriceView.class);
    given(view.getFinalPrice()).willReturn(BigDecimal.valueOf(finalPrice));
    return view;
  }

  private void givenPricedOrder(Long couponId, PgProvider provider) {
    OptionPriceView view = priced(1000);
    Order order = pendingOrder(BigDecimal.ZERO, couponId, provider);
    given(productClient.getOptionPrice(100L, null)).willReturn(view);
    given(commandService.createPlacedOrder(eq(7L), anyList(),
        couponId == null ? isNull() : eq(couponId), eq(provider))).willReturn(order);
  }

  // ===== Phase 1 =====

  @Test
  @DisplayName("Phase1 토스: 재고 예약 후 결제창 정보 반환(결제 호출 없음)")
  void createOrderToss() {
    givenPricedOrder(null, PgProvider.TOSS);

    OrderCheckoutResponse res = orderService.createOrder(7L, request(PgProvider.TOSS));

    assertThat(res.getOrderNo()).isEqualTo(ORDER_NO);
    assertThat(res.getPayableAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000));
    assertThat(res.getRedirectUrl()).isNull();
    verify(inventoryClient).reserve(eq(ORDER_NO), anyList());
    verify(paymentClient, never()).kakaoReady(anyString(), anyLong(), anyLong(), anyString());
    verify(inventoryClient, never()).confirm(anyString());
  }

  @Test
  @DisplayName("Phase1 카카오: ready를 호출해 리다이렉트 URL을 반환한다")
  void createOrderKakaoCallsReady() {
    givenPricedOrder(null, PgProvider.KAKAO);
    PgReadyView ready = mock(PgReadyView.class);
    given(ready.getRedirectUrl()).willReturn("https://kakao/redirect");
    given(paymentClient.kakaoReady(eq(ORDER_NO), eq(7L), eq(2000L), anyString())).willReturn(ready);

    OrderCheckoutResponse res = orderService.createOrder(7L, request(PgProvider.KAKAO));

    assertThat(res.getRedirectUrl()).isEqualTo("https://kakao/redirect");
    verify(inventoryClient).reserve(eq(ORDER_NO), anyList());
  }

  @Test
  @DisplayName("Phase1 쿠폰: 선점→할인 반영→재고 예약")
  void createOrderCoupon() {
    givenPricedOrder(5L, PgProvider.TOSS);
    given(couponClient.reserve(eq(5L), eq(7L), eq(ORDER_NO), any()))
        .willReturn(BigDecimal.valueOf(500));

    OrderCheckoutResponse res = orderService.createOrder(7L, requestWithCoupon(5L, PgProvider.TOSS));

    assertThat(res.getPayableAmount()).isEqualByComparingTo(BigDecimal.valueOf(1500));
    verify(commandService).applyCouponDiscount(1L, BigDecimal.valueOf(500));
    verify(inventoryClient).reserve(eq(ORDER_NO), anyList());
  }

  @Test
  @DisplayName("Phase1 쿠폰 선점 실패: 재고 예약 전이라 해제 없이 주문만 취소")
  void createOrderCouponReserveFail() {
    givenPricedOrder(5L, PgProvider.TOSS);
    given(couponClient.reserve(eq(5L), eq(7L), eq(ORDER_NO), any()))
        .willThrow(new BusinessException(ErrorCode.COUPON_NOT_APPLICABLE));

    assertThatThrownBy(() -> orderService.createOrder(7L, requestWithCoupon(5L, PgProvider.TOSS)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.COUPON_NOT_APPLICABLE);

    verify(inventoryClient, never()).reserve(anyString(), anyList());
    verify(couponClient, never()).release(anyString());
    verify(commandService).markCancelled(eq(1L), anyString());
  }

  @Test
  @DisplayName("Phase1 재고 부족: 결제 준비 없이 주문 취소")
  void createOrderOutOfStock() {
    givenPricedOrder(null, PgProvider.TOSS);
    willThrow(new BusinessException(ErrorCode.OUT_OF_STOCK))
        .given(inventoryClient).reserve(eq(ORDER_NO), anyList());

    assertThatThrownBy(() -> orderService.createOrder(7L, request(PgProvider.TOSS)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.OUT_OF_STOCK);

    verify(inventoryClient, never()).release(anyString());
    verify(commandService).markCancelled(eq(1L), anyString());
  }

  // ===== Phase 2 =====

  @Test
  @DisplayName("Phase2 토스 승인: 재고 확정 + 결제 완료")
  void confirmTossApproved() {
    Order order = pendingOrder(BigDecimal.ZERO, null, PgProvider.TOSS);
    given(orderRepository.findByOrderNo(ORDER_NO)).willReturn(Optional.of(order));
    PaymentView approved = mock(PaymentView.class);
    given(approved.isApproved()).willReturn(true);
    given(paymentClient.tossConfirm(eq(ORDER_NO), eq(7L), eq(2000L), eq("pk")))
        .willReturn(approved);
    given(queryService.getOwnedOrder(ORDER_NO, 7L)).willReturn(
        OrderResponse.builder().orderNo(ORDER_NO).status(OrderStatus.PAYMENT_COMPLETED).build());

    orderService.confirmPayment(7L, ORDER_NO, tossConfirm());

    verify(inventoryClient).confirm(ORDER_NO);
    verify(commandService).markPaymentCompleted(1L);
    verify(inventoryClient, never()).release(anyString());
  }

  @Test
  @DisplayName("Phase2 카카오 승인: 재고 확정 + 결제 완료")
  void confirmKakaoApproved() {
    Order order = pendingOrder(BigDecimal.ZERO, null, PgProvider.KAKAO);
    given(orderRepository.findByOrderNo(ORDER_NO)).willReturn(Optional.of(order));
    PaymentView approved = mock(PaymentView.class);
    given(approved.isApproved()).willReturn(true);
    given(paymentClient.kakaoConfirm(ORDER_NO, 7L, "pt")).willReturn(approved);
    given(queryService.getOwnedOrder(ORDER_NO, 7L)).willReturn(
        OrderResponse.builder().orderNo(ORDER_NO).status(OrderStatus.PAYMENT_COMPLETED).build());

    orderService.confirmPayment(7L, ORDER_NO, kakaoConfirm());

    verify(inventoryClient).confirm(ORDER_NO);
    verify(commandService).markPaymentCompleted(1L);
  }

  @Test
  @DisplayName("Phase2 결제 거절: 재고 예약 해제 + 주문 취소(환불 없음)")
  void confirmDeclinedCompensates() {
    Order order = pendingOrder(BigDecimal.ZERO, null, PgProvider.TOSS);
    given(orderRepository.findByOrderNo(ORDER_NO)).willReturn(Optional.of(order));
    PaymentView declined = mock(PaymentView.class);
    given(declined.isApproved()).willReturn(false);
    given(paymentClient.tossConfirm(eq(ORDER_NO), eq(7L), eq(2000L), eq("pk")))
        .willReturn(declined);

    assertThatThrownBy(() -> orderService.confirmPayment(7L, ORDER_NO, tossConfirm()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_FAILED);

    verify(inventoryClient).release(ORDER_NO);
    verify(inventoryClient, never()).confirm(anyString());
    verify(paymentClient, never()).cancel(anyString());
    verify(commandService).markCancelled(eq(1L), anyString());
  }

  @Test
  @DisplayName("Phase2 쿠폰 주문 거절: 재고와 쿠폰을 모두 해제하고 취소")
  void confirmCouponDeclinedReleasesBoth() {
    Order order = pendingOrder(BigDecimal.valueOf(500), 5L, PgProvider.TOSS);
    given(orderRepository.findByOrderNo(ORDER_NO)).willReturn(Optional.of(order));
    PaymentView declined = mock(PaymentView.class);
    given(declined.isApproved()).willReturn(false);
    given(paymentClient.tossConfirm(eq(ORDER_NO), eq(7L), eq(1500L), eq("pk")))
        .willReturn(declined);

    assertThatThrownBy(() -> orderService.confirmPayment(7L, ORDER_NO, tossConfirm()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_FAILED);

    verify(inventoryClient).release(ORDER_NO);
    verify(couponClient).release(ORDER_NO);
    verify(couponClient, never()).confirm(anyString());
    verify(commandService).markCancelled(eq(1L), anyString());
  }

  @Test
  @DisplayName("Phase2 승인 결과 불확실(5xx/타임아웃): 보상 없이 PENDING_PAYMENT 유지")
  void confirmAmbiguousFailureDoesNotCompensate() {
    Order order = pendingOrder(BigDecimal.ZERO, null, PgProvider.TOSS);
    given(orderRepository.findByOrderNo(ORDER_NO)).willReturn(Optional.of(order));
    given(paymentClient.tossConfirm(eq(ORDER_NO), eq(7L), eq(2000L), eq("pk")))
        .willThrow(new BusinessException(ErrorCode.DEPENDENT_SERVICE_ERROR));

    assertThatThrownBy(() -> orderService.confirmPayment(7L, ORDER_NO, tossConfirm()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.DEPENDENT_SERVICE_ERROR);

    // 승인이 성공했을 수 있으므로 재고 해제·주문 취소를 하지 않는다(재시도 가능 상태 유지).
    verify(inventoryClient, never()).release(anyString());
    verify(commandService, never()).markCancelled(anyLong(), anyString());
  }

  @Test
  @DisplayName("Phase2 교차 PG 확정 차단: 주문의 PG와 다른 provider 요청은 400")
  void confirmRejectsCrossProvider() {
    Order order = pendingOrder(BigDecimal.ZERO, null, PgProvider.KAKAO);
    given(orderRepository.findByOrderNo(ORDER_NO)).willReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.confirmPayment(7L, ORDER_NO, tossConfirm()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PG_REQUEST);

    verify(paymentClient, never()).tossConfirm(anyString(), anyLong(), anyLong(), anyString());
  }

  @Test
  @DisplayName("Phase2 필수 토큰 누락: 토스 요청에 paymentKey가 없으면 PG 호출 전에 400")
  void confirmRejectsMissingToken() {
    Order order = pendingOrder(BigDecimal.ZERO, null, PgProvider.TOSS);
    given(orderRepository.findByOrderNo(ORDER_NO)).willReturn(Optional.of(order));
    ConfirmPaymentRequest noToken =
        ConfirmPaymentRequest.builder().pgProvider(PgProvider.TOSS).build();

    assertThatThrownBy(() -> orderService.confirmPayment(7L, ORDER_NO, noToken))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PG_REQUEST);

    verify(paymentClient, never()).tossConfirm(anyString(), anyLong(), anyLong(), any());
  }
}
