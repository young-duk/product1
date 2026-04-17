package com.example.gateway.handler;

import com.example.gateway.security.JwtTokenProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 개발/테스트 전용 JWT 토큰 발급 컨트롤러.
 * <p>
 * ⚠️  prod 프로파일에서는 비활성화된다.
 * 실제 운영에서는 별도 Auth 서비스가 토큰을 발급해야 한다.
 */
@Profile("!prod")
@RestController
@RequestMapping("/auth")
public class TokenTestController {

    private final JwtTokenProvider tokenProvider;

    public TokenTestController(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    /**
     * 테스트 토큰 발급
     * POST /auth/token
     * { "userId": "user-1", "roles": ["USER"] }
     */
    @PostMapping("/token")
    public Mono<Map<String, String>> issueToken(@RequestBody TokenRequest request) {
        String token = tokenProvider.generateToken(request.userId(), request.roles());
        return Mono.just(Map.of(
                "token", token,
                "type", "Bearer",
                "userId", request.userId()
        ));
    }

    public record TokenRequest(String userId, List<String> roles) {}
}
