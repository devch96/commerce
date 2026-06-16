# 15주차 2일차 — 결제·주문 서비스 신설 & 동기 Saga (OpenFeign)

> 결제(copa-payment)와 주문(copa-order)을 신설하고, 주문이 재고·결제를 동기 호출하는 Saga를 구현. 외부 호출은 OpenFeign으로 전환.

## 🎯 목표

- 설계 06·07 기준 **주문 서비스(Saga 오케스트레이터)** 와 **결제 서비스(가상 PG)** 를 별도 모듈로 신설한다.
- "예약 → 결제 → 확정/보상" 흐름을 동기 REST로 완결하고, 실패 시 보상한다.
- 서비스 간 동기 호출을 **OpenFeign 표준**으로 정한다.

## 🛠️ 작업 내용

### 1. 결제 서비스 (copa-payment, 8084)
- `Payment(orderId 유니크 → 멱등, amount BigDecimal, status, pgTransactionId, refundedAmount)`, `@DynamicInsert/Update`
- **`PaymentGateway` 인터페이스 + `MockPaymentGateway`** — 가상 PG(금액 유효 시 승인+txId 발급). 실 PG 교체 지점을 인터페이스로 분리
- `pay`(거절 시 FAILED로 기록·예외 대신 상태 반환 → 주문이 분기), `cancel`(보상, 멱등). 내부 API `/internal/payments`, 조회 `/payments/{orderId}`
- Flyway V1, docker-compose `copa-payment-mysql`(3309)

### 2. 주문 서비스 (copa-order, 8085)
- `Order`/`OrderItem`(다대일 단방향, `.clauderules` 준수)/`OrderStatusHistory`(상태 변경 이력), `OrderStatus` 상태머신, 금액 `BigDecimal`, `@DynamicInsert/Update`
- **동기 Saga** (`OrderService` 오케스트레이터):
  ```
  상품 option-price로 가격 스냅샷 → 주문 생성(ORDER_PLACED)
   → 재고 reserve → 결제 → 성공: 재고 confirm + PAYMENT_COMPLETED
                          → 품절/결제실패: 결제취소·재고 release·CANCELLED (보상)
  ```
- API: `POST /orders`(클라이언트가 보낸 품목으로 생성), `GET /orders`(상태 필터), `GET /orders/{id}`(소유자 검증), `POST /orders/{id}/cancel`(환불+재고 복원), `PATCH /admin/orders/{id}/status`(ADMIN)
- Flyway V1, docker-compose `copa-order-mysql`(3310)

### 3. 트랜잭션 경계 분리 (command/query)
- 오케스트레이터(`OrderService`)는 트랜잭션 없음. DB 쓰기는 **`OrderCommandService`**(`@Transactional`), 읽기는 **`OrderQueryService`**(`@Transactional(readOnly)`)로 분리
- 이유: 같은 빈에서 `@Transactional` 메서드를 `this.`로 부르면(self-invocation) 프록시를 안 거쳐 트랜잭션이 적용되지 않음 → 별도 빈 cross-bean 호출로 해결 (IDE 경고 대응, `.clauderules`/CLAUDE.md에 원칙 명시)

### 4. 재고 서비스 보강 (copa-inventory)
- 결제 완료 주문의 사용자 취소를 위해 **`restore`** 추가 — CONFIRMED(및 RESERVED) 예약의 재고를 되돌리고 RELEASED로. `/internal/inventory/restore`
- (예약 시 `release`는 RESERVED만 복원하므로, 확정 후 취소엔 별도 복원이 필요)

### 5. OpenFeign 전환
- `copa-order`의 외부 호출을 `RestClient` → **OpenFeign**으로 변경(Spring Cloud BOM 2025.0.0 + `spring-cloud-starter-openfeign`, `@EnableFeignClients`)
- 2계층: `@FeignClient` 인터페이스(`order/client/feign`, 순수 전송) + 어댑터(`order/client`, 응답 봉투 언랩·`FeignException`→`BusinessException` 변환). 도메인 포트(어댑터 공개 API)는 유지 → `OrderService`·테스트 무수정
- CLAUDE.md에 "서비스 간 동기 호출은 OpenFeign" 컨벤션 추가

