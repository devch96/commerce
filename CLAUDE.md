# CLAUDE.md

스파르타 MSA 트랙 Final 개별 프로젝트 (AI 기반 MSA 이커머스 플랫폼 `copa`).

## 설계 문서 (필독)

**이 프로젝트에서 시작하는 세션은 작업 전에 반드시 아래 경로의 설계 문서들을 먼저 참고한다.**
문서들은 Obsidian 볼트에 있으며, obsidian MCP 도구(`mcp__obsidian__obsidian_get_file_contents`,
`mcp__obsidian__obsidian_list_files_in_dir`)로 볼트 루트 기준 상대 경로로 읽는다.

볼트 경로 (루트 기준):

```
3. Resource/개인 공부/MSA 아키텍처 트랙/Final/
├── 설계/
│   ├── 00. 권장 기능 목록.md
│   ├── 01. 프로젝트 개요 & 아키텍처.md
│   ├── 02. 빌드 순서 & 공통 설계.md
│   ├── 03. 회원·인증 서비스.md
│   ├── 04. 상품 서비스.md
│   ├── 05. 재고 서비스.md
│   ├── 06. 주문 서비스.md
│   ├── 07. 결제 서비스.md
│   ├── 08. 프로모션·쿠폰 & 플래시세일.md
│   ├── 09. 검색 서비스.md
│   ├── 10. 리뷰 서비스.md
│   └── 11. AI 추천·상담 서비스.md
└── 13주차/
    ├── 1일차) 프로젝트 계획 수립.md
    └── 주차 별 프로젝트 작업 플랜 가이드.md
```

> 사용자가 명시적으로 다른 자료를 지정하지 않는 한, 특정 서비스(회원/상품/재고/주문/결제 등)를 작업할 때는
> 해당 서비스의 설계 문서와 `02. 빌드 순서 & 공통 설계.md`를 함께 읽고 그 설계를 기준으로 구현한다.
> 빌드/구현 순서와 주차별 범위는 `13주차/주차 별 프로젝트 작업 플랜 가이드.md`를 따른다.

## 코딩 컨벤션

- 상세 컨벤션은 리포지토리 루트의 `.clauderules`를 따른다 (Spring Boot 3.x + Java 21, 레이어 구조,
  Lombok 제약, JPA 규약, 커머스 도메인 규칙 등). 작업 전 함께 확인할 것.
- **record 미사용** — DTO/설정 클래스는 `record` 대신 Lombok 기반 일반 클래스(`@Getter` + 생성자,
  `@Setter` 금지)로 작성한다.
- **서비스 간 동기 호출은 OpenFeign** — 외부/내부 API 호출은 `RestClient`/`RestTemplate` 대신 `spring-cloud-starter-openfeign`
  (`@EnableFeignClients` + `@FeignClient(url=...)`)으로 한다. 공통 응답 봉투 언랩·예외 변환은 Feign 인터페이스를 감싸는
  어댑터 컴포넌트에 둔다(도메인 측 포트는 안정적으로 유지, 테스트는 어댑터를 목킹).
- **`@Transactional`은 오케스트레이터와 다른 빈에 둔다** — 트랜잭션이 없는 오케스트레이션 메서드가 같은 빈의
  `@Transactional` 메서드를 `this.`로 호출하면(self-invocation) 프록시를 거치지 않아 트랜잭션이 적용되지 않는다.
  쓰기/읽기 트랜잭션 메서드는 별도 빈(예: `OrderCommandService`/`OrderQueryService`)으로 분리해 cross-bean 호출로 만든다.

## DB 스키마 관리 (DB를 쓰는 모든 서비스 공통)

- **`spring.jpa.hibernate.ddl-auto: none`** — Hibernate의 DDL 자동 생성을 끈다. 스키마는 절대
  Hibernate가 만들지 않는다.
- **Flyway로 스키마를 관리한다.** 설계 근거: `1주차/2일차) Spring Data JPA 시작학.md`의 "Flyway를
  활용한 DB 형상 관리".
  - 의존성: `org.flywaydb:flyway-core`, `org.flywaydb:flyway-mysql`
  - 설정: `spring.flyway.enabled: true`, `locations: classpath:db/migration`,
    `baseline-on-migrate: true`
  - 마이그레이션 파일: `src/main/resources/db/migration/` 아래 `V<버전>__<설명>.sql`
    (예: `V1__create_users_table.sql`). `V` + 언더바 2개가 필수.
