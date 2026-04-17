package com.example.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("CorrelationIdFilter")
class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("X-Correlation-Id 헤더가 없으면 요청에 UUID가 주입된다")
    void generatesCorrelationIdWhenMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // beforeCommit 콜백이 실제로 실행되도록 응답을 flush
        StepVerifier.create(
                filter.filter(exchange, chain)
                      .then(exchange.getResponse().setComplete())
        ).verifyComplete();

        String correlationId = exchange.getResponse().getHeaders()
                .getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(correlationId).isNotBlank();
    }

    @Test
    @DisplayName("X-Correlation-Id가 이미 있으면 그 값을 그대로 사용한다")
    void preservesExistingCorrelationId() {
        String existing = "my-trace-id-123";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, existing)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(
                filter.filter(exchange, chain)
                      .then(exchange.getResponse().setComplete())
        ).verifyComplete();

        String correlationId = exchange.getResponse().getHeaders()
                .getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(correlationId).isEqualTo(existing);
    }
}
