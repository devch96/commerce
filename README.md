# copa — AI 기반 MSA 이커머스 플랫폼

스파르타 MSA 트랙 Final 개별 프로젝트. 회원·상품·재고·주문·결제·쿠폰·예매를 **독립 실행되는 마이크로서비스**로 나누고,
게이트웨이 JWT 인증, 동기 Saga 오케스트레이션(실 PG 결제창 연동), Transactional Outbox → Kafka 이벤트,
Redis 선착순 쿠폰·가상 대기열 예매, Elasticsearch 검색, ELK·Zipkin·Prometheus/Grafana 관측성까지 갖춘 커머스 백엔드입니다.

## 🗺️ 아키텍처 개요

```
                        ┌──────────────────────┐
  Client ──────────────▶│  copa-gateway :8000  │  JWT 검증 → 서명 신원 토큰(X-Copa-Identity) 발급
                        │  (Spring Cloud GW)   │  화이트리스트(비로그인 공개) 지원
                        └──────────┬───────────┘
      ┌────────────┬───────────────┼───────────────┬────────────┬─────────────┐
      ▼            ▼               ▼               ▼            ▼             ▼
 copa-user    copa-product     copa-order     copa-payment  copa-coupon  copa-ticket
   :8081         :8082           :8085           :8084         :8086        :8087
 (회원·인증)  (상품·카테고리·  (동기 Saga       (토스·카카오    (쿠폰·선착순   (선착순 예매
              장바구니·검색)   오케스트레이터)   실 PG 연동)     FCFS)      대기열·발권)
                   │               │ /internal/** (Feign, 게이트웨이 미경유)
                   │               ├──────────▶ copa-inventory :8083 (재고 예약/확정/해제)
                   │               ├──────────▶ copa-payment / copa-coupon
                   ▼               │
             [Kafka :9092] ◀───────┘
              ├ product-events         : 상품 생성 → 재고 시드 (outbox 릴레이 발행)
              ├ product-search-events  : 상품 변경 → ES 색인기(upsert/delete)
              ├ coupon-issued          : 선착순 통과분 → 쿠폰 DB 반영
              └ ticket-issued          : 발권 통과분 → 예매 DB 반영
                   │
                   ▼
            [Elasticsearch :9200] ← 상품 전문 검색 색인(products) + ELK 로그 겸용
```

- **서비스별 DB 분리**(MySQL 8.0 × 7) + 목적별 Redis 4대(인증 세션 / 상품 캐시 / 선착순 쿠폰 / 예매 대기열·발권).
- 서비스 간 동기 호출은 **OpenFeign**(어댑터에서 공통 봉투 언랩·예외 변환), 비동기는 **Transactional Outbox → Kafka**.
- `/internal/**`은 게이트웨이를 거치지 않는 서비스 간 내부 API.

## 📦 서비스 구성

| 서비스 | 포트 | 역할 | 저장소 |
|--------|------|------|--------|
| `copa-gateway` | 8000 | WebFlux 게이트웨이. JWT 검증 → 서명 신원 토큰(X-Copa-Identity) 발급, 신뢰 헤더 스트리핑, `METHOD path` 화이트리스트 | — |
| `copa-user` | 8081 | 회원가입/로그인/재발급(RTR)·프로필·주소. `auth`/`user` 패키지 분리 | MySQL(3306) + Redis(6379, refresh 토큰) |
| `copa-product` | 8082 | 상품 CRUD(soft delete)·무한 뎁스 옵션 트리·옵션/조합 할인(최장 일치)·카테고리·장바구니·검색 | MySQL(3307) + Redis(6380, 캐시) + ES |
| `copa-inventory` | 8083 | 옵션(optionKey)별 재고의 권위 원천. reserve→confirm/release 모델, 비관적 락, TTL 자동 해제 | MySQL(3308) |
| `copa-payment` | 8084 | 실 PG(토스·카카오) 결제창 연동. ready(tid)/confirm/cancel, orderNo 유니크 멱등 | MySQL(3309) |
| `copa-order` | 8085 | 주문 + **동기 2단계 Saga 오케스트레이터**. 가격 스냅샷→예약→결제→확정/보상 | MySQL(3310) |
| `copa-coupon` | 8086 | 쿠폰 정의/발급(1인 1매)·주문 연동(reserve→use/release/restore)·**Redis 선착순 발급** | MySQL(3311) + Redis(6381) |
| `copa-ticket` | 8087 | 대규모 트래픽 **선착순 예매**. 가상 대기열(ZSET)→입장 허가→Lua 원자 발권→Kafka 비동기 DB 반영 | MySQL(3312) + Redis(6382) |

공통: Spring Boot 3.5.14 · Java 21 · JPA(Hibernate) + **Flyway**(스키마는 마이그레이션으로만, `ddl-auto: none`) · jjwt 0.12.6.

