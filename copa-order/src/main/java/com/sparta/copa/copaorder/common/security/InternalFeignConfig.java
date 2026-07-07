package com.sparta.copa.copaorder.common.security;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 주문 Saga가 /internal/** 를 호출할 때(재고·쿠폰·결제·상품) 서명된 서비스 토큰을 자동 첨부한다.
// 수신 서비스는 이 토큰을 검증해 "정말 order 서비스가 보낸 호출"임을 확인한다(게이트웨이를 거치지 않는 경로 보호).
@Configuration
public class InternalFeignConfig {

  private static final String SERVICE_NAME = "copa-order";

  @Bean
  public RequestInterceptor internalServiceTokenInterceptor(InternalTokenService tokenService) {
    return template -> template.header(
        InternalTokenService.SERVICE_HEADER, tokenService.issueService(SERVICE_NAME));
  }
}