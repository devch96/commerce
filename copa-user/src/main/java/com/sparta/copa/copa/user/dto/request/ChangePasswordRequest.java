package com.sparta.copa.copa.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChangePasswordRequest {

  @NotBlank
  private String currentPassword;

  @NotBlank
  @Pattern(
      regexp = "^(?=.*\\d)(?=.*[!@#$%^&*]).{8,64}$",
      message = "비밀번호는 8자 이상이며 숫자와 특수문자(!@#$%^&*)를 포함해야 합니다.")
  private String newPassword;
}
