# API Gateway — 테스트 가이드

## 1. 테스트 전략

| 레이어 | 방식 | 도구 |
|--------|------|------|
| 단위 테스트 | POJO 수준 검증 | JUnit 5, AssertJ, Mockito |
| 필터 테스트 | MockServerWebExchange | reactor-test (StepVerifier) |
| 통합 테스트 | 실제 게이트웨이 기동 | `@SpringBootTest` + `WebTestClient` |

---

## 2. 현재 작성된 테스트

### 2-1. `JwtTokenProviderTest`

| 테스트 | 검증 항목 |
|--------|-----------|
| `parseUserId` | 토큰에서 userId 파싱 |
| `parseRoles` | 토큰에서 roles 파싱 |
| `emptyRoles` | 빈 역할 목록 처리 |
| `validToken` | 정상 토큰 유효성 통과 |
| `tamperedToken` | 변조 토큰 거부 |
| `emptyToken` | 빈 문자열 거부 |
| `expiredToken` | 만료 토큰 거부 |
| `wrongSecret` | 다른 시크릿 토큰 거부 |
| `notExpired` | 만료되지 않은 JwtClaims 검증 |

### 2-2. `AuthenticationFilterTest`

| 테스트 | 검증 항목 |
|--------|-----------|
| `validTokenPassesThrough` | 유효 토큰 → 체인 통과 |
| `missingAuthHeader` | Authorization 헤더 없음 → 401 |
| `invalidToken` | 잘못된 토큰 → 401 |
| `whitelistedPathSkipsAuth` | `/auth/**` 경로 → 토큰 없이 통과 |
| `actuatorHealthSkipsAuth` | `/actuator/health` 경로 → 토큰 없이 통과 |
| `filterOrder` | 필터 우선순위 -1 확인 |

### 2-3. `CorrelationIdFilterTest`

| 테스트 | 검증 항목 |
|--------|-----------|
| `generatesCorrelationIdWhenMissing` | ID 없을 때 UUID 자동 생성 |
| `preservesExistingCorrelationId` | 기존 ID 그대로 유지 |

---

## 3. 테스트 실행 방법

```bash
# 전체 테스트 실행
mvn clean test

# 특정 클래스만 실행
mvn test -Dtest=JwtTokenProviderTest

# 커버리지 포함 (jacoco 추가 시)
mvn clean verify
```

---

## 4. 수동 API 테스트 (curl)

### 4-1. 토큰 없이 보호된 경로 접근 → 401

```bash
curl -i http://localhost:9080/api/users/1
# HTTP/1.1 401 Unauthorized
```

### 4-2. 잘못된 토큰 → 401

```bash
curl -i http://localhost:9080/api/products/1 \
  -H "Authorization: Bearer invalid.token"
# HTTP/1.1 401 Unauthorized
```

### 4-3. 유효한 토큰으로 요청 (다운스트림 없을 시 502/503 예상)

```bash
# 1. 테스트 토큰 생성 (별도 /auth 서비스 또는 직접 생성)
TOKEN="eyJhbGci..."

curl -i http://localhost:9080/api/users/1 \
  -H "Authorization: Bearer $TOKEN"
```

### 4-4. Health Check (인증 불필요)

```bash
curl http://localhost:9080/actuator/health
# {"status":"UP",...}
```

### 4-5. Rate Limit 테스트 (초당 10회 초과 시 429)

```bash
for i in $(seq 1 25); do
  curl -s -o /dev/null -w "%{http_code}\n" \
    http://localhost:9080/api/users/1 \
    -H "Authorization: Bearer $TOKEN"
done
# 처음 20개: 200 또는 502 (다운스트림 없음)
# 이후: 429 Too Many Requests
```

### 4-6. Correlation ID 전파 확인

```bash
curl -i http://localhost:9080/actuator/health \
  -H "X-Correlation-Id: my-trace-001"
# 응답 헤더에 X-Correlation-Id: my-trace-001 포함 확인
```

---

## 5. Swagger UI 접속

애플리케이션 기동 후:
```
http://localhost:9080/swagger-ui.html
```

---

## 6. Gateway 라우트 목록 확인

```bash
curl http://localhost:9080/actuator/gateway/routes | python -m json.tool
```

---

## 7. 자기 수정 모드 (Self-Correction 체크리스트)

테스트 실패 시 아래 순서로 진단한다:

1. **컴파일 오류**: `mvn compile` → 오류 메시지 확인
2. **의존성 충돌**: `mvn dependency:tree` → 버전 충돌 확인
3. **설정 오류**: `spring.cloud.gateway` 설정 누락 여부 확인
4. **Redis 미기동**: Rate Limit 테스트 시 Redis 실행 확인
   ```bash
   # Redis 기동 (Docker)
   docker run -d -p 6379:6379 redis:7-alpine
   ```
5. **JWT Secret 길이 부족**: HS256은 256bit(32byte) 이상 필요
6. **순환 의존성**: `SecurityConfig` ↔ `JwtAuthenticationManager` 간 순환 없는지 확인
