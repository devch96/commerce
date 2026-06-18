package com.sparta.copa.copaorder.order.service;

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

import com.sparta.copa.copaorder.common.exception.BusinessException;
import com.sparta.copa.copaorder.common.exception.ErrorCode;
import com.sparta.copa.copaorder.order.client.CouponClient;
import com.sparta.copa.copaorder.order.client.InventoryClient;
import com.sparta.copa.copaorder.order.client.PaymentClient;
import com.sparta.copa.copaorder.order.client.ProductClient;
import com.sparta.copa.copaorder.order.client.dto.OptionPriceView;
import com.sparta.copa.copaorder.order.client.dto.PaymentView;
import com.sparta.copa.copaorder.common.enums.OrderStatus;
import com.sparta.copa.copaorder.order.domain.Order;
import com.sparta.copa.copaorder.order.dto.request.CreateOrderRequest;
import com.sparta.copa.copaorder.order.dto.request.OrderLineRequest;
import com.sparta.copa.copaorder.order.dto.response.OrderResponse;
import com.sparta.copa.copaorder.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
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

  private CreateOrderRequest request() {
    return CreateOrderRequest.builder()
        .items(List.of(OrderLineRequest.builder().productId(100L).quantity(2).build()))
        .build();
  }

  private CreateOrderRequest requestWithCoupon(Long couponId) {
    return CreateOrderRequest.builder()
        .items(List.of(OrderLineRequest.builder().productId(100L).quantity(2).build()))
        .couponId(couponId)
        .build();
  }

  private Order placedOrder() {
    Order order = Order.place(7L, BigDecimal.valueOf(2000), BigDecimal.ZERO, null);
    ReflectionTestUtils.setField(order, "id", 1L);
    return order;
  }

  private OptionPriceView priced(long finalPrice) {
    OptionPriceView view = mock(OptionPriceView.class);
    given(view.getFinalPrice()).willReturn(BigDecimal.valueOf(finalPrice));
    return view;
  }

  @Test
  @DisplayName("해피패스: 예약→결제 승인→재고 확정→결제 완료")
  void happyPath() {
    Order order = placedOrder();
    OptionPriceView pricedView = priced(1000);
    given(productClient.getOptionPrice(100L, null)).willReturn(pricedView);
    given(commandService.createPlacedOrder(eq(7L), anyList(), isNull())).willReturn(order);
    PaymentView approved = mock(PaymentView.class);
    given(approved.isApproved()).willReturn(true);
    given(paymentClient.pay(eq(1L), eq(7L), any())).willReturn(approved);
    given(queryService.getOwnedOrder(1L, 7L))
        .willReturn(OrderResponse.builder().id(1L).status(OrderStatus.PAYMENT_COMPLETED).build());

    orderService.createOrder(7L, request());

    verify(inventoryClient).reserve(eq(1L), anyList());
    verify(inventoryClient).confirm(1L);
    verify(commandService).markPaymentCompleted(1L);
    verify(inventoryClient, never()).release(anyLong());
    verify(couponClient, never()).reserve(anyLong(), anyLong(), anyLong(), any());
  }

  @Test
  @DisplayName("쿠폰 적용: 선점→할인 반영→결제→재고 확정+쿠폰 사용 확정")
  void couponHappyPath() {
    Order order = placedOrder();
    OptionPriceView pricedView = priced(1000);
    given(productClient.getOptionPrice(100L, null)).willReturn(pricedView);
    given(commandService.createPlacedOrder(eq(7L), anyList(), eq(5L))).willReturn(order);
    given(couponClient.reserve(eq(5L), eq(7L), eq(1L), any())).willReturn(BigDecimal.valueOf(500));
    PaymentView approved = mock(PaymentView.class);
    given(approved.isApproved()).willReturn(true);
    given(paymentClient.pay(eq(1L), eq(7L), eq(BigDecimal.valueOf(1500)))).willReturn(approved);
    given(queryService.getOwnedOrder(1L, 7L))
        .willReturn(OrderResponse.builder().id(1L).status(OrderStatus.PAYMENT_COMPLETED).build());

    orderService.createOrder(7L, requestWithCoupon(5L));

    verify(commandService).applyCouponDiscount(1L, BigDecimal.valueOf(500));
    verify(paymentClient).pay(1L, 7L, BigDecimal.valueOf(1500));
    verify(inventoryClient).confirm(1L);
    verify(couponClient).confirm(1L);
    verify(commandService).markPaymentCompleted(1L);
  }

  @Test
  @DisplayName("쿠폰 선점 실패: 재고 예약 전 단계라 쿠폰/재고 해제 없이 주문만 취소한다")
  void couponReserveFailCancels() {
    Order order = placedOrder();
    OptionPriceView pricedView = priced(1000);
    given(productClient.getOptionPrice(100L, null)).willReturn(pricedView);
    given(commandService.createPlacedOrder(eq(7L), anyList(), eq(5L))).willReturn(order);
    given(couponClient.reserve(eq(5L), eq(7L), eq(1L), any()))
        .willThrow(new BusinessException(ErrorCode.COUPON_NOT_APPLICABLE));

    assertThatThrownBy(() -> orderService.createOrder(7L, requestWithCoupon(5L)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.COUPON_NOT_APPLICABLE);

    verify(inventoryClient, never()).reserve(anyLong(), anyList());
    verify(paymentClient, never()).pay(anyLong(), anyLong(), any());
    verify(couponClient, never()).release(anyLong());
    verify(commandService).markCancelled(eq(1L), anyString());
  }

  @Test
  @DisplayName("쿠폰 적용 주문의 결제 거절: 재고와 쿠폰 선점을 모두 해제하고 취소한다")
  void couponPaymentDeclinedReleasesBoth() {
    Order order = placedOrder();
    OptionPriceView pricedView = priced(1000);
    given(productClient.getOptionPrice(100L, null)).willReturn(pricedView);
    given(commandService.createPlacedOrder(eq(7L), anyList(), eq(5L))).willReturn(order);
    given(couponClient.reserve(eq(5L), eq(7L), eq(1L), any())).willReturn(BigDecimal.valueOf(500));
    PaymentView declined = mock(PaymentView.class);
    given(declined.isApproved()).willReturn(false);
    given(paymentClient.pay(eq(1L), eq(7L), any())).willReturn(declined);

    assertThatThrownBy(() -> orderService.createOrder(7L, requestWithCoupon(5L)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_FAILED);

    verify(inventoryClient).release(1L);
    verify(couponClient).release(1L);
    verify(couponClient, never()).confirm(anyLong());
    verify(commandService).markCancelled(eq(1L), anyString());
  }

  @Test
  @DisplayName("재고 부족: 예약 실패 시 결제하지 않고 주문을 취소한다")
  void outOfStockCancels() {
    Order order = placedOrder();
    OptionPriceView pricedView = priced(1000);
    given(productClient.getOptionPrice(100L, null)).willReturn(pricedView);
    given(commandService.createPlacedOrder(eq(7L), anyList(), isNull())).willReturn(order);
    willThrow(new BusinessException(ErrorCode.OUT_OF_STOCK))
        .given(inventoryClient).reserve(eq(1L), anyList());

    assertThatThrownBy(() -> orderService.createOrder(7L, request()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.OUT_OF_STOCK);

    verify(paymentClient, never()).pay(anyLong(), anyLong(), any());
    verify(inventoryClient, never()).release(anyLong());
    verify(commandService).markCancelled(eq(1L), anyString());
  }

  @Test
  @DisplayName("결제 거절: 재고 예약을 해제하고 주문을 취소한다")
  void paymentDeclinedCompensates() {
    Order order = placedOrder();
    OptionPriceView pricedView = priced(1000);
    given(productClient.getOptionPrice(100L, null)).willReturn(pricedView);
    given(commandService.createPlacedOrder(eq(7L), anyList(), isNull())).willReturn(order);
    PaymentView declined = mock(PaymentView.class);
    given(declined.isApproved()).willReturn(false);
    given(paymentClient.pay(eq(1L), eq(7L), any())).willReturn(declined);

    assertThatThrownBy(() -> orderService.createOrder(7L, request()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_FAILED);

    verify(inventoryClient).release(1L);
    verify(inventoryClient, never()).confirm(anyLong());
    verify(paymentClient, never()).cancel(anyLong());
    verify(commandService).markCancelled(eq(1L), anyString());
  }
}
