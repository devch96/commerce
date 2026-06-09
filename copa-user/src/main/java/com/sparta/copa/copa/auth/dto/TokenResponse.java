package com.sparta.copa.copa.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TokenResponse {

  private final String tokenType;
  private final String accessToken;
  private final String refreshToken;

  public static TokenResponse of(String accessToken, String refreshToken) {
    return TokenResponse.builder()
        .tokenType("Bearer")
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .build();
  }
}
