# 15주차 4일차 — 쿠폰 서비스 신설 & 주문 Saga 연동 (Phase 1)

> 설계 08(프로모션·쿠폰)의 동기 코어를 신설. 쿠폰 정의·발급·검증 + 주문 Saga에 reserve→use→release로 연동. Redis 선착순·Kafka는 Phase 2~3로 분리.

## 🎯 목표

- 설계 08의 쿠폰/할인 모델을 **동기 REST 코어**로 신설(copa-coupon, 8086).
- 발급(1인 1매, 한정 수량)·검증·할인 계산을 구현하고, 주문(copa-order)에 재고와 동일한 **reserve→use→release** 패턴으로 연동.
- 선착순(Redis Lua)·대기열·플래시세일·Kafka는 다음 단계로 미룬다.

## 🛠️ 작업 내용

### 1. copa-coupon 신설 (포트 8086, MySQL 3311)
- 모듈 스캐폴딩(build.gradle/wrapper/application.yaml/공통 ApiResponse·BusinessException·ErrorCode·GlobalExceptionHandler)을 copa-inventory 기준으로 생성.
- 예외 핸들러는 직전(3일차) 보강 패턴 적용: `DataIntegrityViolationException`→409, `ServletRequestBindingException`→400, `Exception`→500(봉투 유지·로깅), `BusinessException` 로깅.

### 2. 도메인 — Coupon / UserCoupon
- `Coupon`(정의): `type`(FIXED_AMOUNT/PERCENTAGE), `value`, `maxDiscount`(정률 상한), `minOrderAmount`, `expirationType`(CREATED_PLUS_DAYS/ISSUED_PLUS_DAYS/FIXED_RANGE), `validDays`, `startDate`/`endDate`, `totalQuantity`/`issuedQuantity`, `targetType`(ALL), `status`(ACTIVE/INACTIVE), `@Version`.
  - `calculateDiscount(lineTotal)`: FIXED=`min(value, lineTotal)`, PERCENTAGE=`min(lineTotal×value/100, maxDiscount)`, `setScale(2, HALF_UP)`, minOrderAmount 미달 시 거부.
  - `issueOne()`: ACTIVE·잔여 수량 검증 후 `issuedQuantity++`. `resolveExpiry(issuedAt)`: 방식별 만료 산정.
  - `create(...)`에서 `validateDefinition()`(금액/율 양수, 타입·기간 방식별 필수 필드).
- `UserCoupon`(발급 인스턴스): `coupon`(@ManyToOne LAZY 단방향), `userId`, `status`(ISSUED/RESERVED/USED), `expiresAt`, `reservedOrderId`/`usedOrderId`, `discountAmount`, `@Version`. **UNIQUE(coupon_id, user_id)** = 1인 1매.
  - reserve(ISSUED→RESERVED)·use(RESERVED→USED)·release(RESERVED→ISSUED)·restore(USED→ISSUED), 상태 가드 포함.

### 3. 발급·검증·내부 API
- **발급**(`POST /coupons/{couponId}/issue`): `existsByCoupon_IdAndUserId` 선검사 + `findByIdForUpdate`(비관적 락)로 `issuedQuantity` 직렬화 → 초과 발급 0, UNIQUE 2차 방어.
- **조회**: 내 쿠폰(`GET /coupons/me`), ADMIN CRUD(`/admin/coupons`, `X-User-Role`로 ADMIN 재검증).
- **내부 API**(`/internal/coupons`): reserve(검증+할인 계산+RESERVED)·confirm(use)·release·restore. 모두 `orderId` 기준 멱등. 상태 전이는 예약 행 비관적 락으로 직렬화.
- Flyway `V1__create_coupon_tables.sql`(coupons / user_coupons, FK는 DDL 미설정·JPA 논리 제어).

### 4. 인프라 / 게이트웨이
- docker-compose `copa-coupon-mysql`(3311) 추가.
- 게이트웨이 `/coupons/**`·`/admin/coupons/**` → coupon 라우팅, `GatewayProperties`에 `couponServiceUri`(6번째) 추가, 테스트 생성자 갱신.

### 5. 주문 Saga 연동 (copa-order)
- `CouponFeignClient` + `CouponClient`(어댑터, 봉투 언랩·예외 변환) + `CouponReserveView` 추가. `copa.clients.coupon` 설정.
- `OrderService.createOrder`:
  ```
  가격 스냅샷 → 주문 생성(ORDER_PLACED)
   → [보상 가능] 쿠폰 reserve(할인 계산) → applyCouponDiscount → 재고 reserve → 결제(payable=총액-할인)
   → [roll-forward] 재고 confirm + 쿠폰 use(confirm) + PAYMENT_COMPLETED
   → [실패] 재고 release + 쿠폰 release + CANCELLED
  ```
- `couponId`(=UserCoupon id)가 있으면 선점, 적용 불가는 4xx→`COUPON_NOT_APPLICABLE`로 주문 거절.
- 사용자 취소(`cancelOrder`)에 쿠폰 `restore` 추가(주문에 couponId 있을 때). `Order.applyCouponDiscount`·`OrderCommandService.applyCouponDiscount` 추가.

### 6. 테스트
- copa-coupon 14 / copa-order 7(쿠폰 3종 포함) / copa-gateway 12 — **전부 통과**.

## 🧭 주요 설계 결정

- **Coupon / UserCoupon 분리** — 정의(템플릿)와 발급 인스턴스를 나눠 1인 1매·상태 전이·만료를 인스턴스 단위로 관리.
- **쿠폰 선점은 결제 승인 전(보상 구간)** — 실패 시 release로 되돌리고, 결제 승인 후 use는 roll-forward(3일차 saga 정책과 일관).
- **할인 적용 = 옵션 할인가 위에 쿠폰** — `lineTotal`(옵션 할인 반영 합계=주문 총액)에 쿠폰을 적용, 정률은 maxDiscount 상한.
- **Phase 1은 DB 락 기반 발급** — 설계 08의 Redis Lua 대신 비관적 락 + UNIQUE로 초과 발급 0. Redis 선착순은 Phase 2.
- **couponId = UserCoupon id** — 사용자가 보유한 발급 인스턴스를 직접 가리켜 reserve가 바로 선점.

## 🚧 미구현 / 다음

- **Phase 2**: 선착순 발급 Redis Lua 원자연산(1인1매+수량차감) + 성공분 DB 반영.
- **Phase 3**: 가상 대기열(ZSET)·플래시세일 한정재고·Kafka(coupon-issued) 비동기·부하 테스트.
- target_type `CATEGORY`/`PRODUCT` 한정(상품 카테고리 조회 연동), 쿠폰 만료 스케줄러, 발급 취소/소멸.

## ⚠️ 트레이드오프 / 주의

- **payable=0 엣지**: FIXED 쿠폰이 주문 총액과 같으면 결제액 0 → MockPG가 `amount>0`만 승인해 거절됨. 드물어 Phase 1은 보류(실 PG/0원 결제 처리에서 정리).
- **신규 마이그레이션**: copa-coupon `V1`. 새 DB라 충돌 없음. 기동 전 `docker compose up -d`로 copa-coupon-mysql(3311) 필요.
- **쿠폰 reserve가 보상 구간**: 결제 전 단계라 실패 시 release로 안전. 결제 승인 후 use 실패는 `ORDER_COMPLETION_FAILED`(roll-forward)로 빠지며 환불하지 않음.
- **동기 호출**: 쿠폰도 OpenFeign 동기. 선착순 폭주 내성은 Phase 2(Redis)에서 확보.
