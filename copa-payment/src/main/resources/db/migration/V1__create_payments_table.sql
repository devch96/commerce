-- 주문 1건당 결제 1건(order_id 유니크 → 멱등성 기준). 가상 PG 승인/취소 결과 보관.
CREATE TABLE payments
(
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    order_id          BIGINT        NOT NULL,
    user_id           BIGINT        NOT NULL,
    amount            DECIMAL(19, 2) NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    pg_transaction_id VARCHAR(100),
    refunded_amount   DECIMAL(19, 2) NOT NULL DEFAULT 0,
    created_at        DATETIME,
    updated_at        DATETIME,
    PRIMARY KEY (id),
    CONSTRAINT uk_payments_order_id UNIQUE (order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
