-- 예매 이벤트 정의(공연/행사). 좌석의 실시간 권위는 Redis(ticket:{id}:stock)이고,
-- 이 테이블은 정의(총 좌석·가격)와 상태(SCHEDULED/OPEN/CLOSED)를 관리한다.
CREATE TABLE ticket_events
(
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100)   NOT NULL,
    venue       VARCHAR(100)   NOT NULL,
    price       DECIMAL(19, 2) NOT NULL,
    total_seats INT            NOT NULL,
    status      VARCHAR(20)    NOT NULL,
    open_at     DATETIME,
    version     BIGINT         NOT NULL DEFAULT 0,
    created_at  DATETIME,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 발권된 예매 인스턴스. (event_id, user_id) 유니크로 1인 1매 보장(Redis 선착순의 최종 방어선).
-- 외부 식별자는 ticket_no(TKT-yyyyMMdd-XXXXXX) — 순차 PK 열거 방지(orderNo 규약과 동일).
-- 외래키 제약은 DDL에 두지 않고 JPA @ManyToOne으로 논리 제어한다(.clauderules).
CREATE TABLE tickets
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    ticket_no    VARCHAR(30) NOT NULL,
    event_id     BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    status       VARCHAR(20) NOT NULL,
    version      BIGINT      NOT NULL DEFAULT 0,
    issued_at    DATETIME,
    cancelled_at DATETIME,
    PRIMARY KEY (id),
    CONSTRAINT uk_tickets_ticket_no UNIQUE (ticket_no),
    CONSTRAINT uk_tickets_event_user UNIQUE (event_id, user_id),
    KEY idx_tickets_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;