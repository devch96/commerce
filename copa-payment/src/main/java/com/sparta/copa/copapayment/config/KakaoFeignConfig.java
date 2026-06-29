package com.sparta.copa.copapayment.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;

public class KakaoFeignConfig {
  @Value("${kakao.secret-key}")
  private String secretKey;

  @Bean
  public RequestInterceptor requestInterceptor() {
    return requestTemplate -> {
      requestTemplate.header("Authorization", "SECRET_KEY " + secretKey);
      requestTemplate.header("Content-Type", MediaType.APPLICATION_JSON_VALUE);
    };
  }

}
