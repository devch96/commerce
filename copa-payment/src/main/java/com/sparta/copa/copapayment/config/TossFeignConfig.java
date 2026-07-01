package com.sparta.copa.copapayment.config;

import feign.RequestInterceptor;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;

// Feign 전용 설정 클래스. @Configuration을 붙이면 전역 빈으로 등록돼 모든 Feign 클라이언트에 적용되므로 붙이지 않는다.
public class TossFeignConfig {

  @Value("${toss.secret-key}")
  private String secretKey;

  @Bean
  public RequestInterceptor requestInterceptor() {
    return requestTemplate -> {
      // 시크릿 키 뒤에 콜론(:)을 붙여 인코딩하는 것이 토스 규격입니다.
      String token = Base64.getEncoder().encodeToString((secretKey + ":").getBytes());
      requestTemplate.header("Authorization", "Basic " + token);
      requestTemplate.header("Content-Type", MediaType.APPLICATION_JSON_VALUE);
    };
  }
}
