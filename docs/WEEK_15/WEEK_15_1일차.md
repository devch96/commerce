# 15주차 1일차 — 상품 옵션·할인 & 재고 서비스 신설

> copa-product에 무한 뎁스 옵션·옵션/조합 할인을 추가하고, 재고의 권위 원천인 copa-inventory(예약·확정·해제)를 신설.

## 🎯 목표

- 권장기능 목록의 **상품 옵션(무한 뎁스 JSON)** 과 **옵션/조합별 할인**을 상품 서비스에 구현한다.
- 옵션을 주문/재고와 잇는 계약(`optionKey`)을 정하고, 장바구니까지 옵션을 반영한다.
- 설계 05 기준 **재고 서비스(copa-inventory)** 를 신설한다(예약→확정→해제, 동시성, TTL).

## 🛠️ 작업 내용

### 1. 상품 옵션 (copa-product)
- `Product.options`(`Map<String,Object>`, JSON 컬럼) — **무한 뎁스 트리**, leaf = 선언적 초기 재고.
  예) `{"색상":{"네이비":{"사이즈":{"M":10,"L":5}}}}`
- `optionKey` 규약: leaf 경로를 `색상:네이비/사이즈:M`로 직렬화(`ProductOptions`가 평탄화·검증, 차원/값 이름에 `:`·`/` 금지).
- leaf 합계를 `stockQuantity`로 집계 → 기존 SALE 전환(재고≥1)·`isPurchasable`과 호환. 옵션 없는 단순 상품(optionKey=`""`)도 지원.
- 컨버터 `OptionTreeJsonConverter`, `OptionDiscountListJsonConverter`(specs와 동일하게 MySQL JSON / H2 VARCHAR).

### 2. 옵션 할인 (옵션별 + 조합별 통합)
- `OptionDiscount(optionKey, AMOUNT|RATE, value)`. optionKey가 **prefix면 옵션별**(`색상:블랙`), **full path면 조합별**(`색상:블랙/사이즈:L`).
- 한 leaf에 여러 규칙이 걸리면 **최장 일치(가장 구체적) 규칙이 승**(중복 정책). `Product.resolveOption(optionKey)` → `{stock, originalPrice, finalPrice}`.
- 등록/수정 시 모든 할인 규칙이 실제 옵션 leaf를 가리키는지 검증(미스매치 거부).

### 3. 장바구니 옵션 반영
- `CartItem.optionKey` 추가(옵션 없으면 `""`), 유니크 `(user_id, product_id) → (user_id, product_id, option_key)`.
- 담기 시 옵션 유효성 검증(없는 옵션 거부), 조회 시 **옵션 할인가**로 단가·`available`(옵션 재고>0) 재계산.
- 옵션이 바뀌어 무효해진 항목은 `available=false`로 표시(예외 흡수). Flyway `V8`(상품 옵션), `V9`(cart optionKey).

### 4. 연결점 (주문/재고가 소비)
- `GET /internal/products/{id}/option-price?optionKey=` → 주문이 가격 스냅샷, 재고가 재고 시드에 사용.
- `ProductResponse`에 `options`·`optionDiscounts` 노출.

### 5. 재고 서비스 신설 (copa-inventory, 8083)
- 모듈 스캐폴딩: `build.gradle`(web·data-jpa·validation·flyway, Redis 없음), application.yaml(MySQL 3308), 공통(ApiResponse·예외)·config(JpaAuditing·Scheduling).
- 엔티티(모두 `@DynamicInsert`/`@DynamicUpdate`):
  - `Inventory(productId, optionKey, stock, @Version)` — unique `(product_id, option_key)`, **재고의 권위 원천**
  - `StockReservation(orderId, productId, optionKey, quantity, status, expiresAt)` — 보상·멱등 기준
