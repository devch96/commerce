package com.sparta.copa.copa.auth.dto;

import jakarta.validation.constraints.Email;
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
public class SignUpRequest {

  @Email
  @NotBlank
  private String email;

  @NotBlank
  @Size(min = 8, max = 64)
  private String password;

  @NotBlank
  @Size(max = 30)
  private String name;
}
