# API Gateway 프로젝트

## 개요
Spring Cloud Gateway 기반 MSA API 게이트웨이.
JWT 인증, Circuit Breaker, Rate Limiting, CORS, 모니터링 기능 포함.

## 기술 스택
- Java 21 (Virtual Threads 활성화)
- Spring Boot 3.2.4
- Spring Cloud Gateway (WebFlux / Netty 기반 Reactive)
- Spring Security (Stateless, JWT 기반)
- JJWT 0.12.3
- Resilience4j (Circuit Breaker)
- Redis (Rate Limiting — 현재 주석 처리, 필요 시 활성화)
- SpringDoc OpenAPI 2.3.0 (Swagger UI)
- Micrometer + Prometheus (모니터링)

## 실행 방법
```bash
mvn spring-boot:run
```

## 주요 포트
| 서비스 | 포트 |
|--------|------|
| Gateway | 9080 |
| User Service | 8081 |
| Product Service | 8082 |
| Order Service | 8083 |

## 주요 엔드포인트
| 경로 | 설명 | 인증 필요 |
|------|------|----------|
| POST /auth/token | 테스트용 JWT 발급 (dev/test 전용) | X |
| GET /actuator/health | 헬스체크 | X |
| GET /swagger-ui.html | API 문서 | X |
| /api/users/** | User Service 라우팅 | O |
| /api/products/** | Product Service 라우팅 | O |
| /api/orders/** | Order Service 라우팅 | O |

## 환경변수
| 변수 | 기본값 | 설명 |
|------|--------|------|
| JWT_SECRET | (기본값 있음, 운영 시 반드시 변경) | JWT 서명 키 |
| JWT_EXPIRATION_MS | 3600000 (1시간) | 토큰 만료 시간 |
| REDIS_HOST | localhost | Redis 호스트 |
| REDIS_PORT | 6379 | Redis 포트 |
| USER_SERVICE_URL | http://localhost:8081 | User Service URL |
| PRODUCT_SERVICE_URL | http://localhost:8082 | Product Service URL |
| ORDER_SERVICE_URL | http://localhost:8083 | Order Service URL |

## 운영 전 체크리스트
- [ ] JWT_SECRET 환경변수 교체 (하드코딩 기본값 사용 금지)
- [ ] Redis 기동 후 Rate Limiter 주석 해제 (application.yml:62)
- [ ] CORS allowedOriginPatterns 도메인 특정으로 변경
- [ ] Actuator 외부 노출 경로 health, info로 제한

## 코딩 컨벤션

### 네이밍
- 클래스명: `PascalCase` (예: `AuthenticationFilter`, `JwtTokenProvider`)
- 메서드/변수명: `camelCase` (예: `generateToken`, `userId`)
- 상수: `UPPER_SNAKE_CASE` (예: `BEARER_PREFIX`, `HEADER_USER_ID`)
- 패키지명: 소문자 단수형 (예: `filter`, `security`, `config`)
- 메서드명은 동사로 시작 (예: `generate`, `parse`, `validate`, `is`, `has`)

### 코드 구조
- 하나의 클래스/메서드는 하나의 책임만 가진다 (단일 책임 원칙)
- 메서드 길이는 30줄 이내 권장, 길어지면 분리
- 중복 코드는 공통 메서드/유틸로 추출
- 매직 넘버/문자열은 상수로 선언
- 인터페이스 기반으로 설계, 구현체에 직접 의존 금지

### 객체지향 원칙
- 공통 기능은 추상화하여 재사용 (상속보다 조합 우선)
- 외부에 노출할 필요 없는 필드/메서드는 `private` 처리
- 불변 객체 우선 사용 (`final`, `record` 활용)
- `null` 반환 대신 `Optional` 또는 빈 컬렉션 반환

### 공통화 처리
- 에러 응답 형식은 `GlobalExceptionHandler`에서 일괄 처리
- 공통 헤더(Correlation ID 등)는 Filter에서 일괄 처리
- 상수는 관련 클래스 내부 또는 별도 `Constants` 클래스에서 관리
- 설정값은 `@Value` 또는 `@ConfigurationProperties`로 중앙 관리

### 가독성
- 주석은 "무엇"이 아닌 "왜"를 설명 (코드 자체가 무엇인지 표현해야 함)
- 복잡한 로직에는 처리 흐름을 단계별로 주석 기재
- 메서드/클래스명만 봐도 역할을 알 수 있게 작성
- 조건문은 긍정 표현 우선, 부정 중첩 지양

### 패키지 구조
```
com.example.gateway
├── config      # Spring 설정 (Security, Gateway 등)
├── filter      # GlobalFilter (인증, 로깅, CorrelationId 등)
├── handler     # Controller, ExceptionHandler
└── security    # JWT 관련 클래스
```

### 테스트
- 단위 테스트는 외부 의존성 없이 작성 (Mock 활용)
- 테스트 메서드명은 한글 또는 `should_동작_when_조건` 형식
- 하나의 테스트는 하나의 케이스만 검증

## 보안 코딩 룰

### 인증/인가
- JWT Secret은 반드시 환경변수로 관리, 코드/설정 파일에 하드코딩 금지
- 토큰 만료시간은 최소한으로 설정 (Access Token 1시간 이하)
- 화이트리스트 경로는 `SecurityConfig`와 `AuthenticationFilter` 양쪽 모두 동기화 유지
- 다운스트림 서비스에 전달하는 `X-User-Id`, `X-User-Roles` 헤더는 클라이언트 입력값을 그대로 전달하지 말 것 (반드시 Gateway에서 JWT 파싱 후 주입)

### 입력값 검증
- 외부에서 들어오는 모든 입력값은 경계에서 검증
- 헤더, 쿼리 파라미터에 대한 길이 및 형식 검증 적용
- SQL Injection, XSS, Command Injection 방지를 위해 입력값을 그대로 로그에 출력 금지

### 시크릿 관리
- 비밀번호, API Key, 인증서 등 민감정보는 코드에 절대 포함 금지
- `.env` 파일은 `.gitignore`에 반드시 포함
- Git 커밋 전 민감정보 포함 여부 확인

### 통신 보안
- 운영 환경에서는 반드시 HTTPS 사용
- CORS `allowedOriginPatterns`는 운영 시 특정 도메인만 허용 (`*` 금지)
- 내부 서비스 간 통신도 가능하면 mTLS 적용 권장

### 로깅
- 토큰, 비밀번호, 개인정보(이름/이메일/주민번호 등)는 로그에 출력 금지
- 에러 로그에 스택트레이스를 클라이언트에 노출 금지 (내부 로그에만 기록)
- 모든 인증 실패 이벤트는 반드시 로깅

### 의존성
- 사용하지 않는 의존성 제거
- 주기적으로 `mvn dependency:check` 또는 Snyk 등으로 취약점 스캔
- 서드파티 라이브러리 버전 최신 보안 패치 유지

### 운영 환경
- `/auth/token` 엔드포인트는 `@Profile("!prod")`로 운영 비활성화 유지 (절대 운영 노출 금지)
- Actuator 엔드포인트는 `health`, `info`만 외부 노출, 나머지는 내부망 제한
- Rate Limiter 반드시 활성화하여 브루트포스 공격 방지

## Git 저장소
https://github.com/young-duk/product1
