# API Gateway — 보안 설계서 (Security Specification)

## 1. 보안 원칙

1. **Stateless 인증**: 세션 없이 JWT만으로 인증 상태 유지
2. **최소 권한 원칙**: 필요한 경로만 `permitAll`, 나머지는 인증 필수
3. **입력 검증**: 외부로부터 오는 모든 헤더/토큰은 검증 후 사용
4. **정보 노출 최소화**: 에러 응답에 내부 스택 트레이스 미포함
5. **CSRF 불필요**: REST API + Stateless → CSRF 토큰 비활성화

---

## 2. 인증 (Authentication)

### 2-1. JWT 구조

```
Header.Payload.Signature
```

**Header**
```json
{ "alg": "HS256", "typ": "JWT" }
```

**Payload (클레임)**

| 클레임 | 타입 | 설명 |
|--------|------|------|
| `sub` | String | 사용자 ID |
| `roles` | String[] | 권한 목록 (e.g. `["USER", "ADMIN"]`) |
| `iat` | timestamp | 발급 시각 |
| `exp` | timestamp | 만료 시각 |

**서명 알고리즘**: HMAC-SHA256 (HS256)

---

### 2-2. 토큰 검증 로직

```
1. Authorization 헤더 존재 여부 확인
2. "Bearer " 접두사 확인
3. JWT 서명 검증 (secretKey)
4. 만료 시각(exp) 검증
5. 성공 시: userId, roles → 다운스트림 헤더 주입
6. 실패 시: 즉시 401 반환 (다운스트림 미전달)
```

---

### 2-3. 화이트리스트 경로 (인증 불필요)

| 경로 패턴 | 용도 |
|-----------|------|
| `/auth/**` | 로그인·토큰 갱신 등 인증 API |
| `/public/**` | 공개 리소스 |
| `/actuator/health` | 헬스 체크 |
| `/actuator/info` | 앱 정보 |
| `/v3/api-docs/**` | OpenAPI 스펙 |
| `/swagger-ui/**` | Swagger UI |
| `/fallback/**` | Circuit Breaker fallback |

---

### 2-4. 환경 변수로 관리해야 할 민감 정보

| 환경 변수 | 설명 | 기본값(개발용) |
|-----------|------|---------------|
| `JWT_SECRET` | JWT 서명 키 (256bit 이상 권장) | `your-256-bit-secret-...` |
| `JWT_EXPIRATION_MS` | 토큰 유효 시간(ms) | `3600000` (1시간) |
| `REDIS_PASSWORD` | Redis 비밀번호 | (빈 문자열) |

> **주의**: `JWT_SECRET`은 프로덕션 환경에서 반드시 강력한 랜덤값으로 교체해야 한다.

---

## 3. 인가 (Authorization)

### 3-1. 역할(Role) 정의

| 역할 | 설명 |
|------|------|
| `USER` | 일반 사용자 |
| `ADMIN` | 관리자 |

Spring Security의 `GrantedAuthority` 변환: `ROLE_USER`, `ROLE_ADMIN`

### 3-2. 경로별 접근 권한 (현재)

| 경로 | 인증 | 역할 제한 |
|------|------|-----------|
| `/auth/**` | 불필요 | 없음 |
| `/public/**` | 불필요 | 없음 |
| `/actuator/health` | 불필요 | 없음 |
| `/api/**` | 필요 | `USER` 이상 |

> 역할 기반 세분화가 필요한 경우 `SecurityConfig`의 `authorizeExchange`에 `.pathMatchers(...).hasRole("ADMIN")` 추가

---

## 4. Rate Limiting

### 4-1. 동작 방식

- Redis **Token Bucket** 알고리즘 (`RedisRateLimiter`)
- 키 해석기: 인증된 사용자 → `X-User-Id`, 미인증 → 클라이언트 IP

### 4-2. 기본 설정

| 파라미터 | 기본값 | 설명 |
|----------|--------|------|
| `replenishRate` | 10 | 초당 토큰 보충 수 |
| `burstCapacity` | 20 | 최대 버스트 (순간 최대 요청) |
| `requestedTokens` | 1 | 요청당 소비 토큰 수 |

Rate Limit 초과 시 응답: **`HTTP 429 Too Many Requests`**

> 환경 변수 `rate-limiter.replenish-rate`, `rate-limiter.burst-capacity`로 조정 가능 (application.yml 참조)

---

## 5. Circuit Breaker

### 5-1. Resilience4j 설정

| 파라미터 | 기본값 | 설명 |
|----------|--------|------|
| `slidingWindowSize` | 10 | 최근 N 호출 기록 |
| `failureRateThreshold` | 50% | 실패율 임계값 (초과 시 회로 오픈) |
| `waitDurationInOpenState` | 10s | 오픈 상태 유지 시간 |
| `permittedCallsInHalfOpenState` | 3 | 반오픈 상태에서 허용 호출 수 |
| `timeoutDuration` | 5s | 호출 타임아웃 |

### 5-2. 상태 전이

```
[CLOSED] ─ 실패율 >= 50% ──► [OPEN]
                                │
                           10초 후
                                ▼
                           [HALF-OPEN] ─ 3건 테스트 성공 ──► [CLOSED]
                                       └─ 실패 ──────────────► [OPEN]
```

---

## 6. 보안 코딩 가이드라인 (Security Coding Guidelines)

### 6-1. 헤더 주입 방어

- `X-User-Id`, `X-User-Roles` 헤더는 **게이트웨이가 직접 설정**
- 클라이언트가 이 헤더를 전송해도 게이트웨이 필터에서 덮어씀 (mutate)
- 다운스트림 서비스는 이 헤더를 신뢰 가능한 게이트웨이 발급 정보로 처리

### 6-2. JWT Secret 관리

- 코드·Git에 시크릿 절대 하드코딩 금지
- `application.yml`의 `${JWT_SECRET}` 형식으로 환경 변수 참조
- 프로덕션: Kubernetes Secret, AWS Secrets Manager, HashiCorp Vault 사용 권장

### 6-3. CORS 설정

- 개발 환경: `allowedOriginPatterns: "*"` 허용
- 프로덕션: 허용 오리진을 명시적으로 열거

```yaml
# 프로덕션 CORS 예시
allowedOriginPatterns:
  - "https://app.example.com"
  - "https://admin.example.com"
```

### 6-4. Actuator 보안

- 프로덕션에서 `gateway`, `prometheus` 엔드포인트는 내부망 또는 인증 후에만 접근 허용
- `management.server.port`를 별도 포트로 분리하거나 Spring Security로 보호

### 6-5. 로깅 주의사항

- Authorization 헤더(토큰)는 로그에 기록하지 않음
- `LoggingFilter`는 경로, 상태 코드, 소요 시간만 기록
- PII(개인식별정보) 포함 파라미터는 마스킹 처리
