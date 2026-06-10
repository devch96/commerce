package com.sparta.copa.copa.user.dto.request;

import com.sparta.copa.copa.user.domain.UserRole;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChangeRoleRequest {

  @NotNull
  private UserRole role;
}
