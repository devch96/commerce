# 17주차 2일차 — 실 PG(토스/카카오) 연동 감사 & 동기 2단계 결제 Saga

> copa-payment의 토스/카카오 PG 구현을 감사해 버그를 잡고, PG 결제창(redirect) 방식에 맞춰 주문·결제를 **동기 2단계 Saga**(준비 → 확정)로 재구성.

## 🎯 목표

- 기존 `copa-payment`의 토스/카카오 실 PG 구현을 점검하고 오류·개선점을 도출한다.
- 실 PG는 "프론트 결제창 → 리다이렉트 → 백엔드 승인"으로 흐르므로, 한 요청에 끝내던 동기 Saga를 **2단계(준비/확정)** 로 분리한다.
- 결제 확정 경로는 **동기 오케스트레이션**(order가 payment confirm → 재고/쿠폰 확정)으로 유지한다(즉시성). Kafka 이벤트 Saga는 후속.

## 🛠️ 작업 내용

### 1. copa-payment 감사 & 버그 수정
- **PG 거절/시스템오류 구분 버그**: 승인 거절을 `catch(IllegalArgumentException)`으로 판별했으나 Feign은 4xx에서 `FeignException`을 던짐 → 거절도 500으로 샜음. `FeignException.status()` 기반 분기(4xx=거절→`PgApproval.fail()`, 5xx/타임아웃=오류→rethrow)로 수정.
- **승인 금액 위변조 검증 추가**: 토스 `status=="DONE"`+`totalAmount`, 카카오 `amount.total`을 요청 금액과 대조.
- **카카오 요청 DTO snake_case 버그**: `KakaoApproveRequest`가 camelCase라 실제론 카카오가 400. `@JsonNaming(SnakeCaseStrategy)`로 수정(신규 DTO 포함).
- **설정**: `kakao.secret-key`·`kakaoPaymentClient` 타임아웃 누락 추가, 시크릿 키 환경변수화, `TossFeignConfig`의 `@Configuration` 제거(Feign 설정 전역오염 안티패턴).
- **정리**: 구현/사용되지 않던 죽은 `PaymentGateway` 인터페이스 삭제, `Payment.amount`의 무의미한 `precision/scale` 제거.

### 2. copa-payment — ready/confirm/cancel 재편
- 엔드포인트: `POST /internal/payments/kakao/ready`, `/kakao/confirm`, `/toss/confirm`, `POST /internal/payments/{orderId}/cancel`.
- `Payment`에 **`pgProvider`·`tid`** 추가(마이그레이션 `V2`). 카카오는 결제창 진입 전 서버 `ready`로 카카오에서 **tid**를 발급받아 저장 → confirm 때 `pg_token`과 함께 승인. 토스는 ready 없이 `paymentKey`로 confirm.
- **취소**: 저장된 `pgProvider`로 게이트웨이 라우팅(토스 `/v1/payments/{paymentKey}/cancel`, 카카오 `/v1/payment/cancel`) → `Payment.cancel()`. orderId 멱등.
- PG별 CommandService 2개를 **`PaymentCommandService`** 로 통합(중복 제거, 트랜잭션 경계 단일화).

### 3. copa-order — 동기 2단계 Saga
- 상태 **`PENDING_PAYMENT`** 추가(주문 생성 → 결제 대기 → 확정 시 `PAYMENT_COMPLETED`).
- **Phase 1** `POST /orders`(`pgProvider`·`orderName` 추가): 가격 스냅샷 → 주문 생성 → 쿠폰·재고 reserve → 카카오면 payment `ready` → `OrderCheckoutResponse{orderId, payableAmount, orderName, pgProvider, redirectUrl}` 반환.
- **Phase 2** `POST /orders/{orderId}/payment/confirm`(`{pgProvider, paymentKey|pgToken}`): 소유·상태·멱등 검증 → payment confirm → 승인 시 재고 confirm+쿠폰 use+완료(roll-forward), 거절 시 재고·쿠폰 release+취소(보상).
- **`POST /orders/{orderId}/payment/fail`**(failUrl 경유): 예약 해제 + 취소.
- `PaymentFeignClient`/`PaymentClient`를 ready/confirm/cancel 계약으로 교체, 내부 호출이라 **`X-User-Id` 헤더 직접 전달**.
- 단위테스트(`OrderServiceTest`) 2단계 흐름으로 재작성(9건 통과).

## 🧭 주요 설계 결정

- **return URL = 프론트 라우트** — PG 리다이렉트는 JWT를 안 실으므로, return URL을 백엔드로 직접 두면 게이트웨이 인증이 깨진다. 프론트 라우트가 pg_token을 받아 **JWT 붙여** confirm API 호출 → 게이트웨이 인증 유지.
- **금액은 서버 저장 payable만 신뢰** — confirm은 클라 금액을 받지 않고 order가 DB에 저장한 payable로 승인. 사용자가 조작해 적게 결제했으면 PG가 금액 불일치로 거절 → 보상. (게이트웨이의 응답↔요청 금액 대조는 sanity check, 실제 방어선은 이 서버-저장 금액 사용)
- **토스/카카오 플로우 차이 수용** — 카카오는 서버 `ready`(tid 발급)가 필수, 토스는 confirm만. ready는 카카오 전용으로 분리(억지로 통일하지 않음).
- **인터페이스 강제 지양** — approve는 컨트롤러에서 이미 PG별로 갈리므로 공통 인터페이스 구현이 불필요(입력 DTO 통일 부담만↑). 취소 라우팅만 `pgProvider` switch로 처리.
- **동기 오케스트레이션 유지** — 결제 확정은 즉시성이 필요해 order가 동기 지휘. Kafka는 알림·정산 같은 비동기 후처리에 어울림(후속).

## 🔁 설계 문서 대비 변경점

- `07. 결제`: 가상 PG(mock) → **실 PG(토스/카카오) 2단계(ready/confirm)**. `Payment`에 `pgProvider`·`tid` + 마이그레이션 V2. 취소 시 provider 라우팅. 거절/오류 `FeignException` 분기, 금액·상태 검증.
- `06. 주문`: 단일 요청 Saga → **2단계 Saga**(`PENDING_PAYMENT` 추가, createOrder=준비 / confirm=확정 / fail=보상).

## 🚧 미구현 / 다음

- **결제 대기 타임아웃 스케줄러** — PENDING_PAYMENT 미완료 주문 자동 취소 + 예약 해제(현재는 재고 TTL 스윕이 1차 안전망).
- **부분 환불/어드민 승인**, 정산 대사(설계 07 심화 2·4).
- **Kafka 이벤트 Saga 전환**(설계 심화 3), 서킷브레이커·다중 PG 폴백(Resilience4j).
- **CLAUDE.md 갱신** — 결제 설명("가상 PG(mock)")을 실 PG 2단계로 반영 필요.

## ⚠️ 트레이드오프 / 주의

- **confirm 응답은 동기** — 프론트가 결제 결과를 즉시 받는다. Kafka 비동기 전환 시엔 202 + 폴링/SSE로 바뀜.
- **카카오 tid 저장 의존** — ready에서 저장한 tid가 없으면 confirm 불가. ready 중복 호출은 `order_id` 유니크로 차단(현재 409, 재진입 UX는 후속).
- **로컬/실 PG 키** — 시크릿·redirect URL은 환경변수. 기본값은 더미라 실제 승인은 실 키 주입 필요.
- **검증 범위** — 서비스별 컴파일 + 주문 단위테스트까지 확인. DB·실 PG 통합 실행은 미수행.