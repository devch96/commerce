# 15주차 5일차 — 전체 서비스 설계 정합성 감사 & Kafka 도입(상품 생성 → 재고 시드, Transactional Outbox)

> 7개 서비스를 설계 원문과 대조해 설계 차이·안티패턴·보안 위험을 감사하고, 즉시 수정 가능한 항목을 패치. 이어서 첫 Kafka 이벤트 흐름을 **Transactional Outbox**로 도입(상품 생성 → 재고 자동 시드).

## 🎯 목표

- 게이트웨이~쿠폰 7개 서비스를 **설계 문서 원문**과 대조해 ① 설계 대비 차이점 ② 안티패턴 ③ 보안 위험을 정리.
- 위험/난이도가 낮고 가치가 높은 **즉시 수정 항목**부터 패치.
- 동기 REST 골격에 **Kafka 이벤트**를 점진 도입. 시작 흐름은 **상품 생성 → 재고 시드**(지금까지 끊겨 있던 공백).

## 🛠️ 작업 내용

### 1. 전체 서비스 감사 (설계 대조)
- 서비스별로 설계 문서(03 회원·04 상품·05 재고·06 주문·07 결제·08 쿠폰·02 공통)와 코드를 대조.
- **공통 위험**: 모든 `/internal/**`이 공개 API와 같은 포트·무인증(IDOR/임의 결제·재고 조작 가능), JWT 시크릿 평문 기본값, Access/Refresh 토큰 미구분.
- **서비스별 핵심**: gateway CORS(`*`+credentials), user 역할변경 시 세션 무효화 누락·`V3 phone NOT NULL` 무 DEFAULT, product 공개 상세가 HIDDEN 노출, payment 환불 흐름·`@Version` 부재, order 할인 상한 미검증·멱등키 부재·결제 후 confirm 복구 부재, coupon RESERVED TTL 부재·N+1.
- **문서 정합성**: `CLAUDE.md`의 설계 문서 번호가 실제 Obsidian 볼트와 한 칸씩 어긋나 있던 것을 발견.

### 2. 즉시 수정 (안전 코드 픽스 + 문서 + 공개 상세 차단)
- **order 할인액 상한 검증** — `Order.applyCouponDiscount`에 `0 ≤ discount ≤ totalAmount` 강제(위반 시 `COUPON_NOT_APPLICABLE`). 쿠폰 응답을 신뢰 경계로 보고 0원·음수 결제 차단.
- **coupon 낙관적 락 → 409** — `GlobalExceptionHandler`에 `ObjectOptimisticLockingFailureException`→409 매핑(동시 발급·예약 충돌을 500이 아닌 재시도 가능 충돌로 구분).
- **coupon N+1 제거** — `UserCouponRepository.findByUserId`를 `join fetch uc.coupon`으로(내 쿠폰 조회).
- **product 공개 상세 비공개 상태 차단** — `getPublicProduct` 신설(HIDDEN/DISCONTINUED→404), 공개 `GET /products/{id}`가 이를 사용. 내부 API(`getProduct`)는 전체 접근 유지.
- **CLAUDE.md 설계 문서 번호 정정** — 실제 볼트(`02 빌드순서/03 회원인증/04 상품/05 재고/06 주문/07 결제/08 쿠폰/09 검색/10 리뷰/11 AI`)와 일치하도록 수정.