## 🧩 핵심 설계

### 주문 — PG 결제창 방식 동기 2단계 Saga
```
Phase 1  POST /orders
  상품 option-price로 가격 스냅샷 → 주문 생성(PENDING_PAYMENT, pgProvider 저장)
  → 쿠폰 reserve(할인 계산) → 재고 reserve → (카카오) ready로 결제창 URL 발급
Phase 2  POST /orders/{orderNo}/payment/confirm   (PG 리다이렉트 후)
  저장된 pgProvider 대조(교차 PG 차단)·토큰 검증 → PG 승인(금액은 서버 payable만 신뢰)
  → 성공: 재고 confirm + 쿠폰 use + PAYMENT_COMPLETED (roll-forward)
  → 확정 거절(4xx): 재고·쿠폰 release + CANCELLED (보상)
  → 결과 불확실(5xx/타임아웃): 보상하지 않고 PENDING 유지(재시도 가능, 승인됐을 수 있으므로)
```
- 모든 참여자(재고·쿠폰·결제)의 reserve/confirm/release/restore는 **orderNo 기준 멱등**.
- 미결제 예약은 재고 TTL 스케줄러가 자동 해제. TTL 해제 후 승인이 오면 confirm이 **명시 실패**(오버셀 0).
- **주문 외부 식별자는 `orderNo`**(`ORD-yyyyMMdd-XXXXXX`) — 순차 PK 열거 방지. 내부 PK는 FK 전용.

### 재고 — 오버셀링 0
- "결제 전 예약 → 결제 후 확정 / 실패 시 해제". 예약 핫패스는 **비관적 락**(+`@Version`), 품목은 정렬 후 잠가 데드락 방지.
- 재고 시드는 상품 생성 이벤트(`product-events`) 구독으로 옵션 leaf별 `seedIfAbsent`(멱등).

### 선착순 쿠폰 — Redis Lua + Kafka (설계 08-B)
```
관리자 open(재고 시드, 기본 1000) → 사용자 issue-fcfs
→ Lua 원자 실행 [1인1매 SISMEMBER + 재고 DECR + SADD]   ← 초과·중복 발급 0
→ 통과분만 Kafka coupon-issued 발행(실패 시 Redis 보상: SREM+INCR)
→ 컨슈머가 user_coupons 멱등 INSERT (선존재 검사 + (coupon_id,user_id) 유니크 2차 방어)
```
- Redis가 수량 통제의 source of truth, DB는 최종 일관성. 일반 발급(`/issue`, DB 비관적 락)과 경로 분리.

### 선착순 예매 — 가상 대기열 + Lua 원자 발권 (설계 12 · 08-C)
```
관리자 open(좌석 Redis 시드) → 대기열 진입(ZSET, score=진입시각) → 순번 폴링
→ 입장 스케줄러가 1초마다 상위 N명 pop + 입장 허가 키 SETEX (queue_admit.lua)
→ 발권: Lua 원자 실행 [입장 검증 + 1인1매 + 좌석 차감 + 입장권 소모] (ticket_issue.lua)
→ 통과분만 Kafka ticket-issued 발행(실패 시 Redis 보상: SREM+INCR+입장권 재부여)
→ 컨슈머가 tickets 멱등 INSERT ((event_id,user_id) 유니크 = 1인 1매 최종 방어선)
```
- 선착순 쿠폰의 "Lua 원자 연산 + Kafka 비동기 DB 반영" 골격에 **가상 대기열**을 얹어 유량 제어까지 수행.
- 입장 허가는 별도 토큰 값 없이 **인증된 userId 기반 키 존재**(`ticket:{id}:entry:{uid}`)로 판정 — 탈취·공유 불가.
- 외부 식별자 `ticketNo`(`TKT-yyyyMMdd-XXXXXX`). 취소는 DB 전이(중복 취소 409) 후 좌석 Redis 복원.

### 상품 검색 — 2경로
- **QueryDSL 동적 검색**(`GET /products/search`): keyword·가격범위·카테고리(하위 트리 펼침)·정렬 화이트리스트. MySQL 정형 필터.
- **Elasticsearch 전문 검색**(`/search/es`, 집계 `/es/aggregations`): multi_match+fuzziness relevance, 가격/카테고리 집계.
- 색인은 요청 경로와 분리: outbox → `product-search-events` → 색인기(`@KafkaListener`)가 멱등 upsert/delete.

### 이벤트 — Transactional Outbox
- 도메인 변경과 **같은 트랜잭션**에 outbox 행 적재 → 릴레이 스케줄러가 Kafka 발행(at-least-once) → 소비자는 전부 멱등.

