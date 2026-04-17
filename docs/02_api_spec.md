# API Gateway — API 스펙 (인터페이스 명세)

## 1. 공통 사항

### Base URL
```
http://localhost:9080
```

### 공통 요청 헤더

| 헤더 | 필수 | 설명 |
|------|------|------|
| `Authorization` | 인증 필요 경로에서 필수 | `Bearer {JWT 토큰}` |
| `X-Correlation-Id` | 선택 | 요청 추적 ID (없으면 자동 생성) |
| `Content-Type` | POST/PUT/PATCH 시 필수 | `application/json` |

### 공통 응답 헤더

| 헤더 | 설명 |
|------|------|
| `X-Correlation-Id` | 요청 추적 ID (요청값 또는 자동 생성값) |
| `Content-Type` | `application/json` |

### 공통 에러 응답 형식

```json
{
  "timestamp": "2026-04-16T10:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "path": "/api/users/1"
}
```

### HTTP 상태 코드

| 코드 | 의미 |
|------|------|
| `200 OK` | 성공 |
| `400 Bad Request` | 잘못된 요청 |
| `401 Unauthorized` | 인증 토큰 없음 또는 유효하지 않음 |
| `403 Forbidden` | 권한 없음 |
| `429 Too Many Requests` | Rate Limit 초과 |
| `503 Service Unavailable` | 다운스트림 서비스 Circuit Open |

---

## 2. 게이트웨이 자체 엔드포인트 (인증 불필요)

### 2-1. Health Check

```
GET /actuator/health
```

**응답 예시 (200)**
```json
{
  "status": "UP",
  "components": {
    "redis": { "status": "UP" },
    "gateway": { "status": "UP" }
  }
}
```

---

### 2-2. Gateway 라우트 목록 조회

```
GET /actuator/gateway/routes
```

**응답 예시 (200)**
```json
[
  {
    "route_id": "user-service",
    "route_definition": {
      "id": "user-service",
      "predicates": [{ "name": "Path", "args": { "pattern": "/api/users/**" } }],
      "uri": "http://localhost:8081",
      "order": 0
    }
  }
]
```

---

### 2-3. Prometheus 메트릭

```
GET /actuator/prometheus
```

---

### 2-4. Swagger UI

```
GET /swagger-ui.html
GET /v3/api-docs
```

---

## 3. 프록시 라우트 — 다운스트림 전달 규칙

> 게이트웨이는 경로를 StripPrefix(1) 후 다운스트림으로 전달한다.
> 예: `GET /api/users/1` → User Service `GET /users/1`

### 3-1. User Service 라우트

| 항목 | 값 |
|------|-----|
| 게이트웨이 경로 | `/api/users/**` |
| 전달 URL | `http://localhost:8081` (env: `USER_SERVICE_URL`) |
| 전달 경로 | `/users/**` (StripPrefix=1) |
| Rate Limit | 초당 10 req, 버스트 20 req |
| Circuit Breaker | `userServiceCB` |
| Fallback | `GET /fallback/user-service` |

**요청 예시**
```http
GET /api/users/42
Authorization: Bearer eyJhbGci...
X-Correlation-Id: abc-123
```

**게이트웨이가 다운스트림에 추가하는 헤더**
```
X-User-Id: {JWT 클레임의 userId}
X-User-Roles: USER,ADMIN
X-Correlation-Id: abc-123
```

---

### 3-2. Product Service 라우트

| 항목 | 값 |
|------|-----|
| 게이트웨이 경로 | `/api/products/**` |
| 전달 URL | `http://localhost:8082` (env: `PRODUCT_SERVICE_URL`) |
| 전달 경로 | `/products/**` (StripPrefix=1) |
| Circuit Breaker | `productServiceCB` |
| Fallback | `GET /fallback/product-service` |

---

### 3-3. Order Service 라우트

| 항목 | 값 |
|------|-----|
| 게이트웨이 경로 | `/api/orders/**` |
| 전달 URL | `http://localhost:8083` (env: `ORDER_SERVICE_URL`) |
| 전달 경로 | `/orders/**` (StripPrefix=1) |
| Circuit Breaker | `orderServiceCB` |
| Fallback | `GET /fallback/order-service` |

---

## 4. Circuit Breaker Fallback

### 4-1. User Service Fallback

```
GET /fallback/user-service
```

**응답 (503)**
```json
{
  "timestamp": "2026-04-16T10:00:00Z",
  "status": 503,
  "error": "Service Unavailable",
  "path": "/fallback/user-service"
}
```

### 4-2. Product Service Fallback

```
GET /fallback/product-service
```

### 4-3. Order Service Fallback

```
GET /fallback/order-service
```

---

## 5. 인증 흐름 (시퀀스)

```
Client          Gateway         Auth 서비스(별도)    User Service
  │                │                  │                  │
  │ POST /auth/login│                  │                  │
  │──────────────►│                  │                  │
  │  (whitelist)   │                  │                  │
  │                │──── 프록시 ──────►│                  │
  │                │◄── JWT 응답 ──────│                  │
  │◄──────────────│                  │                  │
  │  {token}       │                  │                  │
  │                │                  │                  │
  │ GET /api/users/1│                  │                  │
  │  Authorization: Bearer {token}    │                  │
  │──────────────►│                  │                  │
  │                │ JWT 검증 (내부)   │                  │
  │                │ X-User-Id 주입   │                  │
  │                │──────────────────────────────────►│
  │                │◄──────────────────────────────────│
  │◄──────────────│                  │                  │
```