- 로직 — **"결제 전 예약 → 결제 후 확정/실패 시 해제"**:
  - `reserve` — 주문의 모든 품목을 한 트랜잭션에서 원자적 차감(부분 예약 없음), `existsByOrderId` 멱등, 부족 시 `OUT_OF_STOCK`(409)
  - `confirm` — `RESERVED→CONFIRMED`(추가 차감 없음), `release` — 가용 재고 복원 + `RELEASED`(멱등)
  - **동시성**: 예약 핫패스는 비관적 락(`findForUpdate`)으로 직렬화 + `@Version` 보강 → 오버셀링 0
  - **TTL 스케줄러**: 미결제 `RESERVED` 예약 주기적 자동 해제(안전망)
- 내부 API `/internal/inventory`: 조회·`register`(시드)·`reserve`·`confirm`·`release`. Flyway `V1`.

## 🧭 주요 설계 결정

- **재고의 권위는 재고 서비스** — 상품의 옵션 leaf는 *선언적 초기 재고/표시용*. 실제 예약·차감·동시성은 copa-inventory가 `optionKey`로 소유(설계 04 보정과 일치). 상품 서비스에서 차감하지 않아 다음 단계와 중복·재작업 없음.
- **optionKey = 통합 계약** — `색상:네이비/사이즈:M` 한 문자열이 상품·장바구니·주문(`OrderItem.optionKey`)·재고(`Inventory.optionKey`)를 관통. 옵션 없는 상품은 `""`로 정규화해 유니크 키 일관성 확보(NULL 다중 허용 회피).
- **옵션별·조합별 할인 통합** — 둘 다 optionKey 경로 매칭으로 환원하고 **최장 일치 우선**으로 우선순위·중복 정책을 단순화. 쿠폰/플래시세일은 프로모션 서비스(08) 소관으로 분리.
- **예약 동시성: 비관적 락 + @Version** — 경합 핫패스(예약)는 `SELECT ... FOR UPDATE`로 결정적 직렬화, 일반 갱신은 낙관적 락. 동시 예약 통합 테스트로 오버셀링 0 검증.
- **멱등·TTL 보상** — reserve/confirm/release 모두 멱등, 미결제 예약은 TTL 스케줄러가 회수 → 이벤트 유실에도 재고가 묶이지 않음.

## 🚧 미구현 / 다음

- **Kafka Saga 연동**(11주차) — 현재는 동기 REST 골격. 주문이 `ORDER_CREATED→reserve→결제→confirm/release` 오케스트레이션.
- **상품→재고 자동 시드** — 지금은 `register` 수동/내부 호출. 상품 등록 이벤트로 자동화는 Kafka 단계.
- **Redis 원자 차감(Lua)** — 선착순/플래시세일 초고경합 심화(설계 05/08).
- 다음 순서 후보: **주문 서비스(06)**(상품·재고 준비 완료).

## 🔁 설계 문서 대비 변경점

- `04. 상품`: 옵션 무한 뎁스 JSON·옵션/조합 할인(최장 일치) 구현, 장바구니 optionKey 반영, 옵션가 조회 내부 API 추가.
- `05. 재고`: 설계의 단일 reservation(orderId당 1행)을 **주문당 다품목(같은 orderId 다행)** 으로 확장해 실제 주문 구조에 맞춤. reserve/confirm/release·@Version·TTL은 설계 그대로.

## ⚠️ 트레이드오프 / 주의

- 옵션 트리 leaf 합계를 `stockQuantity`로 집계하므로, 옵션 상품은 등록/수정 시 leaf 재고가 곧 초기 재고가 된다. 이후 권위는 재고 서비스로 넘어가며 상품의 stockQuantity는 카탈로그 표시·판매가능 판정용 스냅샷 성격.
- 예약에 비관적 락을 쓰므로 한정 수량 폭주 시 락 경합이 병목이 될 수 있다 → 초고경합은 Redis 원자 차감(심화)으로 전환 여지.
- 옵션 변경 후 기존 장바구니/예약의 optionKey가 무효해질 수 있어, 장바구니는 표시만(available=false), 주문 단계에서 옵션가를 재검증한다.
