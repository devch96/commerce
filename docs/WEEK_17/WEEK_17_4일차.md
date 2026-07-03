# 17주차 4일차 — 상품 검색 심화: QueryDSL 동적 검색 & Elasticsearch 전문 검색·집계

> 카테고리 필터만 있던 상품 조회를 두 축으로 확장한다. **QueryDSL**로 RDBMS 정형 동적 검색(keyword·가격범위·카테고리·정렬),
> **Elasticsearch**로 전문 검색(relevance·오타 교정)과 집계를 붙인다. ES 색인은 기존 outbox→Kafka 인프라를 재활용해 이벤트 구독으로 채운다.

## 🎯 목표

- **QueryDSL 동적 검색** — 선택적 조건(keyword·가격범위·카테고리 하위 트리)을 값이 있는 것만 조합하고, 정렬은 화이트리스트로 안전하게 받는다.
- **Elasticsearch 전문 검색·집계** — 설계 문서(09)의 bool 쿼리를 그대로 구현(multi_match+fuzziness relevance, status·category·price filter, 가격/카테고리 집계).
- **이벤트 구독 색인** — 상품 등록/수정/삭제 → `product-search-events` 발행 → 같은 서비스 색인기가 ES에 멱등 upsert/delete(요청 경로와 색인 분리).

## 🛠️ 작업 내용

### 1. QueryDSL 동적 검색 (MySQL)
- `build.gradle`에 `querydsl-jpa:5.1.0:jakarta` + `querydsl-apt`(jakarta classifier) 추가 → 컴파일 시 `Q*` 클래스 생성.
- `QuerydslConfig` — `JPAQueryFactory` 빈.
- `ProductQueryRepository` — 각 조건을 **`BooleanExpression`을 반환하는 술어 메서드**로 분리(조건 없으면 `null` → `where(...)`가 자동 무시). keyword(상품명·설명 부분일치), 가격 goe/loe, 카테고리는 조인 엔티티 **exists 서브쿼리**. 정렬은 `price·name·createdAt` 화이트리스트, count 쿼리 분리 + `PageableExecutionUtils`.
- `ProductSearchCondition`(불변 DTO), `ProductService.searchProducts(...)` — 카테고리는 `CategoryService.collectSubtreeIds`로 하위 트리를 펼쳐 넘김.

### 2. Elasticsearch 전문 검색·집계
- `spring-boot-starter-data-elasticsearch` 추가. `ProductDocument`(index=`products`, name/description=Text, status/productCode=Keyword, price=Double, categoryIds=Long).
- `ProductEsSearchService`(`ElasticsearchOperations` + `NativeQuery`):
  - `must`: keyword를 `name^2`·`description` multi_match(fuzziness=AUTO) / 없으면 match_all.
  - `filter`: 노출 상태(SALE·SOLD_OUT) terms, category term, price **number range**(ES 8.15 variant).
  - `aggs`: 가격 avg/min/max, 카테고리 terms(lterms) → `ProductAggregationResponse`.
- 결과에 relevance `score` 포함(`ProductSearchResult`).

### 3. Kafka 이벤트 구독 색인 (outbox 재활용)
- `OutboxRecorder`에 `recordProductUpserted`/`recordProductDeleted` 추가 → 토픽 **`product-search-events`**(`PRODUCT_UPSERTED`/`PRODUCT_DELETED`). 도메인 변경과 같은 트랜잭션.
- `ProductService` 생성/수정 시 upsert, soft delete 시 delete 이벤트 적재.
- `ProductSearchIndexer`(`@KafkaListener`) — `eventType` 헤더로 라우팅해 ES 멱등 upsert/delete(at-least-once 대응). `copa.search.indexer.enabled`로 게이팅.
- `ProductSearchIndexInitializer` — 시작 시 명시 매핑으로 인덱스 생성(동적 매핑 방지).

### 4. 엔드포인트 & 설정
- `ProductSearchController` — `GET /products/search`(QueryDSL), `/es`(전문 검색), `/es/aggregations`(집계). 조건은 **DTO 생성자 바인딩**(`@ModelAttribute` 생략, 복합 타입 자동 바인딩).
- `application.yaml` — `spring.elasticsearch.uris`, Kafka consumer(그룹·역직렬화·earliest), `copa.search.indexer.enabled`.
- `docker-compose.yml` — copa-product에 `ELASTICSEARCH_URIS` + `copa-elasticsearch` healthy 의존성(ELK 클러스터 재사용).
- 게이트웨이는 기존 화이트리스트 `GET /products/**`가 검색 하위 경로를 공개로 커버(추가 라우트 불필요).

