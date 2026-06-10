package com.sparta.copa.copa.user.dto.response;

import com.sparta.copa.copa.user.domain.User;
import com.sparta.copa.copa.user.domain.UserRole;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResponse {

  private final Long id;
  private final String email;
  private final String name;
  private final String phone;
  private final UserRole role;

  public static UserResponse from(User user) {
    return UserResponse.builder()
        .id(user.getId())
        .email(user.getEmail())
        .name(user.getName())
        .phone(user.getPhone())
        .role(user.getRole())
        .build();
  }
}
