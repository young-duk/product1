package com.example.gateway.security;

import java.time.Instant;
import java.util.List;

/**
 * JWT 파싱 결과를 담는 불변 레코드 (JDK 21 Record).
 *
 * @param userId  사용자 식별자
 * @param roles   권한 목록
 * @param expiry  만료 시각
 */
public record JwtClaims(String userId, List<String> roles, Instant expiry) {

    public boolean isExpired() {
        return Instant.now().isAfter(expiry);
    }
}
