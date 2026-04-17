package com.example.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Spring Cloud Gateway 설정.
 * <p>
 * 라우트 정의는 application.yml의 spring.cloud.gateway.routes 에서 관리하며,
 * 이 클래스에서는 Rate Limiter / KeyResolver 등 공통 빈을 등록한다.
 */
@Configuration
public class GatewayConfig {

    /**
     * Redis 기반 Rate Limiter.
     *
     * @param replenishRate 초당 보충 토큰 수
     * @param burstCapacity 최대 버스트 용량
     */
    @Bean
    public RedisRateLimiter redisRateLimiter(
            @Value("${rate-limiter.replenish-rate:10}") int replenishRate,
            @Value("${rate-limiter.burst-capacity:20}") int burstCapacity) {
        return new RedisRateLimiter(replenishRate, burstCapacity, 1);
    }

    /**
     * Rate Limit 키 해석기 — 인증된 사용자는 userId, 미인증 요청은 원격 IP 기준으로 제한.
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                return Mono.just(userId);
            }
            // IP 기반 fallback
            String remoteAddr = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just(remoteAddr);
        };
    }
}
