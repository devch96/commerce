package com.sparta.copa.copagateway.filter;

import com.sparta.copa.copagateway.config.GatewayProperties;
import com.sparta.copa.copagateway.jwt.JwtProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final String USER_ID_HEADER = "X-User-Id";
  private static final String USER_ROLE_HEADER = "X-User-Role";
  private static final String ROLE_CLAIM = "role";

  private final JwtProvider jwtProvider;
  private final GatewayProperties gatewayProperties;
  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerWebExchange sanitized = stripTrustedHeaders(exchange);
    ServerHttpRequest request = sanitized.getRequest();

    if (isWhitelisted(request.getURI().getPath())) {
      return chain.filter(sanitized);
    }

    Optional<String> token = resolveToken(request);
    if (token.isEmpty()) {
      return unauthorized(sanitized, "Missing access token");
    }

    try {
      Claims claims = jwtProvider.parse(token.get());
      ServerHttpRequest authorized = request.mutate()
          .header(USER_ID_HEADER, claims.getSubject())
          .header(USER_ROLE_HEADER, claims.get(ROLE_CLAIM, String.class))
          .build();
      return chain.filter(sanitized.mutate().request(authorized).build());
    } catch (JwtException | IllegalArgumentException e) {
      log.warn("JWT 검증 실패: {}", e.getMessage());
      return unauthorized(sanitized, "Invalid access token");
    }
  }

  // 클라이언트가 임의로 주입한 신뢰 헤더는 위변조 가능성이 있으므로 게이트웨이 진입 시 제거한다.
  private ServerWebExchange stripTrustedHeaders(ServerWebExchange exchange) {
    ServerHttpRequest request = exchange.getRequest().mutate()
        .headers(headers -> {
          headers.remove(USER_ID_HEADER);
          headers.remove(USER_ROLE_HEADER);
        })
        .build();
    return exchange.mutate().request(request).build();
  }

  private boolean isWhitelisted(String path) {
    return gatewayProperties.getWhitelist().stream()
        .anyMatch(pattern -> pathMatcher.match(pattern, path));
  }

  private Optional<String> resolveToken(ServerHttpRequest request) {
    String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
      return Optional.of(authorization.substring(BEARER_PREFIX.length()));
    }
    return Optional.empty();
  }

  private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(HttpStatus.UNAUTHORIZED);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    byte[] body = ("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
    DataBuffer buffer = response.bufferFactory().wrap(body);
    return response.writeWith(Mono.just(buffer));
  }

  @Override
  public int getOrder() {
    return -1;
  }
}
