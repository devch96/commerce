# 17주차 5일차 — Redis 선착순 쿠폰 · 카테고리 캐싱 · 전면 점검(orderNo 전환)

> 세 갈래 작업. (1) 설계 08-B의 **Redis 선착순 쿠폰**(Lua 원자 발급 + Kafka 비동기 DB 반영)을 구현하고,
> (2) 프로젝트 전반의 캐싱 후보를 훑어 **카테고리 트리 Redis Look-Aside 캐시**를 적용했으며,
> (3) 전 서비스 점검으로 보안·정합성 버그 4건을 수정하고 **주문 외부 식별자를 orderNo(String)로 전환**했다.

## 🎯 목표

- **선착순 쿠폰(FCFS)** — 한정 수량(임시 1000장)·1인 1매·초과 발급 0을 DB 락 없이 Redis 원자연산으로 통제.
- **캐싱** — 매 요청 `findAll()`을 치던 카테고리 트리 조회(공개 상품 목록/검색 핫패스)를 캐시로 대체.
- **점검·수정** — 입력 검증/Saga/보안 관점의 전면 리뷰와 발견 문제 수정, orderId 열거 방지(orderNo).

## 🛠️ 작업 내용

### 1. Redis 선착순 쿠폰 (copa-coupon, 설계 08-B)
- **Lua 원자 발급**(`redis/coupon_issue.lua`): `[미오픈 체크 → SISMEMBER 1인1매 → GET/DECR 재고 → SADD]`를
  단일 스크립트로 실행. 반환 코드 `1`(성공)/`-1`(품절)/`-2`(중복)/`-3`(미오픈)로 분기.
- **발급 흐름**: 관리자 `POST /admin/coupons/{id}/fcfs/open`(재고 시드, 기본 1000 = `copa.coupon.fcfs.default-quantity`)
  → 사용자 `POST /coupons/{id}/issue-fcfs` → Lua 통과분만 **Kafka `coupon-issued`** 발행(동기 대기)
  → 컨슈머(`CouponIssuedConsumer`)가 `user_coupons`에 멱등 INSERT(선존재 검사 + `(coupon_id,user_id)` 유니크 2차 방어).
- **발행 실패 보상**: Kafka 발행이 실패하면 Redis 효과를 되돌린다(SREM + INCR) — 발급 미확정 상태로 재시도 가능.
  응답은 `ACCEPTED`(DB 반영은 비동기, 보유 확인은 `/coupons/me`).
- **재오픈 정책**: 재고 키만 덮어쓰고 발급자 집합은 보존 → 재오픈으로 인한 중복 발급 차단.
- 인프라: `copa-coupon-redis`(6381→6379) 신설, coupon에 Redis/Kafka 의존성·설정 추가. 테스트 프로파일은 컨슈머 비활성.
- 단위 테스트 5건(성공 발행·미오픈·중복·품절·발행 실패 보상).

### 2. 카테고리 트리 Redis Look-Aside 캐시 (copa-product)
- `getTree()`·`collectSubtreeIds()`가 **매 호출 `findAll()`** → 공개 상품 목록/검색 트래픽 전부가 카테고리 전량 로드.
- `CategorySnapshot`(id·name·parentId 경량 DTO)을 `categories:all` **단일 키**에 JSON 배열로 캐시(TTL 30분 안전망).
  기존 `ProductCacheService`와 동일 관례(직렬화 실패는 미스로 흡수 — 가용성 우선).
- 관리자 create/update/delete는 **커밋 후 evict**(`TransactionSynchronization` — 롤백 시 오무효화 방지).

### 3. 전면 점검에서 발견·수정한 버그 4건
| # | 심각도 | 문제 | 수정 |
|---|--------|------|------|
| 1 | 🔴 보안 | `GET /payments/{orderId}`가 소유권 검증 없이 타인 결제 노출(**IDOR**, orderId가 순차 Long) | `X-User-Id` 대조, 불일치 403 |
| 2 | 🔴 정합성 | 재고 예약 TTL(5분) 해제 후 결제 승인 시 `confirm()`이 **조용히 no-op** → 재고 차감 없이 주문 완료(오버셀) | 살아있는 예약(RESERVED/CONFIRMED) 없으면 `RESERVATION_EXPIRED`(409)로 명시 실패 → Saga가 복구 경로로 |
| 3 | 🔴 정합성 | PG 승인 호출이 **타임아웃/5xx**여도 무조건 보상(재고 해제+주문 취소) → 승인은 성공했는데 주문만 취소될 수 있음 | 4xx(확정 거절)만 보상, 불확실은 PENDING_PAYMENT 유지(재시도 가능·TTL 안전망) |
| 4 | 🟡 견고성 | Phase1이 `BusinessException`만 잡아 예기치 못한 예외 시 보상 누락 | `RuntimeException`으로 확대 |

