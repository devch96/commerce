# 14주차 3일차 — 상품·카테고리 서비스 & 권한·소유권 모델

> 풀 MSA 기준으로 상품 서비스를 신설하고, 회원 기능 확장 + 판매자/운영자 권한 모델을 도입.

## 🎯 목표

- 회원 기능을 권장 기능 목록 수준으로 확장한다.
- 상품 서비스(copa-product)를 MongoDB 기반으로 신설한다.
- 카테고리(무한 트리) 도메인과 판매자(SELLER)/운영자(ADMIN) 권한·소유권 모델을 도입한다.

## 🛠️ 작업 내용

### 1. 회원 기능 확장 (copa-user)
- `User`에 **휴대폰(phone)**, **`is_active`**(탈퇴=비활성화) 추가
- 가입/변경 비밀번호 **규칙 검증**(숫자+특수문자, 8~64자)
- `PUT /users/me`(이름·휴대폰, 이메일 불변), `PATCH /users/me/password`, `DELETE /users/me`(비번 재확인 후 soft delete)
- 로그인 시 비활성 계정 차단(`ACCOUNT_DEACTIVATED`), Flyway `V3__add_user_phone_and_active`

### 2. 컨트롤러 분리 (공개 API ↔ 내부 API)
- `UserController`(`/users`) · `InternalUserController`(`/internal/users`) · `AdminUserController`(`/users/{id}/role`)
- `ProductController`(`/products`) · `InternalProductController`(`/internal/products`)
- 클래스 레벨 `@RequestMapping` 적용, `/internal/**`은 메시 내부 전용으로 컨트롤러 분리

### 3. 권한 모델 — SELLER 추가
- `UserRole`: **USER < SELLER < ADMIN**
- 운영자 승격 API `PATCH /users/{userId}/role`(ADMIN 전용)

### 4. 상품 서비스 (copa-product, MongoDB)
- `Product`(`@Document`): `productCode`(PROD-연도-UUID 유니크), `sellerId`, `name`, `price`, `categoryIds`, `categoryPathIds`, `status`(기본 HIDDEN), `stockQuantity`, `description`, `specs`
- 커머스 규칙: price·stock ≥ 0, SALE 전환 시 재고 ≥ 1
- 조회 공개, 등록은 SELLER 이상, 수정/삭제는 등록 판매자 본인 또는 ADMIN(소유권 `sellerId`)
- 감사는 `@EnableMongoAuditing` (도큐먼트엔 `@EntityListeners` 불필요)

### 5. 카테고리 — 무한 트리 (ADMIN 관리)
- `Category`(`@Document`): `parentId`로 무한 중첩(`null`=최상위)
- 생성 · 이동(사이클 방지) · 삭제(자식 있으면 거부) · 트리 조회(`GET /categories` 공개)
- 쓰기는 ADMIN 전용

### 6. 상품 ↔ 카테고리: 다중 + 조상 클로저
- 한 상품은 여러 카테고리 보유(`categoryIds` = 셀러 선택)
- 필터용 조상 클로저(`categoryPathIds` = 선택 + 모든 조상)를 비정규화 저장 → 상위 카테고리로 필터해도 하위 상품이 잡힘(멀티키 인덱스 1회)

### 7. 게이트웨이
- `/products/**`, `/categories/**` → copa-product 라우팅
- 메서드 인식형 화이트리스트: `GET /products`, `GET /categories`만 비로그인 공개, 쓰기는 인증+권한

## 🧭 주요 설계 결정

- **카테고리는 별도 서비스로 분리하지 않음** — 상품 카탈로그와 같은 컨텍스트, 읽기 위주 참조 데이터. 엔티티(도메인)로만 둠.
- **재고는 상품 문서에 두지 않고 별도 재고 서비스로** — 동시성·오버셀링·Saga 보상 때문. 현재는 `stockQuantity` 단순 필드 + `specs`로 시작.
- **조상 클로저**는 카테고리 이동/삭제 시 자동 갱신 보류(결과적 일관성, 추후 이벤트). 우선 조회 정합성.
- **단방향 매핑 일관** — JPA(copa-user)는 `@ManyToOne`, Mongo(copa-product)는 id 참조(`@DBRef` 미사용).

## ✅ 권한 매트릭스

| 작업 | 권한 |
|---|---|
| 상품/카테고리 조회 | 공개(비로그인) |
| 상품 등록 | SELLER+ |
| 상품 수정/삭제 | 등록 판매자 본인 + ADMIN |
| 카테고리 생성/수정/삭제 | ADMIN |
| 회원 등급 변경 | ADMIN |

## 📐 설계 문서 대비 변경점 (보정 반영)
- `03. 회원·인증`: 역할에 SELLER 추가, 프로필 엔드포인트 `/users/**`로 구현, 배송지 기능 추가, 탈퇴 soft delete.
- `04. 상품`: 카테고리 String→도메인(무한 트리), 상품 다중 카테고리+조상 클로저, 등록 권한 ADMIN→SELLER+ 소유권, 재고 별도 서비스 분리, MongoDB 확정, Cart 미구현.

## ⏭️ 다음
- 재고 서비스(`optionKey`별 재고, 동시성 락) 신설
- 카테고리 이동 시 상품 클로저 재계산(이벤트 기반)
