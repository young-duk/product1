package com.example.gateway.filter;

import com.example.gateway.security.JwtClaims;
import com.example.gateway.security.JwtTokenProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * JWT 인증 GlobalFilter — 모든 요청에 적용된다.
 * <p>
 * 처리 순서:
 * 1. 화이트리스트 경로는 통과
 * 2. Authorization: Bearer {token} 추출
 * 3. JWT 검증 실패 시 401 반환
 * 4. 성공 시 다운스트림에 X-User-Id, X-User-Roles 헤더 전달
 */
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLES = "X-User-Roles";

    // SecurityConfig의 permitAll 경로와 일치해야 한다
    private static final List<String> WHITELIST = List.of(
            "/auth/",
            "/public/",
            "/actuator/health",
            "/actuator/info",
            "/v3/api-docs",
            "/swagger-ui",
            "/fallback/"
    );

    private final JwtTokenProvider tokenProvider;

    public AuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange);
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        if (!tokenProvider.isValid(token)) {
            return unauthorized(exchange);
        }

        JwtClaims claims = tokenProvider.parse(token);
        String roles = String.join(",", claims.roles());

        // 다운스트림 서비스에 사용자 정보 헤더 주입 (원본 Authorization 헤더는 그대로 유지)
        ServerWebExchange mutated = exchange.mutate()
                .request(r -> r
                        .header(HEADER_USER_ID, claims.userId())
                        .header(HEADER_USER_ROLES, roles))
                .build();

        return chain.filter(mutated);
    }

    @Override
    public int getOrder() {
        return -1; // 가장 먼저 실행
    }

    private boolean isWhitelisted(String path) {
        return WHITELIST.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
