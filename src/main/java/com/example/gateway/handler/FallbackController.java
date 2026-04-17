package com.example.gateway.handler;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Circuit Breaker fallback 응답 핸들러.
 * <p>
 * 각 라우트의 circuitBreaker fallbackUri가 이 컨트롤러를 가리킨다.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/user-service")
    public Mono<Map<String, String>> userServiceFallback() {
        return fallback("user-service");
    }

    @GetMapping("/product-service")
    public Mono<Map<String, String>> productServiceFallback() {
        return fallback("product-service");
    }

    @GetMapping("/order-service")
    public Mono<Map<String, String>> orderServiceFallback() {
        return fallback("order-service");
    }

    private Mono<Map<String, String>> fallback(String service) {
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                service + " is currently unavailable. Please try again later.");
    }
}