### 3. Kafka 도입 — 상품 생성 → 재고 시드 (Transactional Outbox)
- **인프라**: docker-compose `copa-kafka` 추가(KRaft 단일 노드, 9092, advertised `localhost:9092`). 기동·healthy 확인.
- **Producer(copa-product)**:
  - 의존성 `spring-kafka`, 메인에 `@EnableScheduling`.
  - Flyway `V11__create_outbox_events.sql`(`outbox_events`: aggregate/eventType/topic/messageKey/payload/createdAt/publishedAt + 미발행 인덱스).
  - `OutboxEvent` 엔티티 / `OutboxEventRepository`(`findUnpublished`) / `OutboxRecorder`(상품과 **같은 트랜잭션**에 이벤트 적재, payload는 JSON 문자열) / `OutboxRelay`(`@Scheduled` 폴링→Kafka 발행→`markPublished`).
  - `ProductService.createProduct`에서 `outboxRecorder.recordProductCreated(product)` 호출(옵션 leaf는 `ProductOptions.flatten`, 옵션 없으면 `optionKey=""`+stockQuantity).
  - producer 설정: `acks=all` + `enable.idempotence=true`, String 직렬화. 토픽 `product-events`.
  - `OutboxRelay`가 발행 시 `ProducerRecord`에 **`eventType` 헤더**를 싣는다(outbox 행의 eventType 재사용) → 소비자가 payload 파싱 없이 헤더로 라우팅.
- **Consumer(copa-inventory)**:
  - 의존성 `spring-kafka`. `ProductEventConsumer`(`@KafkaListener(..., containerFactory="productEventListenerContainerFactory")`) — eventType 분기는 리스너 밖으로 빼고 멱등 시드만 담당. 파싱 실패 시 skip.
  - **eventType 라우팅은 `RecordFilterStrategy`로**: `KafkaConfig`의 컨테이너 팩토리에 `ProductEventRecordFilter`를 등록. 한 토픽(`product-events`)에서 `eventType` **헤더**가 PRODUCT_CREATED인 것만 통과(나머지 폐기), `setAckDiscarded(true)`로 폐기 레코드 오프셋도 커밋. 필터는 payload를 역직렬화하지 않아 가볍다.
  - `InventoryService.seedIfAbsent` — **insert-if-absent**(이미 있으면 절대 덮어쓰지 않음). 중복 전달로 예약/차감된 재고가 초기값으로 리셋되는 것을 방지. `uk_inventory_product_option` 위반도 멱등 흡수.
  - consumer 설정: `auto-offset-reset=earliest`, String 역직렬화.
- **테스트 격리**: 브로커 없는 테스트에서 producer는 `copa.outbox.relay.enabled=false`, consumer는 `spring.kafka.listener.auto-startup=false`로 컨텍스트만 로딩.

### 4. 이벤트 DTO 정리 + 역직렬화 테스트
- `ProductCreatedEvent`에서 `@JsonCreator`/`@JsonProperty` 제거(둘 다).
  - producer는 **직렬화 전용**이라 생성자 힌트가 무의미(게터로 직렬화).
  - consumer는 Spring Boot의 `-parameters` 컴파일 + `ParameterNamesModule`로 **생성자 파라미터 이름 바인딩**. `@JsonIgnoreProperties(ignoreUnknown=true)`만 유지(생산자 추가 필드 무시).
- `ProductCreatedEventTest`(`@JsonTest`, 앱과 동일 ObjectMapper) 추가 — 어노테이션 없이 역직렬화되는지·모르는 필드(eventId 등) 무시하는지 검증. 리스너가 테스트에서 꺼져 있어 생기는 사각지대를 단위 테스트로 보강.
- `ProductEventRecordFilterTest` 추가(3건) — 헤더 PRODUCT_CREATED 통과 / 다른 타입 폐기 / 헤더 없음 폐기. 필터도 리스너가 꺼진 테스트에선 실행되지 않으므로 별도 클래스로 빼서 단위 검증.

### 5. 검증
- copa-order / copa-coupon / copa-product / copa-inventory **컴파일·테스트 전부 BUILD SUCCESSFUL**.
- `ProductCreatedEventTest`(1건)·`ProductEventRecordFilterTest`(3건) 실행 확인, `@SpringBootTest`(InventoryConcurrencyTest)로 `KafkaConfig` 빈·`containerFactory` 연결 컨텍스트 로딩 확인.
- `docker compose config` valid, Kafka 브로커 런타임 healthy(토픽 조회 정상).

