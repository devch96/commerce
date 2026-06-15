# 14주차 1일차 — 프로젝트 초기 구성 & API 게이트웨이

> AI 기반 MSA 이커머스 플랫폼 `copa` / 풀 MSA 구성으로 시작
> 관련 커밋: `[1일차] 프로젝트 초기 구성 및 API 게이트웨이 구현`

## 🎯 목표

- 멀티 서비스 프로젝트 골격과 공통 인프라를 세팅한다.
- 모든 서비스의 진입점이 될 API 게이트웨이(인증·라우팅)를 완성한다.
- 코딩 컨벤션과 빌드/실행 가이드를 문서로 고정한다.

## 🛠️ 작업 내용

### 1. 프로젝트 스캐폴딩
- 독립 실행되는 3개 서비스 디렉토리 구성 (각자 별도 Gradle 프로젝트)
  - `copa-gateway` — Spring Cloud Gateway (WebFlux)
  - `copa-user` — 회원·인증 서비스
  - `copa-product` — 상품 서비스 (골격)
- 공통: Spring Boot 3.5.14, Java 21 (toolchain)
- 루트 `.gitignore` 추가 (`.idea/`, `build/`, `.gradle/` 등)

### 2. 공통 인프라 (docker-compose)
- `copa-auth-mysql` (MySQL 8.0, 3306) — 회원·인증용
- `copa-auth-redis` (Redis 7.2, 6379) — Refresh Token 저장·무효화용
- Kafka(주문 Saga)는 15주차에 추가 예정

### 3. API 게이트웨이 (copa-gateway)
- **라우팅**: `/auth/**`, `/users/**` → `copa-user`
- **JWT 인증 필터** (`JwtAuthenticationFilter`, GlobalFilter)
  - 화이트리스트 경로(`/auth/signup`, `/auth/login`, `/auth/reissue`)는 토큰 없이 통과
  - 토큰 서명·만료 검증 후 `X-User-Id`, `X-User-Role` 헤더를 주입해 하위 서비스로 전달 (무상태)
  - 클라이언트가 임의 주입한 신뢰 헤더(`X-User-*`)는 진입 시 제거 → **스푸핑 방지**
- CORS 전역 설정

### 4. 컨벤션 / 가이드 문서
- `CLAUDE.md` — 모듈 구성, 빌드/실행, DB 스키마 관리(Flyway) 가이드
- `.clauderules` — Spring Boot 3.x + Java 21 코딩 컨벤션 (레이어 구조, Lombok 제약, JPA 규약, 커머스 도메인 규칙)

## 🧭 주요 설계 결정

- **무상태 JWT + 게이트웨이 1차 인증**: 게이트웨이가 서명만 검증하면 되므로 매 요청마다 인증 서비스를 조회할 필요가 없다. 결합도↓, 확장성↑.
- **신뢰 헤더 정화(sanitize)**: 인증 결과를 헤더로 전달하므로, 외부에서 위조한 `X-User-*` 헤더를 게이트웨이에서 반드시 제거한다.
- **단일 거대 커밋 대신 관심사별 분리**: 인프라·게이트웨이를 1일차로, 회원 서비스를 2일차로 끊어 읽기 좋은 히스토리 유지.

## 📦 결과물

- 커밋: `[1일차] 프로젝트 초기 구성 및 API 게이트웨이 구현`
- 게이트웨이 인증 필터 단위 테스트 (화이트리스트 통과 / 헤더 주입 / 401 / 스푸핑 방지)

## ⏭️ 다음 (2일차)

- 회원·인증 서비스(copa-user) 구현: 가입/로그인/토큰 + 배송지 관리
