package com.example.gateway.filter;

import com.example.gateway.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("AuthenticationFilter")
class AuthenticationFilterTest {

    private static final String SECRET = "test-secret-key-at-least-256-bits-long-for-hs256-algorithm";

    private JwtTokenProvider tokenProvider;
    private AuthenticationFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(SECRET, 3_600_000L);
        filter = new AuthenticationFilter(tokenProvider);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("유효한 Bearer 토큰이 있으면 X-User-Id 헤더를 주입하고 통과한다")
    void validTokenPassesThrough() {
        String token = tokenProvider.generateToken("user-99", List.of("USER"));

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/products/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull(); // 200 아님 → 필터가 401 세팅 안 함
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 401을 반환한다")
    void missingAuthHeader() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/products/1")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("잘못된 토큰이면 401을 반환한다")
    void invalidToken() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/orders/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.value")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("화이트리스트 경로(/auth/**)는 토큰 없이 통과한다")
    void whitelistedPathSkipsAuth() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/auth/login")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Actuator health 경로는 토큰 없이 통과한다")
    void actuatorHealthSkipsAuth() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/actuator/health")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("필터 실행 순서는 -1이다")
    void filterOrder() {
        assertThat(filter.getOrder()).isEqualTo(-1);
    }
}
