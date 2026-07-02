# 17주차 3일차 — 서비스 컨테이너화(멀티스테이지) & 관측성 스택(AOP·ELK·Zipkin·Prometheus/Grafana)

> 지금까지 호스트에서 직접 띄우던 7개 서비스를 각자 멀티스테이지 Dockerfile로 이미지화해 `docker-compose`에 통합하고,
> 로그(ELK)·트레이스(Zipkin)·메트릭(Prometheus/Grafana) 3축 관측성을 도입한다. AOP로 서비스별 공통 로깅도 붙인다.

## 🎯 목표

- 각 서비스를 **멀티스테이지 빌드**로 이미지화(빌드=JDK, 런타임=JRE)하고, 인프라만 있던 `docker-compose.yml`에 앱까지 올려 `up` 한 번으로 전체 스택이 뜨게 한다.
- **AOP 공통 로깅** — 컨트롤러·서비스 계층 진입/완료/소요시간을 서비스마다 반복 코드 없이 남긴다.
- **분산 관측성** — 로그(ELK), 트레이스(Zipkin), 메트릭(Prometheus→Grafana)을 표준 스택으로 도입하고 서비스별 대시보드까지 JSON으로 프로비저닝한다.

## 🛠️ 작업 내용

### 1. 멀티스테이지 Dockerfile (7개 서비스)
- `eclipse-temurin:21-jdk`에서 Gradle Wrapper로 `bootJar` 빌드 → `eclipse-temurin:21-jre`로 jar만 복사(런타임 이미지 축소).
- 래퍼·`build.gradle`·`settings.gradle`를 먼저 복사해 **의존성 레이어 캐시**, `-plain.jar` 제외하고 실행 가능 jar만 취함.
- 각 서비스에 `.dockerignore`(`build/`·`.gradle/` 등) 추가.

### 2. docker-compose 앱 서비스 통합
- 앱 7종 추가(build.context=각 디렉토리). 설정은 전부 **환경변수 오버라이드**(코드 무변경). 컨테이너 간 통신은 서비스명, DB/Redis는 **내부 포트(3306/6379)**.
- `depends_on` + `condition: service_healthy`(DB/Redis/Kafka)로 기동 순서 보장. `JWT_SECRET`은 gateway·user 공유.
- **Kafka 이중 리스너 전환**: 기존 `advertised=localhost:9092`는 컨테이너 간 접근 시 자기 자신을 가리켜 실패 → `EXTERNAL(localhost:9092, 호스트용)` + `INTERNAL(copa-kafka:29092, 컨테이너용)`으로 분리. product·inventory는 `copa-kafka:29092` 사용.

### 3. AOP 공통 로깅
- 6개 서비스에 `LoggingAspect`(`@Aspect`) — `@RestController`·`@Service` 계층을 `@Around`로 감싸 진입(debug)·완료/실패(info/error)·소요시간(ms) 로깅.
- gateway는 WebFlux라 AOP 대신 `RequestLoggingFilter`(`GlobalFilter`) — 메서드·경로·상태코드·소요시간.

### 4. ELK — 로그 파이프라인
- 7개 서비스에 `logstash-logback-encoder` + `logback-spring.xml`(콘솔 + Logstash TCP JSON, `service`·traceId·spanId 포함).
- 흐름: 앱 → `copa-logstash:5000`(json_lines) → `copa-elasticsearch:9200`(인덱스 `copa-logs-YYYY.MM.dd`) → `copa-kibana:5601`.

### 5. Zipkin — 분산 트레이싱
- `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave`. Feign 서비스(order·payment)엔 `feign-micrometer`로 트레이스 전파.
- `management.zipkin.tracing.endpoint=${ZIPKIN_ENDPOINT}`, 개발 샘플링 1.0 → `copa-zipkin:9411`.

### 6. Prometheus + Grafana — 메트릭 & 대시보드
- `spring-boot-starter-actuator` + `micrometer-registry-prometheus`, `/actuator/prometheus` 노출. `copa-prometheus`가 전 서비스 스크래핑(10초).
- `management.metrics.distribution.percentiles-histogram`으로 HTTP 지연 **히스토그램 버킷** 발행(p95/p99 계산용).
- Grafana 데이터소스(Prometheus·Zipkin, uid 고정) + **대시보드 JSON 3종 자동 프로비저닝**:
  - `copa-overview` — `application` 변수(서비스 드롭다운)로 **서비스별** 요청량·에러율·지연 p95/p99·JVM·CPU·GC·스레드·HikariCP.
  - `copa-gateway` — 라우트별 요청량·상태코드·경로 지연.
  - `copa-kafka` — 컨슈머 소비량/lag(inventory)·프로듀서 발행/에러(product)·리스너 처리.

## 🧭 주요 설계 결정

- **멀티스테이지 + JRE 런타임** — 빌드 산출물(gradle 캐시·소스)을 최종 이미지에서 제거해 크기·공격면 축소.
- **코드 무변경, 환경변수만으로 컨테이너화** — application.yaml이 이미 `${ENV:default}` 패턴이라 compose에서 오버라이드만. 호스트 실행도 그대로 유지.
- **로그 전송은 Logback→Logstash TCP** — 파일 마운트/Filebeat 없이 JSON을 바로 쏴 컨테이너 환경에 깔끔. traceId/spanId를 로그에 실어 Kibana↔Zipkin 상호참조.
- **Grafana는 하나의 템플릿 대시보드 + 특화 대시보드** — `application` 변수로 "각 서비스에 맞게"를 커버하고, gateway·kafka는 그 서비스에만 있는 지표라 별도 대시보드로.
- **데이터소스 uid 고정** — 프로비저닝 대시보드가 자동 생성 uid에 흔들리지 않도록 `prometheus`/`zipkin` 고정.

## 🔁 설계 문서 대비 변경점

- 인프라: 인프라 전용이던 `docker-compose.yml`이 **앱 7종 + 관측성 6종(ES·Logstash·Kibana·Zipkin·Prometheus·Grafana)** 까지 포함하는 전체 스택으로 확장.
- 공통 설계(`02`): 관측성(로깅·트레이싱·메트릭) 표준 스택과 AOP 로깅 규약이 새로 추가됨(설계 문서엔 미기재 → 후속 반영 필요).

## 🚧 미구현 / 다음

- **Grafana 알림/알람 룰**, 로그 기반 알림(ElastAlert 류)은 미도입.
- **트레이스↔로그 상관** 을 Grafana에서 클릭 연동(Exemplar/Trace to Logs)까지는 미설정.
- **Kibana 인덱스 패턴/대시보드 자동 프로비저닝** 미적용(현재 인덱스만 생성).
- 설계 문서(`02` 공통 설계)에 관측성 규약 반영, CLAUDE.md 인프라 섹션 갱신.

## ⚠️ 트레이드오프 / 주의

- **리소스** — ES(512m)·Logstash(256m) 힙 등으로 로컬 메모리 요구가 커짐. Docker Desktop 4~6GB 권장.
- **첫 빌드 시간** — 각 Dockerfile이 Gradle 의존성을 새로 받아 초기 `up --build`가 느림(레이어 캐시로 이후 단축).
- **개발용 보안 완화** — ES `xpack.security.enabled=false`, Grafana admin/admin, 샘플링 1.0. 운영 전 전부 조정 필요.
- **검증 범위** — 대표 3개 서비스(단순/reactive/feign) 컴파일 + `docker compose config` 통과까지 확인. 전체 `up --build` 기동·대시보드 실데이터 확인은 미수행.