## 🧭 주요 설계 결정

- **검색을 별도 서비스(09) 대신 copa-product에** — 설계 04가 이미 "QueryDSL 동적 검색·통합 검색(ES)"을 상품 서비스 **미구현 항목**으로 명시. 새 모듈/Dockerfile/게이트웨이 라우트 오버헤드 회피.
- **색인은 동기 대신 Kafka 이벤트 구독** — 설계 09의 권장(방식 1). 기존 outbox→Kafka 인프라를 재활용해 요청 경로에서 ES를 분리(색인 장애가 상품 API에 전파되지 않음).
- **QueryDSL은 `BooleanExpression`(null 무시) 술어 방식** — `BooleanBuilder` 대신. 조건별 술어를 메서드로 분리해 재사용·가독성 확보(피드백 반영).
- **카테고리 필터는 exists 서브쿼리** — Product가 카테고리 컬렉션을 들지 않으므로(.clauderules 조인 엔티티) `ProductCategory`에 대한 exists로 필터. 상위 카테고리로 검색해도 하위 상품이 잡히게 하위 트리를 펼침.
- **ES 인덱스 매핑을 선제 생성** — 색인기가 문서를 먼저 저장하면 ES 동적 매핑으로 `status`가 text가 되어 term 필터가 어긋남 → 시작 시 `@Field` 명시 매핑으로 고정.
- **상태 무관 전량 색인 + 쿼리 시 status filter** — HIDDEN→SALE 상태 변경이 재색인(upsert 이벤트)으로 자연 반영. 삭제는 인덱스에서 제거.
- **테스트 컨텍스트에서 ES 리포지토리 스캔 off** — `SimpleElasticsearchRepository` 생성자가 시작 시 클러스터에 연결해 컨텍스트 로드가 실패 → 테스트에서 `spring.data.elasticsearch.repositories.enabled=false` + 색인기 비활성.

## 🔁 설계 문서 대비 변경점

- **04 상품 서비스**: "미구현(다음 작업 후보)"에 있던 **QueryDSL 동적 검색·통합 검색(ES)** 을 구현으로 이동.
- **09 검색 서비스**: 독립 서비스(별도 ES·Consumer) 대신 **copa-product 내부**에 실현. 토픽 `product-search-events` 신설, ES `products` 인덱스는 ELK 로그용 클러스터(`copa-elasticsearch:9200`)를 겸용.
- CLAUDE.md copa-product·인프라 섹션에 검색 2경로·토픽·인덱스 반영.

## 🚧 미구현 / 다음

- **무중단 재색인**(alias 스위칭 v1→v2), **자동완성/오타 교정**(edge n-gram·completion suggester·동의어 사전).
- **실시간 인기 검색어**(Redis `ZINCRBY` 누적), **User Activity 로그 파이프라인**(Kafka→Logstash→ES→Kibana).
- **색인 정합성** — 이벤트 version/timestamp로 오래된 갱신 무시 + 주기적 풀 재동기화.
- **카테고리 트리 캐싱** — `collectSubtreeIds`가 매 검색마다 `findAll()`로 카테고리 전량 로드(쿼리 1회지만 전량). 규모 커지면 캐시로 전환.

## ⚠️ 트레이드오프 / 주의

- **at-least-once → 멱등 소비 의존** — outbox 릴레이는 재발행 가능. 색인기는 id 기준 upsert/delete로 멱등.
- **ES 클러스터 공용** — 상품 검색(`products`)과 ELK 로그(`copa-logs-*`)가 같은 인스턴스. 인덱스명으로 분리되지만 운영에선 리소스 경합 고려.
- **consumer offset earliest** — 새 색인기 그룹이 초기 오프셋부터 읽어 누락을 줄이는 대신, 토픽에 과거 이벤트가 많으면 초기 재색인 부하.
- **검증 범위** — 컴파일 + 전체 테스트(컨텍스트 로드 포함) 통과, `bootJar` 확인. 실제 ES 색인/검색 end-to-end(`docker compose up`) 기동 확인은 미수행.