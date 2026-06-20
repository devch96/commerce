-- Transactional Outbox: 상품 등 도메인 변경과 같은 트랜잭션에 이벤트를 적재한다.
-- 별도 릴레이(스케줄러)가 미발행(published_at IS NULL) 행을 Kafka로 발행한 뒤 published_at을 채운다.
CREATE TABLE outbox_events
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    event_type     VARCHAR(50)  NOT NULL,
    topic          VARCHAR(100) NOT NULL,
    message_key    VARCHAR(100),
    payload        TEXT         NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    published_at   DATETIME(6),
    PRIMARY KEY (id),
    -- 미발행 이벤트(published_at IS NULL)를 생성 순서대로 폴링하기 위한 인덱스.
    KEY idx_outbox_unpublished (published_at, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;