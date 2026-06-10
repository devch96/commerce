package com.sparta.copa.copa.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
public class SignUpRequest {

  @Email
  @NotBlank
  private String email;

  // 8~64자, 숫자와 특수문자(!@#$%^&*)를 각각 1자 이상 포함.
  @NotBlank
  @Pattern(
      regexp = "^(?=.*\\d)(?=.*[!@#$%^&*]).{8,64}$",
      message = "비밀번호는 8자 이상이며 숫자와 특수문자(!@#$%^&*)를 포함해야 합니다.")
  private String password;

  @NotBlank
  @Size(max = 30)
  private String name;

  @NotBlank
  @Pattern(regexp = "^01[0-9]-?\\d{3,4}-?\\d{4}$", message = "올바른 휴대폰 번호 형식이 아닙니다.")
  private String phone;
}
