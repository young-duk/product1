# API Gateway — 아키텍처 설계서

## 1. 개요

| 항목 | 내용 |
|------|------|
| 프로젝트명 | api-gateway |
| 버전 | 0.0.1-SNAPSHOT |
| 작성일 | 2026-04-16 |
| Java | JDK 21 (LTS) |
| Framework | Spring Boot 3.2.4 + Spring Cloud 2023.0.1 |

### 목적

마이크로서비스 아키텍처(MSA)에서 클라이언트와 백엔드 서비스 사이의 단일 진입점(Single Entry Point)을 제공한다.

- 라우팅: 경로 기반으로 적절한 마이크로서비스에 요청 전달
- 인증/인가: JWT 검증을 게이트웨이에서 일괄 처리
- 횡단 관심사: 로깅, 요청 추적, Rate Limiting, Circuit Breaker 일원화

---

## 2. 전체 아키텍처

```
                         ┌─────────────────────────────────────────┐
Client (Web/Mobile/CLI)  │           API Gateway  :9080             │
          │              │                                           │
          │  HTTP/S      │  Filter Chain (실행 순서)                 │
          └─────────────►│  -3  LoggingFilter        (pre/post)     │
                         │  -2  CorrelationIdFilter  (pre)          │
                         │  -1  AuthenticationFilter (pre)          │
                         │       ↓                                   │
                         │  Spring Cloud Gateway RouteLocator        │
                         │  - Path Predicate 매칭                    │
                         │  - StripPrefix / RateLimiter / CB 필터   │
                         │       ↓                                   │
                         │  Netty Reverse Proxy                      │
                         └────────────┬────────────────────────────┘
                                      │
              ┌───────────────────────┼────────────────────────┐
              ▼                       ▼                         ▼
    User Service :8081      Product Service :8082    Order Service :8083
```

---

## 3. 기술 스택 선택 근거

### Spring Cloud Gateway (WebFlux / Netty)
- Servlet 기반 Spring MVC 대신 **Reactive(비동기 논블로킹)** 스택을 사용
- Netty 기반 이벤트 루프 → 스레드 수 대비 높은 동시성
- JDK 21 Virtual Threads(`spring.threads.virtual.enabled=true`)와 결합 시 블로킹 구간 자동 최적화

### JDK 21 신기능 활용

| 기능 | 적용 위치 | 이점 |
|------|-----------|------|
| Virtual Threads | Spring Boot 자동 설정 | 블로킹 I/O 구간에서 OS 스레드 낭비 없음 |
| Record | `JwtClaims` | 불변 DTO, 보일러플레이트 제거 |
| Pattern Matching (`instanceof`) | `GlobalExceptionHandler` | 타입 체크·캐스팅 간소화 |
| Text Block | `GlobalExceptionHandler` | 멀티라인 JSON 템플릿 가독성 향상 |
| Sealed Class | (확장 시) GatewayError 계층 | 에러 타입 완전 열거 |

---

## 4. 패키지 구조

```
com.example.gateway
├── GatewayApplication.java          # 진입점
├── config/
│   ├── GatewayConfig.java           # RedisRateLimiter, KeyResolver 빈
│   └── SecurityConfig.java          # SecurityWebFilterChain (WebFlux)
├── filter/
│   ├── AuthenticationFilter.java    # GlobalFilter, Ordered(-1) — JWT 검증
│   ├── CorrelationIdFilter.java     # GlobalFilter, Ordered(-2) — 요청 추적 ID
│   └── LoggingFilter.java           # GlobalFilter, Ordered(-3) — 요청/응답 로깅
├── security/
│   ├── JwtClaims.java               # record — 파싱 결과 DTO
│   ├── JwtTokenProvider.java        # JWT 생성/파싱/검증
│   ├── JwtAuthenticationManager.java  # ReactiveAuthenticationManager
│   └── JwtSecurityContextRepository.java
└── handler/
    ├── GlobalExceptionHandler.java  # ErrorWebExceptionHandler, Order(-1)
    └── FallbackController.java      # Circuit Breaker fallback 응답
```

---

## 5. 요청 처리 흐름

```
[Client Request]
     │
     ▼
LoggingFilter (pre) — 요청 로그 기록
     │
     ▼
CorrelationIdFilter — X-Correlation-Id 부여/전파
     │
     ▼
AuthenticationFilter — JWT 검증
     │  실패 → 401 Unauthorized 즉시 반환
     │  성공 → X-User-Id, X-User-Roles 헤더 추가
     ▼
Spring Security WebFilterChain — 경로별 authorize
     │  거부 → 403 Forbidden
     ▼
RouteLocator — Path Predicate 매칭
     │
     ├── /api/users/**  → RateLimiter → CircuitBreaker → lb://user-service
     ├── /api/products/** → CircuitBreaker → lb://product-service
     └── /api/orders/**  → CircuitBreaker → lb://order-service
              │
              ▼ (회로 열림)
         FallbackController → 503 Service Unavailable
              │
              ▼ (성공)
         Downstream Service 응답
              │
              ▼
LoggingFilter (post) — 응답 상태/소요시간 로그
     │
     ▼
[Client Response]
```

---

## 6. 모듈 의존관계

```
GatewayApplication
    └─ SecurityConfig ──── JwtAuthenticationManager ──── JwtTokenProvider
                     └──── JwtSecurityContextRepository ─┘
    └─ GatewayConfig ──── RedisRateLimiter
                    └──── KeyResolver
    └─ AuthenticationFilter ─── JwtTokenProvider
    └─ CorrelationIdFilter
    └─ LoggingFilter
    └─ GlobalExceptionHandler
    └─ FallbackController
```

---

## 7. 확장 포인트

| 항목 | 현재 구현 | 확장 방향 |
|------|-----------|-----------|
| 서비스 탐색 | 정적 URL | Eureka / Consul (`lb://` URI) 추가 |
| 인증 방식 | JWT Bearer | OAuth2 Resource Server(`spring-security-oauth2-resource-server`) |
| Rate Limit 키 | IP / userId | API Key, Tenant-Id 기반으로 변경 가능 |
| 설정 관리 | application.yml | Spring Cloud Config Server 연동 |
| 분산 추적 | X-Correlation-Id | Micrometer Tracing + Zipkin/Jaeger |
