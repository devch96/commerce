package com.sparta.copa.copainventory.inventory.controller;

import com.sparta.copa.copainventory.common.response.ApiResponse;
import com.sparta.copa.copainventory.inventory.dto.request.OrderReferenceRequest;
import com.sparta.copa.copainventory.inventory.dto.request.RegisterInventoryRequest;
import com.sparta.copa.copainventory.inventory.dto.request.ReserveRequest;
import com.sparta.copa.copainventory.inventory.dto.response.InventoryResponse;
import com.sparta.copa.copainventory.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 서비스 간 내부 호출용. 게이트웨이를 거치지 않고 서비스 메시 내부에서만 접근한다(주문 Saga가 호출).
@RestController
@RequestMapping("/internal/inventory")
@RequiredArgsConstructor
public class InternalInventoryController {

  private final InventoryService inventoryService;

  @GetMapping
  public ApiResponse<InventoryResponse> getInventory(@RequestParam Long productId,
      @RequestParam(required = false) String optionKey) {
    return ApiResponse.success(inventoryService.getInventory(productId, optionKey));
  }

  // 재고 시드/보정(상품 옵션 leaf를 재고로 등록).
  @PostMapping("/register")
  public ApiResponse<InventoryResponse> register(
      @Valid @RequestBody RegisterInventoryRequest request) {
    return ApiResponse.success(inventoryService.register(request));
  }

  // 예약(결제 전). 재고 부족이면 409 OUT_OF_STOCK → 주문 Saga가 INVENTORY_FAILED로 취소.
  @PostMapping("/reserve")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reserve(@Valid @RequestBody ReserveRequest request) {
    inventoryService.reserve(request);
  }

  // 확정(결제 성공).
  @PostMapping("/confirm")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void confirm(@Valid @RequestBody OrderReferenceRequest request) {
    inventoryService.confirm(request.getOrderId());
  }

  // 해제(결제 실패/취소, 보상).
  @PostMapping("/release")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void release(@Valid @RequestBody OrderReferenceRequest request) {
    inventoryService.release(request.getOrderId());
  }
}
