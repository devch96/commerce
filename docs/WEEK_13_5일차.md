# 13주차 5일차 — 상품 RDBMS 전환 & 장바구니

> copa-product를 MongoDB → MySQL(JPA)로 전환하고, 회원 장바구니·상품 soft delete·금액 BigDecimal 등 도메인을 정교화.

## 🎯 목표

- copa-product를 JPA·QueryDSL·인덱스 학습과 `.clauderules`(JPA 규약)에 맞춰 RDBMS로 전환한다.
- 회원 장바구니를 신설한다(회원 전용, 영속).
- 상품 삭제·금액·이미지 순서 등 도메인 규칙을 현업 수준으로 정교화한다.

## 🛠️ 작업 내용

### 1. RDBMS 전환 (MongoDB → MySQL)
- `build.gradle`: `data-mongodb` → `data-jpa` + Flyway + mysql-connector (Redis 캐시 유지)
- `Product`/`Category` `@Document` → `@Entity`, id `String` → `Long`
- 컬렉션/Map 필드 매핑(성격별):
  - 카테고리 → **`@ManyToMany List<Category>`**(조인 테이블 `product_categories`). 조상 클로저는 저장하지 않고,
    검색 시점에 `CategoryService.collectSubtreeIds(categoryId)`로 하위 트리를 펼쳐 `ProductRepository.findByCategoryIds(List<Long>)`로 조회
  - `images` → `@ElementCollection` + **`@OrderColumn`**(순서 보장)
  - `specs` → **JSON 컬럼**(`@Convert` + `StringMapJsonConverter`)
- `MongoAuditingConfig` → `JpaAuditingConfig`
- docker-compose: `copa-product-mongo` → `copa-product-mysql`(3307), Redis 유지
- Flyway `V1`(catalog) ~ `V5`(이미지 순서·스펙 JSON)

### 2. 회원 장바구니 (신규, 회원 전용)
- `CartItem(userId, @ManyToOne Product, quantity, addedAt)`, `unique(user_id, product_id)`, MySQL 영속
- API(`/cart`, 인증 필요): 조회(enrich + `available`), 담기(수량 누적), 수량 변경, 항목 삭제, 비우기
- 조회는 `join fetch`로 N+1 방지, 상품 로드는 `ProductQueryService` 경계
- 게이트웨이 `/cart/**` 라우팅 추가

### 3. 상품 soft delete
- 물리 삭제 → `deleted` 플래그. 카탈로그 목록/상세·내부 조회는 제외(`findByDeletedFalse`, JPQL 필터, `getById` 필터)
- `isPurchasable()`에 `!deleted` 포함 → 삭제 상품은 담기 불가, 장바구니엔 `available=false`로 표시
- Flyway `V3`

### 4. 금액 BigDecimal
- `price`·`lineTotal`·`totalPrice`를 `Long` → `BigDecimal`(`DECIMAL(19,2)`), 비교 `compareTo`·연산 `add/multiply`
- `.clauderules`에 통화 규약 추가, Flyway `V4`

## 🧭 주요 설계 결정

- **필드 성격별 매핑** — 카테고리는 `@ManyToMany`(엔티티 참조), 순서 중요 리스트(`images`)는 `@OrderColumn`, 표시 전용 가변 구조(`specs`)는 JSON 컬럼.
- **카테고리 검색: 저장형 클로저 → 조회 시 하위 트리 확장** — 상품에 조상 클로저를 비정규화 저장하지 않고, 검색 때 카테고리의 하위 트리 id를 모아 `findByCategoryIds`로 조회. 쓰기 단순·동기화 부담 없음(대신 조회마다 트리 1회 계산).
- **장바구니 상품 참조 = `@ManyToOne` 엔티티** — 같은 서비스라 조인·enrich 이점. 분리 시 productId로 회귀 가능(`ProductQueryService` 경계 유지).
- **soft delete** — 그림자 productId 우회 대신 상품 자체를 soft delete. 카탈로그 제외 + 장바구니 표시 + 주문/리뷰 참조 보존을 한 번에 해결.
- **금액 BigDecimal** — 부동소수점/정수 금지(`.clauderules`).
- **증분 마이그레이션** — V1을 고치지 않고 V4(금액)·V5(이미지·스펙)로 추가.

## 🚧 미구현 / 다음

- 비회원 장바구니(cart-id·Redis TTL) + 로그인 병합
- 목록 N+1 최적화(경량 DTO/fetch join), 캐시 심화(thundering herd·hot key)
- 재고 서비스(옵션별 재고·동시 차감), 카테고리 이동 시 클로저 재계산(이벤트)

## 🔁 설계 문서 대비 변경점
- `04. 상품`: 카테고리는 `@ManyToMany List<Category>`(저장형 클로저 폐기, 검색 시 하위 트리 확장), 이미지 `@OrderColumn`, 스펙 JSON, 상품 soft delete, 금액 BigDecimal로 보정.

## ⚠️ 트레이드오프 / 주의
- `List<Category>`는 product↔category **다대다(@ManyToMany)** 라 `.clauderules`의 "@ManyToOne 단방향만" 규약과 충돌. 요청에 따라 `@ManyToMany`로 가되, 엄격 준수가 필요하면 조인 엔티티(`ProductCategory`)로 바꾸는 선택지 있음.
- 카테고리 이동/삭제 시 상품 클로저 재계산이 필요 없어짐(저장 안 하니까). 대신 검색마다 하위 트리 계산 + `distinct` 페이징 비용.
