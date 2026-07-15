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
            .path("/products/**", "/categories/**", "/cart/**")
            .uri(properties.getProductServiceUri().toString()))
        .route("copa-order", route -> route
            .path("/orders/**", "/admin/orders/**")
            .uri(properties.getOrderServiceUri().toString()))
        .route("copa-payment", route -> route
            .path("/payments/**")
            .uri(properties.getPaymentServiceUri().toString()))
        .route("copa-coupon", route -> route
            .path("/coupons/**", "/admin/coupons/**")
            .uri(properties.getCouponServiceUri().toString()))
        .route("copa-ticket", route -> route
            .path("/events/**", "/tickets/**", "/admin/events/**")
            .uri(properties.getTicketServiceUri().toString()))
        .build();
  }
}
