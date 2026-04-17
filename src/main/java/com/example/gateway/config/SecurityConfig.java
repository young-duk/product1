package com.example.gateway.config;

import com.example.gateway.security.JwtAuthenticationManager;
import com.example.gateway.security.JwtSecurityContextRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Mono;

/**
 * Spring Security — WebFlux(Reactive) 보안 설정.
 * <p>
 * Gateway는 Stateless이므로 세션/CSRF를 비활성화하고
 * JWT 기반 인증만 사용한다.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final JwtAuthenticationManager authenticationManager;
    private final JwtSecurityContextRepository securityContextRepository;

    public SecurityConfig(JwtAuthenticationManager authenticationManager,
                          JwtSecurityContextRepository securityContextRepository) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                // Stateless — 세션 불필요
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                // CSRF 비활성화 (REST API)
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // HTTP Basic 비활성화
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                // Form Login 비활성화
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                // JWT 인증 관리자 연결
                .authenticationManager(authenticationManager)
                .securityContextRepository(securityContextRepository)
                // 인증 실패 시 401 응답
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((swe, e) ->
                                Mono.fromRunnable(() ->
                                        swe.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED)))
                        .accessDeniedHandler((swe, e) ->
                                Mono.fromRunnable(() ->
                                        swe.getResponse().setStatusCode(HttpStatus.FORBIDDEN)))
                )
                // 경로별 접근 제어
                .authorizeExchange(auth -> auth
                        // 공개 경로 — 인증 불필요
                        .pathMatchers(
                                "/auth/**",
                                "/public/**",
                                "/actuator/**",       // 모니터링 전체 허용 (운영 시 제한 권장)
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/fallback/**"
                        ).permitAll()
                        // 나머지 모든 요청 — 인증 필요
                        .anyExchange().authenticated()
                )
                .build();
    }
}
