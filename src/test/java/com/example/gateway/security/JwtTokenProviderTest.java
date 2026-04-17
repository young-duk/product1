package com.example.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtTokenProvider")
class JwtTokenProviderTest {

    // 256bit 이상의 테스트 시크릿
    private static final String SECRET = "test-secret-key-at-least-256-bits-long-for-hs256-algorithm";
    private static final long EXPIRATION_MS = 3_600_000L; // 1시간

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(SECRET, EXPIRATION_MS);
    }

    @Nested
    @DisplayName("토큰 생성 및 파싱")
    class GenerateAndParse {

        @Test
        @DisplayName("유효한 토큰에서 userId를 파싱할 수 있다")
        void parseUserId() {
            String token = tokenProvider.generateToken("user-42", List.of("USER"));
            JwtClaims claims = tokenProvider.parse(token);
            assertThat(claims.userId()).isEqualTo("user-42");
        }

        @Test
        @DisplayName("유효한 토큰에서 roles를 파싱할 수 있다")
        void parseRoles() {
            String token = tokenProvider.generateToken("user-42", List.of("USER", "ADMIN"));
            JwtClaims claims = tokenProvider.parse(token);
            assertThat(claims.roles()).containsExactlyInAnyOrder("USER", "ADMIN");
        }

        @Test
        @DisplayName("빈 roles로 토큰을 생성하면 파싱 결과도 빈 리스트이다")
        void emptyRoles() {
            String token = tokenProvider.generateToken("user-1", List.of());
            JwtClaims claims = tokenProvider.parse(token);
            assertThat(claims.roles()).isEmpty();
        }
    }

    @Nested
    @DisplayName("토큰 유효성 검사")
    class Validation {

        @Test
        @DisplayName("정상 토큰은 유효하다")
        void validToken() {
            String token = tokenProvider.generateToken("user-1", List.of("USER"));
            assertThat(tokenProvider.isValid(token)).isTrue();
        }

        @Test
        @DisplayName("변조된 토큰은 유효하지 않다")
        void tamperedToken() {
            String token = tokenProvider.generateToken("user-1", List.of("USER"));
            String tampered = token + "tampered";
            assertThat(tokenProvider.isValid(tampered)).isFalse();
        }

        @Test
        @DisplayName("빈 문자열은 유효하지 않다")
        void emptyToken() {
            assertThat(tokenProvider.isValid("")).isFalse();
        }

        @Test
        @DisplayName("만료된 토큰은 유효하지 않다")
        void expiredToken() {
            // 이미 만료된 토큰 생성 (만료 시간 -1ms)
            JwtTokenProvider shortLived = new JwtTokenProvider(SECRET, -1L);
            String token = shortLived.generateToken("user-1", List.of("USER"));
            assertThat(tokenProvider.isValid(token)).isFalse();
        }

        @Test
        @DisplayName("다른 시크릿으로 생성된 토큰은 유효하지 않다")
        void wrongSecret() {
            JwtTokenProvider other = new JwtTokenProvider(
                    "completely-different-secret-key-at-least-256-bits-long-!!!!", EXPIRATION_MS);
            String token = other.generateToken("user-1", List.of("USER"));
            assertThat(tokenProvider.isValid(token)).isFalse();
        }
    }

    @Nested
    @DisplayName("JwtClaims record")
    class ClaimsRecord {

        @Test
        @DisplayName("만료되지 않은 클레임은 isExpired() == false")
        void notExpired() {
            String token = tokenProvider.generateToken("u1", List.of());
            JwtClaims claims = tokenProvider.parse(token);
            assertThat(claims.isExpired()).isFalse();
        }
    }
}
