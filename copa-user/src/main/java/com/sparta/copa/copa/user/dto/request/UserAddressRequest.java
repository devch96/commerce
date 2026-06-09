package com.sparta.copa.copa.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAddressRequest {

  @NotBlank
  @Size(max = 50)
  private String addressName;

  @NotBlank
  @Size(max = 50)
  private String receiverName;

  @NotBlank
  @Size(max = 20)
  private String zipcode;

  @NotBlank
  @Size(max = 255)
  private String baseAddress;

  @NotBlank
  @Size(max = 255)
  private String detailAddress;

  // 기본 배송지로 등록할지 여부. null이면 false로 간주한다 (첫 주소는 서비스가 자동으로 기본 처리).
  private Boolean isDefault;
}