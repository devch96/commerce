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
import org.springframework.http.HttpMethod;
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

    if (isWhitelisted(request.getMethod(), request.getURI().getPath())) {
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

  // 화이트리스트 항목은 "GET /products/**"처럼 메서드를 앞에 붙여 메서드별로 공개할 수 있다.
  // (예: 상품 조회는 비로그인 공개, 등록/수정/삭제는 인증 필요) 메서드 없이 경로만 적으면 모든 메서드에 공개.
  private boolean isWhitelisted(HttpMethod method, String path) {
    for (String entry : gatewayProperties.getWhitelist()) {
      int space = entry.indexOf(' ');
      if (space > 0) {
        String allowedMethod = entry.substring(0, space);
        String pattern = entry.substring(space + 1).trim();
        if (method != null && method.name().equalsIgnoreCase(allowedMethod)
            && pathMatcher.match(pattern, path)) {
          return true;
        }
      } else if (pathMatcher.match(entry, path)) {
        return true;
      }
    }
    return false;
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
