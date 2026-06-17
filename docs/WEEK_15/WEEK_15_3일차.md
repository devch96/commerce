# 15주차 3일차 — 전체 서비스 중간 점검 & 안티패턴 픽스

> 게이트웨이→유저→상품→재고→주문→결제 순으로 코드를 점검해 안티패턴·`.clauderules` 위반을 찾고, 그 자리에서 픽스. 6개 서비스 **81 테스트 전부 통과**.

## 🎯 목표

- 6개 서비스의 흐름을 재정리하고, 시니어 관점의 안티패턴과 `.clauderules`/CLAUDE.md 위반을 점검한다.
- 점검에서 나온 🔴/🟠 항목을 같은 날 픽스하고, 변경이 기존 테스트를 깨지 않음을 확인한다.

## 🛠️ 작업 내용

### 1. 게이트웨이 — 빌드 깨짐 수정
- `JwtAuthenticationFilterTest`의 `GatewayProperties` 생성자를 5인자(order·payment URI 포함)로 정합. order/payment 라우팅 추가 커밋 이후 미갱신이던 테스트 복구. (`JwtAuthenticationFilterTest.java`)

### 2. 주문 — Saga roll-forward 분리 (핵심 변경)
- **결제 승인을 보상/전진 경계로 분리** (`OrderService.createOrder`):
  - `reserve → pay`(승인)까지만 `try`(보상 가능 구간). 실패 시 `compensate()` = 재고 `release` + 주문 `CANCELLED`. 결제 미승인이라 환불 없음.
  - 결제 승인 후 `confirm` + `markPaymentCompleted`는 `completePaidOrder()`로 분리해 **roll-forward**: `confirmWithRetry()`(멱등, `CONFIRM_MAX_ATTEMPTS=3`) 후 완료. 끝내 실패하면 `ORDER_COMPLETION_FAILED`로 드러내되 **환불·해제는 하지 않고** 주문을 `ORDER_PLACED`로 남겨 후속 복구 대상으로.
- **Feign 타임아웃 명시**: `feign.client.config.default.connectTimeout 2000 / readTimeout 5000`(기본 60s 제거). (`application.yaml`)
- **사용자 취소 일관화**: 환불은 하드콜, 재고 `restore`·주문 마감은 `safe()` 멱등 처리.
- `ErrorCode.ORDER_COMPLETION_FAILED`(500) 추가.

### 3. 재고 — 락 정렬 + 멱등 강화
- **데드락 회피**: `reserve`/`release`/`restore` 모두 락 획득 전 `(productId, optionKey)`로 정렬 → 항상 동일 락 순서(`ITEM_LOCK_ORDER`/`RESERVATION_LOCK_ORDER`).
- **confirm/release 상호 배타**: 예약 행을 `findForUpdateByOrderIdAndStatus`(PESSIMISTIC_WRITE)로 잠가, 결제 confirm과 TTL release(다중 인스턴스 포함)가 같은 예약을 동시에 처리하는 경합·재고 이중 복원을 차단.
- **DB 멱등 가드**: `V2__stock_reservation_order_unique.sql` — `uk_stock_reservation_order_product_option(order_id, product_id, option_key)` 유니크로 동시 중복 예약의 이중 차감 최종 방어.

### 4. 회원 — 보안 픽스
- **reissue 비활성 차단**: 탈퇴/비활성 계정은 Refresh Token 폐기 후 `ACCOUNT_DEACTIVATED`. (`AuthService.reissue`)
- **자격 변경 시 세션 무효화**: `UserSessionInvalidatedEvent`(user 패키지) 발행 → `SessionInvalidationListener`(auth 패키지, `@TransactionalEventListener(AFTER_COMMIT)`)가 토큰 삭제. 이벤트로 디커플링해 **auth→user 의존 방향 유지**. 비번 변경·탈퇴에 적용.
- **낙관적 락**: `User`/`UserAddress`에 `@Version` + `V4__add_optimistic_lock_version.sql`.
- **예외 핸들러 보강**: `DataIntegrityViolationException`→409, `Exception`→500(봉투 유지·로깅), `ServletRequestBindingException`→400, `BusinessException` 로깅.

