package com.sparta.copa.copagateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {

  @Bean
  public RouteLocator gatewayRoutes(RouteLocatorBuilder builder, GatewayProperties properties) {
    return builder.routes()
        .route("copa-user", route -> route
            .path("/auth/**", "/users/**")
            .uri(properties.getAuthServiceUri().toString()))
        .route("copa-product", route -> route
            .path("/products/**", "/categories/**")
            .uri(properties.getProductServiceUri().toString()))
        .build();
  }
}
