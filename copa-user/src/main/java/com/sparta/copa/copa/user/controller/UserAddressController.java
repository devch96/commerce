package com.sparta.copa.copa.user.controller;

import com.sparta.copa.copa.common.response.ApiResponse;
import com.sparta.copa.copa.user.dto.request.UserAddressRequest;
import com.sparta.copa.copa.user.dto.response.UserAddressResponse;
import com.sparta.copa.copa.user.service.UserAddressService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me/addresses")
@RequiredArgsConstructor
public class UserAddressController {

  private static final String USER_ID_HEADER = "X-User-Id";

  private final UserAddressService userAddressService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<UserAddressResponse> add(@RequestHeader(USER_ID_HEADER) Long userId,
      @Valid @RequestBody UserAddressRequest request) {
    return ApiResponse.success(userAddressService.addAddress(userId, request));
  }

  @GetMapping
  public ApiResponse<List<UserAddressResponse>> list(@RequestHeader(USER_ID_HEADER) Long userId) {
    return ApiResponse.success(userAddressService.getAddresses(userId));
  }

  @PutMapping("/{addressId}")
  public ApiResponse<UserAddressResponse> update(@RequestHeader(USER_ID_HEADER) Long userId,
      @PathVariable Long addressId, @Valid @RequestBody UserAddressRequest request) {
    return ApiResponse.success(userAddressService.updateAddress(userId, addressId, request));
  }

  @PatchMapping("/{addressId}/default")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changeDefault(@RequestHeader(USER_ID_HEADER) Long userId,
      @PathVariable Long addressId) {
    userAddressService.changeDefaultAddress(userId, addressId);
  }

  @DeleteMapping("/{addressId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@RequestHeader(USER_ID_HEADER) Long userId, @PathVariable Long addressId) {
    userAddressService.deleteAddress(userId, addressId);
  }
}