### 5. 결제 — 멱등 TOCTOU 차단
- **command/query 분리**: `PaymentCommandService.process`가 `saveAndFlush`로 `orderId` 유니크 충돌을 **PG 호출 전에** 드러냄(이중 청구 방지). `PaymentService.pay`는 비트랜잭션 오케스트레이터로 충돌을 **트랜잭션 밖에서 멱등 재조회**로 흡수(트랜잭션 poisoning 회피).
- 예외 핸들러 보강(회원과 동일 패턴).

### 6. 상품
- **공개 목록 필터**: `findByDeletedFalseAndStatusIn`/`findProductsByCategoryIds`에 `status in (SALE, SOLD_OUT)`(`PUBLICLY_VISIBLE`) → HIDDEN(가공 중)·DISCONTINUED(단종) 노출 제거.
- **낙관적 락**: `Product`에 `@Version` + `V10__add_product_version.sql`.

## 🧭 주요 설계 결정

- **결제 승인 후는 roll-forward** — 분산 트랜잭션에서 이미 캡처된 결제를 보상(환불)으로 되돌리면 결제·재고 정합이 더 어긋난다. confirm은 멱등이므로 재시도로 전진 완결하고, 실패는 드러내되 되돌리지 않는다.
- **락 순서 고정으로 데드락 예방** — 동일 자원을 항상 같은 순서로 잠그면 순환 대기가 생기지 않는다(고전적 데드락 회피).
- **예약 행 비관적 락으로 상태 전이 직렬화** — `RESERVED→(CONFIRMED|RELEASED)`를 상호 배타로 만들어 단일/다중 인스턴스 모두에서 경합을 막는다(분산 락 도입 전 DB 수준 해법).
- **세션 무효화는 도메인 이벤트로** — auth→user 단방향 의존을 깨지 않으려 user는 이벤트만 발행하고 auth가 소비. `AFTER_COMMIT`이라 롤백 시 잘못된 세션 종료가 없다.
- **결제 멱등은 DB 유니크 + 트랜잭션 밖 흡수** — 같은 트랜잭션 안에서 제약 위반을 잡으면 트랜잭션이 오염돼 후속 재조회가 불가하므로, 오케스트레이터(비트랜잭션)에서 충돌을 잡아 재조회.

## 🚧 미구현 / 다음 (의도적 보류)

- **CORS origin 화이트리스트** — 운영 프론트 origin 확정 후 환경변수화(현재 와일드카드는 로컬 개발 편의 유지).
- **게이트웨이단 ADMIN 인가** — `/admin/**` 경로 role 게이트(현재 다운스트림 방어만).
- **Resilience4j** — Feign 재시도·서킷브레이커·백오프(현재 타임아웃만).
- **보상 실패 아웃박스/복구 배치** — `ORDER_COMPLETION_FAILED`·보상 실패의 재처리. Kafka 비동기 Saga 전환(11주차)과 함께 검토.

## ⚠️ 트레이드오프 / 주의

- **새 Flyway 마이그레이션 3개**(user `V4`, product `V10`, inventory `V2`)는 기동 시 자동 적용. 기존 dev DB에 `(order_id, product_id, option_key)` 중복 예약 행이 있으면 inventory `V2`가 실패할 수 있음(깨끗한 DB면 무해).
- **confirm 재시도(3회)는 동기 요청 스레드에서 즉시 반복** — 백오프/서킷브레이커는 아직 없음(Resilience4j 추후). 일시적 글리치엔 충분하나 장기 장애엔 `ORDER_COMPLETION_FAILED`로 빠짐.
- **catch-all `Exception` 핸들러**는 프레임워크 4xx(헤더/파라미터 누락 등)를 500으로 떨어뜨릴 수 있어 `ServletRequestBindingException`→400을 별도로 잡았다. 다른 Spring MVC 예외의 전체 커버리지(`ResponseEntityExceptionHandler` 상속)는 추후.
- **결제 트랜잭션 내 PG 호출**은 여전히 남아있음(mock이라 즉답). 실 PG 교체 시 커넥션 점유를 줄이려면 PG 호출을 트랜잭션 밖으로 빼야 함.
