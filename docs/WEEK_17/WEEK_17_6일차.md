# 17주차 6일차 — 서비스 간 신뢰 강화(게이트웨이 서명 신원 토큰 · 내부 서비스 토큰 · 포트 차단)

> 게이트웨이가 JWT를 검증해 `X-User-Id`/`X-User-Role`을 **평문 헤더**로 주입하던 구조는
> "게이트웨이만 외부 오픈"이라는 경계 보안(perimeter security)에 의존한다. 경계 안쪽에 발을 들인
> 공격자에게는 무방비였다. 이번 작업은 신뢰의 근거를 **네트워크 위치**에서 **서명 검증**으로 옮겼다.

## 🎯 목표

- **신원 헤더 위조 차단** — 게이트웨이를 우회해 서비스에 직접 `X-User-Role: ADMIN`을 붙이는 공격을 막는다.
- **`/internal/**` 무단 호출 차단** — 게이트웨이를 거치지 않는 서비스 간 API에 호출자 인증을 추가한다.
- **공격 표면 축소** — 앱 서비스가 호스트에 포트를 노출하지 않게 해 우회 접근 경로 자체를 없앤다.
- SSH/비대칭 키 관리 없이 기존 JWT 인프라(HMAC 공유 시크릿)로 구현한다.

## 🧨 무엇이 취약했나

| 공격 | 기존 구조에서 가능 여부 |
|---|---|
| 게이트웨이 우회 + `X-User-Role: ADMIN` 헤더 위조 → 권한 상승 | ✅ 가능 (헤더를 진실로 신뢰) |
| `/internal/inventory/confirm` 등 Saga 내부 API 직접 호출 | ✅ 가능 (인증 장벽 0) |
| 위 공격이 원격에서 | ⚠️ docker-compose가 `8083:8083`처럼 앱 서비스를 **호스트로 직접 매핑** → 배포 시 원격 노출 |

핵심 결함 두 가지: **위치 기반 신뢰**("내부에서 왔으니 믿는다")와 **검증 불가능한 신원 전파**(평문 헤더에 서명 없음).

## 🛠️ 작업 내용

### 1. 게이트웨이 — 서명된 신원 토큰 발급 (`copa-gateway`)
- 사용자 JWT 검증 후, `uid`·`role`을 담은 **단명(60초) HS256 토큰**(`typ=identity`)을 발급해 `X-Copa-Identity`로 전달
  (`InternalTokenIssuer`). 더 이상 원시 `X-User-Id`/`X-User-Role`을 하위로 넘기지 않는다.
- 인입 헤더 strip 대상에 `X-Copa-Identity`·`X-Copa-Service`를 추가 → 클라이언트가 서명 토큰을 위조 주입해도 차단.

### 2. 비즈니스 서비스 6개 — 검증 필터 (`InternalAuthFilter`)
`common/security/` 아래 공통 클래스(`InternalTokenService`·`InternalAuthFilter`·`IdentityHeaderRequestWrapper`·`SecurityFilterConfig`)를
product·order·coupon·payment·user·inventory에 배치. 서블릿 필터가 경계에서 신뢰를 강제한다.

| 경로 | 동작 |
|---|---|
| 게이트웨이 경유 (`/products`, `/orders`, …) | 클라이언트가 붙인 `X-User-*`는 **무조건 제거**, 유효한 `X-Copa-Identity`에서만 `X-User-Id`/`X-User-Role`을 재구성(`IdentityHeaderRequestWrapper`). 위조 신원 토큰이면 401 |
| 내부 (`/internal/**`) | `X-Copa-Service` 서비스 토큰(`typ=service`) 검증. 없거나 위조면 401. 통과 시 인증된 호출자가 넘긴 `X-User-*`는 신뢰(내부 신뢰 구역) |

- 컨트롤러는 여전히 `@RequestHeader("X-User-Id")`를 그대로 읽는다 — 래퍼가 검증된 값으로 헤더를 채우므로 컨트롤러 코드는 무변경.
- `copa.security.enabled` 속성으로 게이팅. 테스트 프로파일(H2)에서는 `false`로 꺼서 기존 컨트롤러 테스트가 헤더를 직접 주입하는 방식을 유지(기존 `copa.search.indexer.enabled` 게이팅과 동일 관례).

### 3. order — 내부 호출에 서비스 토큰 자동 첨부 (`InternalFeignConfig`)
- Feign `RequestInterceptor`가 order의 모든 `/internal/**` 호출(재고·쿠폰·결제·상품)에 서명된 `X-Copa-Service`(`svc=copa-order`) 토큰을 자동 부착.
- 전역 인터셉터라 4개 Feign 클라이언트 전부 커버. 결제 내부 호출이 넘기던 `X-User-Id`는 그대로 유지(서비스 토큰 브랜치가 신뢰).

### 4. docker-compose — 공격 표면 축소
- 앱 서비스 6개의 호스트 포트 매핑을 제거(`ports:` → `expose:`) → **게이트웨이(8000)만 외부 노출**. `localhost:8083/internal/...` 우회가 원천 차단.
- 서비스 간 통신은 컨테이너 이름(`copa-inventory:8083` 등)을 쓰므로 무영향.
- 전 서비스(7개)에 `COPA_INTERNAL_SECRET` 환경변수 주입(게이트웨이·하위 서비스가 같은 시크릿으로 서명/검증).

## 🔑 토큰 두 종류 (요약)

```
X-Copa-Identity  (typ=identity, sub=uid, role)  — 게이트웨이 발급 → 비즈니스 서비스 검증
X-Copa-Service   (typ=service,  svc=copa-order)  — /internal 호출자 발급 → 수신 서비스 검증
```
둘 다 HS256 공유 시크릿(`copa.internal.secret`), TTL 60초. 서명·만료·`typ` 불일치는 모두 401.

## ✅ 검증

- 게이트웨이·6개 서비스 전체 테스트 통과.
- 게이트웨이 필터 테스트 갱신: 원시 헤더 전달 → 서명 토큰(`X-Copa-Identity`) 발급·검증으로 수정.
- product에 `InternalAuthFilterTest` 6종 추가: 유효 신원→헤더 재구성 / 위조 `X-User-Role` 제거 / 위조 신원 토큰 401 / `/internal` 무토큰 401 / 유효 서비스 토큰 통과 / `typ=service` 토큰을 신원으로 오용 시 401.

## ⚠️ 남은 트레이드오프

- **HMAC 공유 시크릿(대칭)** — 시크릿을 가진 내부 서비스는 이론상 토큰을 위조할 수 있다. 비대칭(공개키) 방식이면 서비스는 검증만 가능해
  이 구멍도 닫히지만, 키 배포·관리 부담이 생긴다. 이번엔 키 관리를 피하려 대칭을 택했다.
- **운영 배포 시 `COPA_INTERNAL_SECRET`을 강한 랜덤값으로 반드시 교체**(현재 dev 기본값은 코드/컴포즈에 노출).
- 더 강한 단계는 mTLS·서비스 메시(SPIFFE 신원) — "네트워크 위치로 신뢰하지 않는" Zero-Trust로 위치 기반 신뢰 자체를 제거.