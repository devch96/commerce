package com.sparta.copa.copagateway.logging;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 게이트웨이를 통과하는 모든 요청의 메서드·경로·상태코드·소요시간을 로깅한다.
 * WebFlux라 AOP 대신 GlobalFilter를 쓴다. traceId/spanId는 Micrometer Tracing이 로그에 주입한다.
 */
@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.currentTimeMillis();
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod().name();
        String path = request.getURI().getPath();

        return chain.filter(exchange).doFinally(signal -> {
            long elapsed = System.currentTimeMillis() - start;
            var status = exchange.getResponse().getStatusCode();
            log.info("{} {} -> {} ({}ms)", method, path, status, elapsed);
        });
    }

    @Override
    public int getOrder() {
        // 응답 상태코드가 확정된 뒤 로깅하도록 가장 바깥(낮은 우선순위)에 둔다.
        return Ordered.LOWEST_PRECEDENCE;
    }
}