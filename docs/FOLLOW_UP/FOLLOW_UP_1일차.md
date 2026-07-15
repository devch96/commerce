# 후속조치 1일차 — 대규모 트래픽 선착순 예매 시스템 (`copa-ticket`)

> 주차별 커리큘럼(13~17주차)이 끝나 이후 작업은 `docs/FOLLOW_UP/`(리포지토리)과
> Obsidian `Final/후속조치/`(설계 고민 기록)에 정리한다.
>
> 선착순 쿠폰(17주차 5일차)이 검증한 "Redis Lua 원자 연산 + Kafka 비동기 DB 반영" 골격 위에,
> 그때 후속 과제로 남겼던 **가상 대기열(08-C)** 을 얹어 티켓팅 규모의 트래픽을 받는 예매 서비스를 신설했다.

## 🎯 목표

- **초과 판매 0 · 중복 예매 0** — 좌석이라는 한정 자원에 수십만 요청이 몰려도 정합성이 깨지지 않는다.
- **유량 제어(대기열)** — 정합성은 원자 연산이 지키지만, 트래픽 자체가 Redis·앱 스레드를 직격하지 않도록
  대기열이 유입을 직렬화한다. 진입 순서 보존(공정성)은 부수 효과.
- **DB는 핫패스 밖으로** — 발권 응답은 Redis 확정 즉시 반환, DB는 Kafka 컨슈머가 최종 일관성으로 반영.

## 🏗️ 아키텍처 (신규 서비스 `copa-ticket`, 8087)

```
사용자 ─▶ POST /events/{id}/queue          (대기열 진입: ZSET, score=진입 시각)
      ─▶ GET  /events/{id}/queue/status    (폴링: WAITING(순번)/ADMITTED/ISSUED)
                   ▲
      QueueAdmissionScheduler (1초마다 상위 N명 pop + 입장 허가 키 SETEX — queue_admit.lua 원자)
                   │
사용자 ─▶ POST /events/{id}/tickets         (발권: ticket_issue.lua 원자)
              ① 입장 허가 검증 ② 1인 1매 ③ 좌석 차감 ④ 입장권 소모
      ─▶ 통과분만 Kafka ticket-issued 발행(동기 대기, 실패 시 Redis 보상)
      ─▶ TicketIssuedConsumer가 DB 멱등 INSERT ((event_id,user_id) 유니크 최종 방어선)
```

Redis 키 4종 (`copa-ticket-redis`, 6382):

| 키 | 타입 | 역할 |
|---|---|---|
| `ticket:{id}:stock` | String | 잔여 좌석. **존재 자체가 "오픈" 신호** |
| `ticket:{id}:issued` | Set | 발권자(1인 1매). 재오픈에도 보존 |
| `ticket:{id}:queue` | ZSET | 대기열(score=진입 millis) |
| `ticket:{id}:entry:{uid}` | String(TTL) | 입장 허가. 별도 토큰 값 없이 **키 존재**로 판정 |

## 🛠️ 작업 내용

### 1. 발권 Lua에 대기열 우회 차단을 내장
- 쿠폰 발급 Lua(재고+1인1매)에 **입장 허가 검증(-4)과 입장권 소모(DEL)** 를 추가 — 대기열을 건너뛴
  직접 발권 호출이 서비스 코드가 아니라 원자 연산 수준에서 거부된다.
- 입장 허가는 랜덤 토큰이 아니라 `ticket:{id}:entry:{uid}` **키 존재**로 판정. 게이트웨이 서명 신원
  토큰(17주차 6일차)이 userId를 이미 보장하므로 토큰 값은 잉여이고, 값 파생/유출 걱정도 없다.

### 2. 대기열 입장을 단일 Lua로(pop + 허가 부여)
- `queue_admit.lua`: ZRANGE 상위 N → ZREM → 각자 entry 키 SETEX(TTL 300s). pop과 허가 사이
  크래시로 "대기열에서 빠졌는데 허가는 못 받은" 유실 창을 제거.