### 4. orderNo(String) 도입 — 주문 외부 식별자 전환
- **내부 PK(Long)는 유지**하고 외부 노출용 `orderNo`(`ORD-yyyyMMdd-XXXXXX`, 혼동 문자 제외 30자 알파벳 SecureRandom 6자) 신설.
- 클라이언트 API 경로(`/orders/{orderNo}/**`)·응답 DTO·서비스 간 Saga 참조(재고·쿠폰·결제) 전부 orderNo로 통일.
- 마이그레이션: order V2(`order_no` 백필+유니크, `pg_provider`), inventory V3·coupon V2·payment V3(order 참조 컬럼 VARCHAR(30)).
- **잠복 버그 발견**: `payments.order_id`가 DB는 BIGINT인데 엔티티는 String — 숫자 문자열일 땐 암묵 변환으로 동작했지만
  `ORD-...` 저장 시 깨질 상태였음 → V3로 교정.

### 5. 나머지 개선
- **pgProvider 저장+검증**: Phase1에서 선택 PG를 주문에 저장 → Phase2 confirm에서 대조(교차 PG 확정 차단),
  provider별 필수 토큰(토스=paymentKey·카카오=pgToken) 누락 시 PG 호출 전 400(`INVALID_PG_REQUEST`).
- **GlobalExceptionHandler 보강(6개 서비스)**: 깨진 JSON/잘못된 enum(`HttpMessageNotReadable`)·타입 미스매치·
  필수 헤더 누락·미처리 예외(500)를 공통 봉투로. 기존 catch-all 보유 서비스(user·payment·coupon)는 중복 없이 병합.
- **검색 조건 검증**: 음수 가격·min>max → 400(`INVALID_SEARCH_CONDITION`, QueryDSL·ES 동일 정책).
  ES 정렬은 `price`/`createdAt` 화이트리스트(미허용 필드 무시 → ES 500 방지).
- **getMyOrders 페이징**: `List` → `Page` + `@PageableDefault(size=20, createdAt DESC)`.

## 🧭 주요 설계 결정

- **선착순 DB 반영은 Kafka 비동기**(설계 08 정석): Redis가 수량·1인1매의 source of truth, DB는 최종 일관성.
  발급 응답이 Redis 확정 즉시 반환돼 처리량 최대 — 대신 "발급됐지만 DB 반영은 잠시 후" 상태를 UI가 감안(`ACCEPTED`).
- **트랜잭션 내 유니크 위반 catch-재시도 금지**: Hibernate가 rollback-only로 마킹해 커밋이 깨진다(`UnexpectedRollbackException`).
  → FCFS 컨슈머는 선존재 검사 멱등 + 위반 시 롤백→재배달 흡수, orderNo 생성은 재시도 없이 DB 유니크를 최종 방어선으로(일 7억+ 조합).
- **orderNo는 PK 교체가 아닌 별도 컬럼**: UUID PK 교체는 FK 연쇄·InnoDB 인덱스 비용이 크다. 외부 식별자 분리가 업계 표준 패턴.
- **PG 승인 실패는 "확정 거절"과 "결과 불확실"을 구분**: 불확실에 보상하면 돈만 나가고 주문이 취소되는 최악 경로.
- **재고 confirm의 멱등과 침묵은 다르다**: "이미 CONFIRMED → no-op"은 멱등이지만, "예약 흔적 없음 → no-op"은 오버셀. 후자는 실패해야 한다.

## ⚠️ 남긴 것 / 알려진 트레이드오프

- **프론트 breaking change**: 주문 생성 응답 `orderId`(Long) → `orderNo`(String), 이후 경로 전부 `/orders/{orderNo}/...`.
- FCFS **Redis↔DB 대사(reconciliation) 배치 미구현**(설계 08-F): at-least-once 재배달로 수렴하지만 이벤트 종료 후 정합성 대사는 후속.
- 발행 실패 보상까지 실패하면 재고 1장이 잠길 수 있음(오버셀은 아님) — 대사로 정정.
- `RESERVATION_EXPIRED` 경로의 주문(결제 캡처됨·PENDING_PAYMENT 잔류)은 수동/후속 복구 대상.

## ⏭️ 다음

- FCFS 부하 테스트(k6/Gatling)로 초과 발급 0 검증, Redis↔DB 대사 배치.
- 설계 08-C 가상 대기열(ZSET waitroom), 플래시세일 재고 연계(08-D).
- `ORDER_COMPLETION_FAILED`/`RESERVATION_EXPIRED` 복구 자동화(재처리 스케줄러 또는 Kafka 재시도).