- **테스트**는 H2를 쓰므로 MySQL 전용 마이그레이션이 깨진다. 테스트 프로파일
  (`src/test/resources/application.yaml`)에서 `spring.flyway.enabled: false` +
  `spring.jpa.hibernate.ddl-auto: create-drop`로 두어 Hibernate가 H2 스키마를 만들게 한다.
- `copa-gateway`는 WebFlux 라우터로 DB가 없어 위 규칙이 적용되지 않는다.
- `copa-product`도 **MySQL + Flyway**를 쓴다(위 규칙 동일). 컬렉션/Map 필드(categoryIds·images·specs 등)는
  `@ElementCollection` 값 테이블로 매핑하고, 상품 상세 조회 캐시는 별도 Redis(`copa-product-redis`)를 쓴다.

## 모듈 구성

멀티 모듈이 아닌, 독립 실행되는 서비스 디렉토리들의 모음이다 (각자 별도 Gradle 프로젝트).

| 모듈 | 역할 | 패키지 루트 |
|------|------|------------|
| `copa-gateway` | Spring Cloud Gateway (WebFlux) + JWT 인증 필터 | `com.sparta.copa.copagateway` |
| `copa-user` | 회원·인증 서비스 | `com.sparta.copa.copa` |
| `copa-product` | 상품 서비스 | `com.sparta.copa.copaproduct` |
| `copa-inventory` | 재고 서비스 | `com.sparta.copa.copainventory` |
| `copa-payment` | 결제 서비스 | `com.sparta.copa.copapayment` |
| `copa-order` | 주문 서비스 (Saga 오케스트레이터) | `com.sparta.copa.copaorder` |
| `copa-coupon` | 쿠폰·프로모션 서비스 | `com.sparta.copa.copacoupon` |

- 공통: Spring Boot `3.5.14`, Java 21 (toolchain), Spring Cloud `2025.0.0` (gateway).
- 인증: `jjwt 0.12.6` 기반 JWT. 게이트웨이에서 토큰 검증 후 라우팅.
- **`copa-user` 내부 구조**: `auth` 패키지(인증·토큰: `JwtProvider`, `AuthService`, `TokenService`,
  `/auth/**` 엔드포인트)와 `user` 패키지(User 도메인·프로필: `UserService`, `/users/me`,
  서비스 간 내부용 `/internal/users/{id}`)로 분리한다. `auth`가 `user`에 의존한다.
- 게이트웨이는 `/auth/**`, `/users/**`를 `copa-user`로, `/products/**`를 `copa-product`로 라우팅한다.
  `/internal/**`은 게이트웨이를 거치지 않는 서비스 간 내부 API.
- **`copa-product`**: 상품 CRUD·목록 조회. 상품 조회(`GET /products/**`)는 비로그인 공개(인증은 주문 단계부터),
  등록/수정/삭제는 게이트웨이가 주입한 `X-User-Role`로 ADMIN 권한을 한 번 더 검증(방어적 설계).
  DB는 **MySQL**(`copa-product-mysql`) + Flyway, 상품 상세 조회는 Redis Look-Aside 캐시. 장바구니(`/cart`)도 이 서비스가 담당.
  상품 삭제는 물리 삭제가 아니라 **soft delete**(`deleted` 플래그)로 처리한다.
  상품 옵션은 **무한 뎁스 JSON 트리**(`options`, leaf=선언적 초기 재고)이고 `optionKey`(`색상:네이비/사이즈:M`)로 접근한다.
  옵션별/조합별 할인(`optionDiscounts`)은 prefix=옵션별·full path=조합별이며 **최장 일치 우선**. 쿠폰은 프로모션 서비스 소관.
  상품 생성 시 **Transactional Outbox**(`outbox_events`)에 `PRODUCT_CREATED` 이벤트를 같은 트랜잭션으로 적재하고, 릴레이 스케줄러(`OutboxRelay`)가
  Kafka 토픽 `product-events`로 발행한다(재고 시드용).
  - **상품 검색**: 두 경로를 제공한다. **QueryDSL 동적 검색**(`GET /products/search`)은 MySQL 위에서 keyword(상품명·설명)·가격범위·카테고리(하위 트리)·정렬(price·name·createdAt)을
    `BooleanExpression` 술어로 선택 조합한다(`ProductQueryRepository`). **Elasticsearch 전문 검색**(`GET /products/search/es`, 집계는 `/es/aggregations`)은
    bool 쿼리(multi_match+fuzziness relevance, status·category·price filter)와 집계(가격 avg/min/max·카테고리 terms)를 `ElasticsearchOperations`로 수행한다(`ProductEsSearchService`).
    ES 색인은 **Kafka 이벤트 구독**으로 채운다: 상품 생성/수정 시 outbox에 `PRODUCT_UPSERTED`, soft delete 시 `PRODUCT_DELETED`를 토픽 `product-search-events`로 발행 →
    같은 서비스의 색인기(`ProductSearchIndexer`, `@KafkaListener`)가 ES `products` 인덱스에 멱등 upsert/delete. 인덱스 매핑은 시작 시 명시 생성(`ProductSearchIndexInitializer`).
    ES 클러스터는 ELK 로그용(`copa-elasticsearch:9200`)을 재사용하며, 검색 색인기·초기화기는 `copa.search.indexer.enabled`로 게이팅(테스트 비활성).
