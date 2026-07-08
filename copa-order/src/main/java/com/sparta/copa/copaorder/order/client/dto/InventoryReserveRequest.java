package com.sparta.copa.copaorder.order.client.dto;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 재고 예약 요청. orderId(orderNo) 기준 멱등, items는 옵션 leaf 단위 라인.
@Getter
@RequiredArgsConstructor
public class InventoryReserveRequest {

  private final String orderId;
  private final List<ReserveLine> items;
}