package com.sparta.copa.copaorder.order.client.feign;

import com.sparta.copa.copaorder.order.client.dto.InventoryReserveRequest;
import com.sparta.copa.copaorder.order.client.dto.OrderRefRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

// 재고 서비스 내부 API(OpenFeign). 예약/확정/해제/복원.
@FeignClient(name = "inventory", url = "${copa.clients.inventory}")
public interface InventoryFeignClient {

  @PostMapping("/internal/inventory/reserve")
  void reserve(@RequestBody InventoryReserveRequest body);

  @PostMapping("/internal/inventory/confirm")
  void confirm(@RequestBody OrderRefRequest body);

  @PostMapping("/internal/inventory/release")
  void release(@RequestBody OrderRefRequest body);

  @PostMapping("/internal/inventory/restore")
  void restore(@RequestBody OrderRefRequest body);
}