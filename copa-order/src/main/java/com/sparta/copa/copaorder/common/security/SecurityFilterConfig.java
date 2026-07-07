package com.sparta.copa.copaorder.common.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

// 내부 인증 필터 등록. 테스트(H2 프로파일)에서는 copa.security.enabled=false로 꺼서
// 컨트롤러가 X-User-* 헤더를 직접 받는 기존 방식을 유지한다.
@Configuration
@ConditionalOnProperty(name = "copa.security.enabled", havingValue = "true", matchIfMissing = true)
public class SecurityFilterConfig {

  @Bean
  public FilterRegistrationBean<InternalAuthFilter> internalAuthFilter(
      InternalTokenService tokenService) {
    FilterRegistrationBean<InternalAuthFilter> registration =
        new FilterRegistrationBean<>(new InternalAuthFilter(tokenService));
    registration.addUrlPatterns("/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }
}