- **`copa-inventory`**: 옵션(`optionKey`)별 재고의 **권위 원천**. "결제 전 예약(reserve) → 결제 후 확정(confirm)/실패 시 해제(release)" 모델로
  오버셀링을 막는다. 내부 API(`/internal/inventory/**`)만 노출(주문 Saga가 호출). 예약 핫패스는 **비관적 락**으로 직렬화(+`@Version` 낙관적 락),
  reserve/confirm/release는 모두 **멱등**, 미결제 예약은 **TTL 스케줄러**가 자동 해제.
  재고 시드는 Kafka `product-events`(`PRODUCT_CREATED`) 구독으로 옵션 leaf별 `seedIfAbsent`(insert-if-absent, 멱등) 처리한다(`register` 내부 API는 보정용).
  DB는 **MySQL**(`copa-inventory-mysql`), Redis 없음. 주문 Saga의 Kafka 전환은 후속 단계(현재는 동기 REST 골격).
- **`copa-payment`**: 가상 PG(mock) 결제. 주문 Saga의 결제 단계를 수행(재고 예약 성공 후에만 호출). `Payment(orderId 유니크)`로
  멱등, 결과 status(APPROVED/FAILED)로 주문이 confirm/release 분기. 내부 API(`/internal/payments`)·조회(`/payments/{orderId}`).
  `PaymentGateway` 인터페이스 뒤에 `MockPaymentGateway`(추후 실 PG 교체). DB는 **MySQL**(`copa-payment-mysql`).
- **`copa-order`**: 주문 + **동기 Saga 오케스트레이터**. `POST /orders`는 클라이언트가 보낸 품목으로 흐름을 지휘한다:
  상품 `option-price`로 가격 스냅샷 → 주문 생성(ORDER_PLACED) → 재고 `reserve` → 결제 → 성공 시 재고 `confirm`+`PAYMENT_COMPLETED`,
  실패/품절 시 결제 취소·재고 `release`·`CANCELLED`(보상). 외부 호출은 **OpenFeign**(상품 8082·재고 8083·결제 8084) —
  `@FeignClient` 인터페이스(`order/client/feign`) + 어댑터(`order/client`, 공통 응답 봉투 언랩·`FeignException`→`BusinessException` 변환).
  DB 변경은 `OrderCommandService`(짧은 트랜잭션)에 위임(자기호출 프록시 우회 방지). 사용자 취소는 결제 환불 + 재고 `restore`(확정 재고 복원).
  금액은 `BigDecimal`. 쿠폰 적용 시 옵션 할인가(주문 총액) 위에 쿠폰 할인을 스택. DB는 **MySQL**(`copa-order-mysql`).
  쿠폰도 재고와 동일하게 **reserve→use(confirm)→release/restore**로 Saga에 연동(결제 8084·쿠폰 8086 추가).
