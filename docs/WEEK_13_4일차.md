# 13주차 4일차 — 상품 상세 조회 Look-Aside 캐시

> copa-product의 상품 상세 조회에 Redis Look-Aside 캐시를 도입(1차).

## 🎯 목표
- 읽기 압도적인 상품 상세 조회의 DB 부하를 캐시로 흡수한다.
- 캐시 일관성(무효화)과 가용성(캐시 장애 격리)을 함께 고려한 1차 기준선을 만든다.

## 🛠️ 작업 내용

### 인프라
- `spring-boot-starter-data-redis` 추가
- 전용 **`copa-product-redis`**(docker-compose, 6380→6379) — `copa-auth-redis`와 분리(서비스별 데이터스토어 격리)
- `application.yaml`에 `spring.data.redis` 설정

### 캐시 계층 (`ProductCacheService`)
- `StringRedisTemplate` + `ObjectMapper`로 `ProductResponse`를 **JSON 문자열**로 저장
- key `product:{id}`, **TTL 10분**
- 직렬화/역직렬화 실패는 **캐시 미스로 흡수**(캐시 장애가 조회를 막지 않게), 깨진 캐시는 삭제

### 조회/무효화 (`ProductService`)
- `getProduct`: 캐시 HIT → 즉시 반환 / MISS → Mongo 조회 후 적재 (Look-Aside)
- `updateProduct`·`deleteProduct`: DB 커밋 **이후(afterCommit)** 키 삭제 — 커밋 전 삭제로 인한 stale 재적재 방지
- 역직렬화 위해 `ProductResponse`에 `@Jacksonized`

### 테스트
- 캐시 HIT(DB 미조회) / MISS(DB 조회 후 put) 유닛 테스트

## 🧭 주요 설계 결정
- **Look-Aside + 무효화(삭제)** — 갱신(rewrite)보다 동시성에서 안전(설계의 "삭제 우선").
- **커밋 후 무효화** — 일관성 윈도우를 줄이는 기준선.
- **TTL 10분 안전망** — 무효화 누락 대비 백스톱.
- **캐시 가용성 우선** — Redis 직렬화 오류 등은 미스로 흡수하고 DB로 폴백.

## 🚧 1차에서 의도적으로 제외 (다음 라운드)
- 부재(null) 캐싱 / 캐시 침투 방지
- 지연 이중 삭제(커밋~afterCommit 사이 좁은 창)
- Thundering herd(분산 락 / PER)
- Hot key(로컬 다층 캐시)
- 목록 조회 캐싱

## ⏭️ 다음
- 부재 캐싱 → thundering herd → hot key 순으로 단계적 강화 검토
