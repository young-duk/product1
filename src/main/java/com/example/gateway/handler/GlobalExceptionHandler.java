package com.example.gateway.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Gateway 전역 예외 핸들러.
 * <p>
 * Spring Cloud Gateway의 기본 DefaultErrorWebExceptionHandler보다 높은 우선순위(-1)로
 * 등록되어 표준화된 JSON 에러 응답을 반환한다.
 * <p>
 * 응답 형식:
 * <pre>
 * {
 *   "timestamp": "2024-01-01T00:00:00Z",
 *   "status": 500,
 *   "error": "Internal Server Error",
 *   "path": "/api/..."
 * }
 * </pre>
 */
@Order(-1)
@Component
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        String path = exchange.getRequest().getPath().value();

        HttpStatus status = resolveStatus(ex);
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        if (status.is5xxServerError()) {
            log.error("Gateway error on path={}: {}", path, ex.getMessage(), ex);
        } else {
            log.warn("Gateway client error on path={}: {}", path, ex.getMessage());
        }

        String body = buildErrorBody(status, path);
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }

    private HttpStatus resolveStatus(Throwable ex) {
        if (ex instanceof ResponseStatusException rse) {
            return HttpStatus.valueOf(rse.getStatusCode().value());
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String buildErrorBody(HttpStatus status, String path) {
        // Text Block (JDK 21)
        return """
                {
                  "timestamp": "%s",
                  "status": %d,
                  "error": "%s",
                  "path": "%s"
                }
                """.formatted(Instant.now(), status.value(), status.getReasonPhrase(), path);
    }
}
