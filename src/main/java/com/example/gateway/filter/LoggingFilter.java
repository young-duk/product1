package com.example.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 요청/응답 로깅 GlobalFilter.
 * <p>
 * Pre: 메서드, 경로, Correlation-Id 기록
 * Post: 응답 상태 코드 및 소요 시간(ms) 기록
 */
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String correlationId = request.getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        long startTime = System.currentTimeMillis();

        log.info("[{}] --> {} {}", correlationId,
                request.getMethod(), request.getPath().value());

        return chain.filter(exchange)
                .doFinally(signalType -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    int status = exchange.getResponse().getStatusCode() != null
                            ? exchange.getResponse().getStatusCode().value()
                            : 0;
                    log.info("[{}] <-- {} ({} ms)", correlationId, status, elapsed);
                });
    }

    @Override
    public int getOrder() {
        return -3; // CorrelationIdFilter(-2) 보다 먼저 실행 — correlationId가 설정된 후 로깅
    }
}