- **`copa-coupon`**: 쿠폰·프로모션 서비스(설계 08). `Coupon`(정의/템플릿) + `UserCoupon`(발급 인스턴스, `(coupon_id,user_id)` 유니크=1인 1매).
  할인 `type`(FIXED_AMOUNT/PERCENTAGE, 정률은 `maxDiscount` 상한), 유효기간 3종, `minOrderAmount`, 한정 수량(`total/issuedQuantity`), `targetType`(현재 ALL).
  발급은 **비관적 락 + 유니크**로 초과/중복 발급 0. 내부 API(`/internal/coupons`) **reserve(검증+할인계산)·confirm(use)·release·restore**는 `orderId` 멱등(주문 Saga가 호출).
  사용자용 발급/조회(`/coupons/**`), 관리(`/admin/coupons/**`, ADMIN). DB는 **MySQL**(`copa-coupon-mysql`). **Phase 1=동기**(Redis 선착순·Kafka는 Phase 2~3).
- 게이트웨이는 `/orders/**`·`/admin/orders/**`를 `copa-order`로, `/payments/**`를 `copa-payment`로, `/coupons/**`·`/admin/coupons/**`를 `copa-coupon`으로 라우팅한다(모두 인증 필요).
  `/internal/**`은 게이트웨이를 거치지 않고 서비스가 직접 호출한다.
- 게이트웨이 화이트리스트는 `GET /products`처럼 `METHOD path` 형식으로 메서드별 공개를 지정할 수 있다(메서드 생략 시 전체 공개).

## 인프라

`docker-compose.yml` — 회원·인증(`copa-user`)용 MySQL 8.0 + Redis 7.2, 상품(`copa-product`)용 MySQL 8.0 + Redis 7.2(조회 캐시),
재고(`copa-inventory`)용 MySQL 8.0, 결제(`copa-payment`)·주문(`copa-order`)·쿠폰(`copa-coupon`)용 MySQL 8.0,
이벤트 버스 **Kafka**(KRaft 단일 노드).

```bash
docker compose up -d
```

- `copa-auth-mysql`: 3306, db=`copa_auth`, user=`copa`/`copa` (root=`root`)
- `copa-auth-redis`: 6379 (appendonly)
- `copa-product-mysql`: 3307→3306, db=`copa_product`, user=`copa`/`copa` (root=`root`)
- `copa-product-redis`: 6380→6379 (상품 상세 조회 Look-Aside 캐시 전용)
- `copa-inventory-mysql`: 3308→3306, db=`copa_inventory`, user=`copa`/`copa` (root=`root`)
- `copa-payment-mysql`: 3309→3306, db=`copa_payment`, user=`copa`/`copa` (root=`root`)
- `copa-order-mysql`: 3310→3306, db=`copa_order`, user=`copa`/`copa` (root=`root`)
- `copa-coupon-mysql`: 3311→3306, db=`copa_coupon`, user=`copa`/`copa` (root=`root`)
- `copa-kafka`: 9092 (KRaft 단일 노드, advertised `localhost:9092`). 첫 이벤트 흐름은 상품 생성 → 재고 시드(토픽 `product-events`).
  상품 변경 → 검색 색인 흐름은 토픽 `product-search-events`(copa-product outbox → copa-product 색인기 → Elasticsearch).
  주문 Saga·쿠폰 선착순의 Kafka 전환은 후속 단계.
- `copa-elasticsearch`: 9200 (single-node). ELK 로그 저장과 **상품 검색 색인(`products`)** 을 겸한다.

> 참고: `.clauderules`는 PostgreSQL을 명시하지만 설계 문서와 실제 인프라(`docker-compose.yml`)는
> **MySQL 8.0** 기준이다. 이 프로젝트는 MySQL로 진행하며, Flyway도 `flyway-mysql`을 쓴다.

## 빌드 / 실행

각 모듈 디렉토리에서 Gradle Wrapper 사용:

```bash
cd copa-user && ./gradlew build       # 빌드
cd copa-user && ./gradlew test        # 테스트
cd copa-user && ./gradlew bootRun     # 실행
```

## Git 워크플로

- 작업 브랜치: `work/{팀번호}-{영문이름}` (현재: `work/4-changhoon`)
- 제출: `work/...` → `project/{팀번호}-{영문이름}` PR 후 병합. (`README.md` 참고)
