# 13주차 2일차 — 회원·인증 서비스 & 배송지 관리

> AI 기반 MSA 이커머스 플랫폼 `copa`
> 관련 커밋: `[2일차] 회원·인증 서비스(copa-user) 구현` (스테이징)

## 🎯 목표

- 모든 서비스의 전제가 되는 회원·인증 서비스(copa-user)를 완성한다.
- 베이스만 잡혀 있던 **배송지(UserAddress)** 기능을 완성한다.
- 작업 중 도출된 코딩/도메인 규칙을 `.clauderules`에 반영한다.

## 🛠️ 작업 내용

### 1. 회원·인증 (auth + user 패키지)
- JWT(jjwt 0.12.6) + Redis 기반 인증
  - 회원가입 / 로그인 / 재발급(Refresh Token Rotation) / 로그아웃
  - `JwtProvider`(발급·검증), `TokenService`(Redis refresh token), `AuthService`
- 회원 프로필 조회 `GET /users/me`
- 서비스 간 내부 조회 `GET /internal/users/{id}` (게이트웨이 미경유, 메시 내부 전용)
- BCrypt 단방향 해시로 비밀번호 저장

### 2. 배송지 관리 (UserAddress) — 신규 완성
- 엔티티: `addressName, receiverName, zipcode, baseAddress, detailAddress, isDefault`
- API
  | Method | Path | 설명 |
  |---|---|---|
  | POST | `/users/me/addresses` | 추가 (201) |
  | GET | `/users/me/addresses` | 목록 (id 오름차순) |
  | PUT | `/users/me/addresses/{id}` | 수정 |
  | PATCH | `/users/me/addresses/{id}/default` | 기본 배송지 지정 (204) |
  | DELETE | `/users/me/addresses/{id}` | 삭제 (204) |
- 비즈니스 불변식
  - **최대 5개** (초과 시 `ADDRESS_LIMIT_EXCEEDED`)
  - **기본 배송지 유일성** (회원당 0 또는 1개): 첫 주소 자동 기본화, 기본 삭제 시 승격
  - 본인 소유만 조작 가능 (`findByIdAndUser_Id`로 소유권 검증)

### 3. DB 스키마 (Flyway)
- `V1__create_users_table.sql`
- `V2__create_user_addresses_table.sql`
- 테스트는 H2(`ddl-auto: create-drop`), 운영은 MySQL + Flyway

### 4. 컨벤션 반영 (.clauderules)
- `iteration`: Stream 변환 연산(map/filter)은 허용, **부수효과용 `stream().forEach()`는 금지**하고 for 문 사용 (스트림 파이프라인 오버헤드 회피)
- `member_business_logic`: 배송지 최대 5개, 기본 배송지 유일성 규칙

## 🧭 주요 설계 결정

- **`User`↔`UserAddress`는 단방향 `@ManyToOne`** (자식이 FK 소유)
  - `@OneToMany` 단방향(+`@JoinColumn`)은 INSERT 후 FK를 채우는 별도 UPDATE가 나가는 안티패턴 → 성능·복잡성 측면에서 배제
  - 조회는 자식 레포지토리에서 `findByUser_Id(...)`로 직접 처리
- **기본 배송지 변경 시 전체 해제 후 타깃만 설정**: 기본이 2개 이상인 깨진 상태도 복구하는 자기 치유(self-healing). `@DynamicUpdate` + 변경 감지로 실제 변경된 행만 UPDATE.
- **물리 FK 제약은 DDL에 생성하지 않음** (`.clauderules` 규칙): 무결성은 JPA `@ManyToOne`으로 논리 제어, 인덱스만 DDL에 둠.

## 🐞 버그 수정

- `AuthService.reissue`가 호출하던 `UserService.getById`가 `private`이라 컴파일 실패 → 외부 호출용이 맞으므로 `public`으로 수정.

## 📦 결과물

- 스테이징: `copa-user/**` + `.clauderules`
- 설계 문서: 옵시디언 볼트 `설계/01. 회원·인증 서비스.md`에 배송지(UserAddress) 설계 섹션 추가
- 빌드/테스트 통과

## ⏭️ 다음 (3일차 예정)

- 상품 서비스(copa-product) 구현 — 풀 MSA에 맞춰 **MongoDB** 채택, 게이트웨이 `/products` 라우팅 연동
