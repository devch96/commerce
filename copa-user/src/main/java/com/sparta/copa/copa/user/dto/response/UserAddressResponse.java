package com.sparta.copa.copa.user.dto.response;

import com.sparta.copa.copa.user.domain.UserAddress;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserAddressResponse {

  private final Long id;
  private final String addressName;
  private final String receiverName;
  private final String zipcode;
  private final String baseAddress;
  private final String detailAddress;
  private final boolean isDefault;

  public static UserAddressResponse from(UserAddress address) {
    return UserAddressResponse.builder()
        .id(address.getId())
        .addressName(address.getAddressName())
        .receiverName(address.getReceiverName())
        .zipcode(address.getZipcode())
        .baseAddress(address.getBaseAddress())
        .detailAddress(address.getDetailAddress())
        .isDefault(Boolean.TRUE.equals(address.getIsDefault()))
        .build();
  }
}