### 6. 게이트웨이·문서
- 게이트웨이 라우팅: `/orders/**`·`/admin/orders/**`→order, `/payments/**`→payment (둘 다 인증 필요). `/internal/**`은 게이트웨이 미경유
- CLAUDE.md(모듈표·설명·인프라·컨벤션), docker-compose 갱신

## 🧭 주요 설계 결정

- **결제는 별도 서비스로 분리** — 주문 안에 결제 로직을 넣지 않고 copa-payment 신설. 주문은 결제를 내부 REST(OpenFeign)로 호출. 관심사 분리 + 설계 07 충실.
- **동기 Saga (Kafka 보류)** — 설계가 "동기 단순화 가능"을 명시. 현재 인프라(동기 내부 REST)와 일치. "재고 먼저 원자 예약 → 예약 성공 주문만 결제"가 핵심(오버셀링·헛결제 방지). Kafka 비동기는 11주차.
- **주문 입력 = 클라이언트가 보낸 품목** — 장바구니는 상품 서비스 소관이라, 주문서가 모은 품목 리스트를 직접 받음(cross-service 결합 최소). 가격은 상품 `option-price`로 서버가 재조회·스냅샷.
- **금액 BigDecimal** — 설계는 `Long`이나 `.clauderules` 통화 규약·상품 옵션가(BigDecimal)와 일관.
- **OpenFeign + 어댑터(anti-corruption)** — 봉투 언랩·예외 변환을 어댑터에 모아 호출부 결합을 줄임. Feign 인터페이스를 직접 주입하면 호출부마다 봉투를 풀어야 함.
- **결제 거절은 예외 대신 상태 반환** — FAILED 기록을 남기고(롤백 회피) 주문이 status로 분기. 멱등(`orderId` 유니크).

## 🚧 미구현 / 다음

- **Kafka 비동기 Saga**(11주차) — 동기 호출이라 결제 지연이 그대로 블로킹. 이벤트 기반 + 멱등/역전 방어로 전환 예정.
- **쿠폰/할인** — 프로모션 서비스(08) 미존재. 옵션 할인가만 반영, `couponId`/`discountAmount`는 자리만.
- **취소/환불 정산 고도화** — 부분 환불·배송비 차감·어드민 환불 승인(설계 07 6번).
- **장바구니 자동 비우기** — 주문 성공 후 상품 서비스 cart 비우기 미연동.
- 멱등키(주문 `Idempotency-Key`), 타임아웃·재시도·서킷브레이커(Resilience4j).

## 🔁 설계 문서 대비 변경점

- `06. 주문`: 단일 reservation(orderId당 1행) → **주문당 다품목**(같은 orderId 다행)으로 확장. Kafka 이벤트 대신 **동기 OpenFeign 오케스트레이션**. 금액 `Long`→`BigDecimal`. 트랜잭션을 command/query 빈으로 분리.
- `07. 결제`: 가상 PG를 `PaymentGateway` 인터페이스로 추상화. 결제 거절을 예외 아닌 status로 반환(주문 분기·기록 보존).
- `05. 재고`: 확정 후 취소 복원을 위한 `restore` 추가.

## ⚠️ 트레이드오프 / 주의

- **동기 Saga**: 구현·디버깅이 단순하고 즉시 결과 응답. 대신 의존 서비스 지연/장애에 취약하고 폭주 내성이 낮음(블로킹). 보상 실패는 로깅만 하고 재고 TTL 스윕이 후속 안전망.
- **보상의 부분 실패**: 결제 성공 후 confirm 단계 오류 시 결제 취소+재고 해제+주문 취소를 시도하나, best-effort라 일시적 불일치 가능 → 정합성 대사·재처리는 11주차.
- **OpenFeign 연결 실패**는 `RetryableException`(status=-1)이라 4xx/409 분기에 안 걸리고 `DEPENDENT_SERVICE_ERROR`로 매핑.
- **command/query 분리**로 self-invocation 트랜잭션 누락을 막았지만, 오케스트레이터의 단건 `findById`(취소 시 소유권 체크)는 트랜잭션 밖 읽기(지연 로딩 없어 무해).
