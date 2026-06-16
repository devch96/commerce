-- 주문. 금액은 통화 규약대로 DECIMAL. 품목은 order_items(다대일 단방향)로 분리.
CREATE TABLE orders
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    user_id         BIGINT         NOT NULL,
    total_amount    DECIMAL(19, 2) NOT NULL,
    discount_amount DECIMAL(19, 2) NOT NULL DEFAULT 0,
    refunded_amount DECIMAL(19, 2) NOT NULL DEFAULT 0,
    coupon_id       BIGINT,
    status          VARCHAR(30)    NOT NULL,
    created_at      DATETIME,
    updated_at      DATETIME,
    PRIMARY KEY (id),
    KEY idx_orders_user_id (user_id),
    KEY idx_orders_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE order_items
(
    id         BIGINT         NOT NULL AUTO_INCREMENT,
    order_id   BIGINT         NOT NULL,
    product_id BIGINT         NOT NULL,
    option_key VARCHAR(255)   NOT NULL DEFAULT '',
    quantity   INT            NOT NULL,
    price      DECIMAL(19, 2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_items_order_id (order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 주문 상태 변경 이력(감사 추적).
CREATE TABLE order_status_history
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    order_id    BIGINT      NOT NULL,
    from_status VARCHAR(30),
    to_status   VARCHAR(30) NOT NULL,
    reason      VARCHAR(200),
    changed_at  DATETIME,
    PRIMARY KEY (id),
    KEY idx_order_status_history_order_id (order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
