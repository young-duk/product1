# API Gateway — 요구사항 정의서 (Requirements Specification)

## 1. 프로젝트 개요

| 항목 | 내용 |
|------|------|
| 프로젝트명 | api-gateway |
| 목적 | MSA 환경의 단일 진입점 제공 |
| 플랫폼 | JDK 21 + Spring Boot 3.2.4 + Spring Cloud 2023.0.1 |
| 포트 | 9080 |

---

## 2. 기능 요구사항 (Functional Requirements)

### FR-01. 라우팅

| ID | 요구사항 | 우선순위 |
|----|----------|----------|
| FR-01-1 | Path Predicate 기반으로 요청을 다운스트림 서비스로 전달한다 | 필수 |
| FR-01-2 | `/api/users/**` 요청은 User Service로 전달한다 | 필수 |
| FR-01-3 | `/api/products/**` 요청은 Product Service로 전달한다 | 필수 |
| FR-01-4 | `/api/orders/**` 요청은 Order Service로 전달한다 | 필수 |
| FR-01-5 | StripPrefix(1)로 `/api` 접두사를 제거 후 전달한다 | 필수 |

### FR-02. 인증

| ID | 요구사항 | 우선순위 |
|----|----------|----------|
| FR-02-1 | JWT Bearer 토큰을 검증하여 인증 처리한다 | 필수 |
| FR-02-2 | 토큰 없거나 유효하지 않으면 HTTP 401을 반환한다 | 필수 |
| FR-02-3 | 인증 성공 시 userId, roles를 다운스트림 헤더로 전달한다 | 필수 |
| FR-02-4 | 화이트리스트 경로는 인증 없이 통과한다 | 필수 |

### FR-03. 인가

| ID | 요구사항 | 우선순위 |
|----|----------|----------|
| FR-03-1 | 화이트리스트 외 모든 경로는 인증된 사용자만 접근 가능하다 | 필수 |
| FR-03-2 | 권한 없는 접근 시 HTTP 403을 반환한다 | 필수 |

### FR-04. Rate Limiting

| ID | 요구사항 | 우선순위 |
|----|----------|----------|
| FR-04-1 | Redis 기반 Token Bucket으로 Rate Limiting을 적용한다 | 필수 |
| FR-04-2 | 기본 초당 10 req, 버스트 20 req를 허용한다 | 필수 |
| FR-04-3 | 초과 시 HTTP 429를 반환한다 | 필수 |
| FR-04-4 | 인증된 사용자는 userId 기준, 미인증은 IP 기준으로 제한한다 | 필수 |

### FR-05. Circuit Breaker

| ID | 요구사항 | 우선순위 |
|----|----------|----------|
| FR-05-1 | 다운스트림 서비스 장애 시 Circuit을 Open한다 | 필수 |
| FR-05-2 | Circuit Open 시 Fallback 응답(503)을 반환한다 | 필수 |
| FR-05-3 | 실패율 50% 초과 시 회로를 개방한다 | 필수 |
| FR-05-4 | 10초 후 Half-Open 상태로 전환하여 복구를 시도한다 | 필수 |

### FR-06. 요청 추적

| ID | 요구사항 | 우선순위 |
|----|----------|----------|
| FR-06-1 | 모든 요청에 X-Correlation-Id를 부여한다 | 필수 |
| FR-06-2 | 클라이언트가 전달한 X-Correlation-Id가 있으면 그 값을 사용한다 | 필수 |
| FR-06-3 | X-Correlation-Id를 응답 헤더에 포함한다 | 필수 |

### FR-07. 로깅

| ID | 요구사항 | 우선순위 |
|----|----------|----------|
| FR-07-1 | 요청 시 메서드, 경로, Correlation-Id를 기록한다 | 필수 |
| FR-07-2 | 응답 시 상태 코드, 소요 시간을 기록한다 | 필수 |
| FR-07-3 | JWT 토큰 등 민감 정보는 로그에 기록하지 않는다 | 필수 |

### FR-08. 모니터링

| ID | 요구사항 | 우선순위 |
|----|----------|----------|
| FR-08-1 | `/actuator/health` 엔드포인트를 제공한다 | 필수 |
| FR-08-2 | Prometheus 메트릭을 `/actuator/prometheus`로 노출한다 | 권장 |
| FR-08-3 | 게이트웨이 라우트 정보를 `/actuator/gateway/routes`로 조회 가능하다 | 권장 |

### FR-09. API 문서화

| ID | 요구사항 | 우선순위 |
|----|----------|----------|
| FR-09-1 | Swagger UI를 `/swagger-ui.html`로 제공한다 | 권장 |
| FR-09-2 | OpenAPI 스펙을 `/v3/api-docs`로 제공한다 | 권장 |

---

## 3. 비기능 요구사항 (Non-Functional Requirements)

### NFR-01. 성능

| ID | 요구사항 |
|----|----------|
| NFR-01-1 | 단일 인스턴스 기준 1,000 RPS 이상 처리 가능해야 한다 |
| NFR-01-2 | 평균 응답 지연(latency)은 다운스트림 응답 시간 + 5ms 이내여야 한다 |

### NFR-02. 보안

| ID | 요구사항 |
|----|----------|
| NFR-02-1 | JWT Secret은 환경 변수로 주입하고 코드에 하드코딩하지 않는다 |
| NFR-02-2 | HTTPS를 사용해야 한다 (TLS Termination은 앞단 Load Balancer 또는 Ingress에서 처리) |
| NFR-02-3 | 에러 응답에 내부 스택 트레이스를 노출하지 않는다 |

### NFR-03. 가용성

| ID | 요구사항 |
|----|----------|
| NFR-03-1 | 다운스트림 서비스 장애 시 게이트웨이 자체는 정상 동작해야 한다 (Circuit Breaker) |
| NFR-03-2 | Redis 장애 시 Rate Limiting이 일시 중단되어도 라우팅은 계속되어야 한다 |

### NFR-04. 유지보수성

| ID | 요구사항 |
|----|----------|
| NFR-04-1 | 새로운 서비스 라우트 추가 시 `application.yml` 수정만으로 가능해야 한다 |
| NFR-04-2 | 단위 테스트 커버리지 70% 이상을 유지한다 |

---

## 4. 제약사항

| 항목 | 제약 |
|------|------|
| Java 버전 | JDK 21 이상 |
| Spring Boot | 3.2.x 이상 (Jakarta EE 10 기반) |
| Redis | Rate Limiting 기능 사용 시 Redis 6 이상 필요 |
| OS | Linux, macOS, Windows (개발 환경) |

---

## 5. 용어 정의

| 용어 | 정의 |
|------|------|
| Gateway | 클라이언트와 마이크로서비스 사이의 단일 진입점 |
| JWT | JSON Web Token — 서명된 클레임 기반 인증 토큰 |
| Rate Limiting | 단위 시간당 요청 횟수 제한 |
| Circuit Breaker | 다운스트림 장애 전파를 차단하는 패턴 |
| Correlation ID | 분산 시스템에서 요청 추적에 사용하는 고유 ID |
| Whitelist | 인증 없이 접근 허용하는 경로 목록 |
| Downstream | 게이트웨이 뒤쪽 마이크로서비스 |
| Fallback | Circuit Open 시 반환하는 대체 응답 |
