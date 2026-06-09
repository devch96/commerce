package com.sparta.copa.copa.auth.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

  private final String secret;
  private final long accessExpirationMillis;
  private final long refreshExpirationMillis;

  public JwtProperties(String secret, long accessExpirationMillis, long refreshExpirationMillis) {
    this.secret = secret;
    this.accessExpirationMillis = accessExpirationMillis;
    this.refreshExpirationMillis = refreshExpirationMillis;
  }
}
