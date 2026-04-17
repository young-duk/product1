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

## Git 저장소
https://github.com/young-duk/product1