### 캐싱
- 상품 상세: Redis Look-Aside(TTL 10분, 변경 시 커밋 후 evict).
- 카테고리 트리: 전체 스냅샷 단일 키(`categories:all`) Look-Aside — 상품 목록/검색 핫패스의 반복 findAll 제거.

### 보안·견고성
- **서비스 간 신뢰(내부 위조 방어)** — 신뢰의 근거를 "네트워크 위치"에서 "서명 검증"으로 이동. 게이트웨이가 JWT 검증 후
  `uid`·`role`을 담은 **서명 신원 토큰**(`X-Copa-Identity`, HS256, TTL 60s)을 발급하고, 각 서비스의 `InternalAuthFilter`가
  이를 검증한 값에서만 `X-User-Id`/`X-User-Role`을 재구성한다(클라이언트가 붙인 원시 헤더는 무조건 제거 → 위조 불가).
  게이트웨이를 거치지 않는 `/internal/**`은 호출자(order)가 붙인 **서비스 토큰**(`X-Copa-Service`)을 검증해 무단 호출을 차단.
  docker-compose는 앱 서비스 포트를 호스트에 노출하지 않고(`expose`) **게이트웨이(8000)만** 외부 오픈. 자세한 내용은 [docs/WEEK_17/WEEK_17_6일차.md](docs/WEEK_17/WEEK_17_6일차.md).
- 결제 조회 등 리소스 접근은 소유자 검증(IDOR 방어). 금액은 `BigDecimal`, PG 승인 금액은 서버 저장값만 신뢰.
- 전 서비스 공통 예외 봉투(잘못된 JSON/enum·타입 미스매치·헤더 누락·500 포함), 검색 파라미터 검증(음수·역전 400).

## 🚀 실행 방법

```bash
# 1. 인프라 + 전체 서비스 (Docker)
docker compose up -d          # MySQL×7, Redis×4, Kafka, ELK, Zipkin, Prometheus/Grafana + 8개 서비스

# 2. 개별 서비스 로컬 실행 (인프라만 Docker로 띄운 뒤)
cd copa-user && ./gradlew bootRun

# 테스트 (모듈별)
cd copa-order && ./gradlew test
```

- 진입점: `http://localhost:8000` (게이트웨이). 비로그인 공개: `/auth/**`, `GET /products/**`, `GET /categories/**`,
  예매 이벤트 목록/상세(`GET /events`, `GET /events/*` — 대기열·발권은 인증 필요).
- 관측성: Grafana `:3000`(대시보드 3종 프로비저닝) · Kibana `:5601` · Zipkin `:9411` · Prometheus `:9090`.
- 테스트는 H2 + `flyway.enabled=false`(MySQL 전용 마이그레이션 회피), Kafka 리스너·ES 색인기는 프로퍼티로 비활성.

## 📁 프로젝트 구조

```
├── copa-gateway / copa-user / copa-product / copa-inventory
├── copa-payment / copa-order / copa-coupon / copa-ticket   # 각자 독립 Gradle 프로젝트 (멀티 모듈 아님)
├── docker-compose.yml                          # 인프라 + 앱 + 관측성 통합
├── observability/                              # Prometheus·Grafana·Logstash 설정/대시보드
├── docs/WEEK_14~17/                            # 일차별 작업 기록
├── docs/FOLLOW_UP/                             # 커리큘럼 종료 후 후속 작업 기록
└── CLAUDE.md                                   # 컨벤션·설계 규약 (AI 세션용 컨텍스트 포함)
```

각 서비스 내부는 `controller → service → repository → domain` 레이어, DTO는 record 대신 Lombok 불변 클래스,
`@Transactional`은 오케스트레이터와 분리된 커맨드/쿼리 빈에 둡니다(자기호출 프록시 우회 방지). 상세 규약은 `CLAUDE.md` 참고.

## 🛠️ 기술 스택

| 분류 | 스택 |
|------|------|
| 언어/프레임워크 | Java 21, Spring Boot 3.5.14, Spring Cloud Gateway (WebFlux) |
| 데이터 | MySQL 8.0 (서비스별 분리), Flyway, JPA/Hibernate, QueryDSL |
| 캐시/원자연산 | Redis 7.2 ×4 (세션·캐시·선착순 쿠폰 Lua·예매 대기열/발권 Lua) |
| 메시징 | Apache Kafka (KRaft 단일 노드), Transactional Outbox 패턴 |
| 검색 | Elasticsearch 8.x (전문 검색·집계, ELK 겸용) |
| 서비스 간 통신 | OpenFeign (+ 어댑터 계층), 내부 API `/internal/**` |
| 인증 | JWT (jjwt), Refresh Token Rotation, 게이트웨이 중앙 검증 |
| 결제 | 토스페이먼츠·카카오페이 결제창(redirect) 연동 |
| 관측성 | ELK(JSON 로그), Micrometer→Zipkin 트레이싱, Prometheus + Grafana |

