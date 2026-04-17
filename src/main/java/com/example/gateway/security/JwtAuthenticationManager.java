package com.example.gateway.security;

import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * JWT 기반 ReactiveAuthenticationManager.
 * <p>
 * SecurityContextRepository에서 전달한 Authentication(credentials=rawToken)을
 * 검증하고, 성공 시 authorities가 채워진 인증 객체를 반환한다.
 */
@Component
public class JwtAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationManager(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = authentication.getCredentials().toString();

        if (!tokenProvider.isValid(token)) {
            return Mono.empty();
        }

        JwtClaims claims = tokenProvider.parse(token);

        List<SimpleGrantedAuthority> authorities = claims.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(claims.userId(), token, authorities);
        auth.setDetails(claims);

        return Mono.just(auth);
    }
}