- 초당 입장 인원 = `admit-batch-size(100) × (1000/admit-interval-ms(1000))` — 발권 유입의 유량 제어 밸브.
- **폴링 응답에 서버 계산값 탑재**: WAITING 응답에 `ahead`(내 앞 대기)·`estimatedWaitSeconds`
  (= ceil(position/배치) × 주기 — 입장 속도는 서버 설정이라 서버만 계산 가능)·`pollAfterMs`
  (예상 대기의 1/10을 [1s,10s]로 클램프)를 내려준다. 순번이 먼 대기자일수록 폴링을 드물게
  **서버가 지시**해, 수만 명이 일괄 최단 주기로 폴링하는 자기 유발 부하를 통제한다.
- 트레이드오프: entry 키를 스크립트 안에서 동적 구성하므로 **Redis Cluster 불가**(단일 노드 전제, 주석 명시).

### 3. 발행 실패 보상에 "입장권 재부여" 추가
- 쿠폰과 동일한 SREM+INCR 보상에 더해 entry 키를 재-SETEX한다 — 발행 실패가 사용자를
  대기열 맨 뒤로 되돌리는 이중 불이익을 막고, 재시도가 즉시 가능하다.

### 4. 취소는 멱등이 아니라 409 — 좌석 과복원 차단
- 취소 = DB 전이(ISSUED→CANCELLED) 성공 **후** Redis 복원(SREM + 오픈 중이면 INCR).
- 중복 취소를 no-op으로 받으면 복원 INCR이 두 번 실행돼 **초과 판매 방향**으로 어긋난다 →
  이미 취소된 건은 `TICKET_NOT_CANCELABLE`(409)로 끊는다.

### 5. ticketNo를 발행 시점에 확정해 이벤트에 탑재
- 컨슈머가 번호를 생성하면 Kafka 재배달마다 다른 번호가 나와 멱등이 깨진다. 프로듀서에서
  `TKT-yyyyMMdd-XXXXXX`(orderNo 규약)를 확정해 싣고, 컨슈머는 선존재 검사 + 유니크로 멱등 INSERT.

### 6. 재오픈 시드 = 총 좌석 − 발권자 수
- 쿠폰 open은 관리자 지정 수량을 그대로 덮어썼지만, 예매는 좌석 총량이 고정이므로
  `totalSeats − SCARD(issued)`로 계산해 재오픈이 초과 시드가 되지 않게 했다.

### 7. 인프라·문서
- docker-compose: `copa-ticket-mysql`(3312)·`copa-ticket-redis`(6382)·`copa-ticket`(expose 8087) 추가.
- 게이트웨이: `/events/**`·`/tickets/**`·`/admin/events/**` 라우팅. 화이트리스트는 `GET /events`·
  `GET /events/*`만 — AntPathMatcher의 `*`는 한 세그먼트라 `/events/{id}/queue/**`는 인증 유지.
- CLAUDE.md 모듈표·인프라 목록 갱신, Obsidian `설계/12. 예매 서비스` + `후속조치/1일차` 기록.

## ✅ 검증

- `copa-ticket` 단위 테스트 22건: 발권 Lua 코드 분기 5종(성공/미오픈/중복/미입장/매진),
  발행 실패 보상(SREM+INCR+입장권 재부여), 재오픈 시드 계산, 취소(소유자·중복·정상),
  대기열 진입/폴링 상태 4종 + 예상 대기·폴링 간격 계산, 컨슈머 멱등 INSERT.
- `copa-ticket`·`copa-gateway` gradle build 통과(게이트웨이는 GatewayProperties 생성자 확장 반영).

## ⚠️ 남은 트레이드오프 / 후속

- **결제 미연동(Phase 1)** — 현재 발권=확정. Phase 2: 발권을 좌석 선점(RESERVED)으로 낮추고
  결제 데드라인 + 미결제 자동 해제(재고 TTL 스케줄러 패턴 재사용).
- **입장 스케줄러 다중 인스턴스** — 현재 단일 인스턴스 전제. 수평 확장 시 분산 락(ShedLock 등) 필요.
- **폴링 부하** — `pollAfterMs` 서버 지시로 1차 완화. 근본 전환은 SSE/WebSocket 푸시.
- **대사(reconciliation)** — 보상 실패로 잠긴 좌석(로그만 남김)은 이벤트 종료 후 Redis↔DB 대조 배치로 정정.
- **k6 부하 실측** — 동시 N천 요청에서 초과 발권 0 실증(선착순 쿠폰 것과 함께 후속).