## 🧭 주요 설계 결정

- **Outbox 채택(afterCommit 발행 대신)** — 상품 저장과 이벤트 적재를 한 트랜잭션으로 묶어 유실 0(at-least-once). 발행 실패 시 트랜잭션 롤백 → 다음 폴링 재시도. 소비자는 멱등.
- **payload = JSON 문자열, 서비스별 DTO 분리** — `JsonDeserializer` 신뢰 패키지/타입 헤더 결합을 피하고 각 서비스가 자기 DTO를 소유(생산자 스키마 변화에 `ignoreUnknown`으로 내성).
- **한 토픽·다중 eventType 라우팅 = `RecordFilterStrategy`(헤더 기반)** — eventType 분기를 리스너 `if`가 아니라 `KafkaConfig`의 필터로. 필터는 역직렬화 전 단계라 payload를 파싱하지 않고 `eventType` 헤더만 읽어 가볍다(리스너에서 또 파싱하는 이중 비용 회피). 그래서 producer가 eventType을 헤더로 싣는다.
- **시드는 insert-if-absent(절대 덮어쓰기 금지)** — 기존 `register`(절대값 설정)는 보정용으로 남기고, 이벤트 소비는 멱등 시드만. 중복/재전송에도 운영 중 재고가 안전.
- **상품 생성 → 재고 시드를 첫 흐름으로** — 지금까지 product가 inventory를 호출하지 않아 재고가 수동 시드였던 공백을 메움. 주문 Saga의 Kafka 전환보다 부작용이 작아 도입 1순위로 적합.
- **즉시 수정은 안전 항목만** — CORS 강화·JWT fail-fast는 dev/프론트 영향 가능성으로 제외하고 후속으로.

## 🚧 미구현 / 다음

- **설계 차이 후속(우선)**: user `V3 phone NOT NULL` DEFAULT, payment `@Version` + 환불/정산 흐름, order 멱등키(Idempotency-Key) + 결제 후 confirm 영구 실패 복구 스케줄러, coupon RESERVED TTL 해제.
- **공통 보안**: `/internal/**` 서비스 간 인증(공유 시크릿/mTLS/네트워크 격리), JWT 시크릿 fail-fast, Access/Refresh 토큰 `type` 클레임 구분, gateway CORS 화이트리스트.
- **Kafka 확장**: 상품 수정/삭제 이벤트, 검색 색인(`product-events` 재사용), 주문 Saga의 Kafka 전환, DLT/재시도 정책·Outbox 정리(발행 완료 행 아카이브).

## ⚠️ 트레이드오프 / 주의

- **Outbox 릴레이가 트랜잭션 내 외부 I/O** — `@Scheduled` + `@Transactional` 안에서 Kafka 발행을 동기 대기(`get(timeout)`). dev/학습 단계에선 단순·정확하나, 발행 지연 시 트랜잭션·커넥션 점유. 추후 발행/표시 분리 검토.
- **소비자 역직렬화는 `-parameters`에 의존** — Spring Boot Gradle 플러그인이 기본 on. 끄면 깨지므로 `ProductCreatedEventTest`로 회귀 방지.
- **신규 마이그레이션·인프라** — copa-product `V11`(새 테이블, 충돌 없음). 기동 전 `docker compose up -d copa-kafka`(+ 각 MySQL) 필요. consumer 그룹 `copa-inventory`는 `earliest`라 첫 구독 시 누적 이벤트부터 소비.
- **at-least-once** — 중복 전달 가능. 재고 시드는 `seedIfAbsent` + 유니크로 멱등 보장.
- **라이브 E2E 미수행** — 컴파일/테스트/브로커 기동까지 검증. 실제 "상품 등록 → 재고 자동 시드" 통합 확인은 두 앱 + MySQL 기동 후 진행 예정.
