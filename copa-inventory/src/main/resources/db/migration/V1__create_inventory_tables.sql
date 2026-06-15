-- 상품(옵션)별 가용 재고. 재고의 권위 원천. (product_id, option_key) 유니크로 옵션 leaf와 1:1 매핑.
CREATE TABLE inventory
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    product_id BIGINT       NOT NULL,
    option_key VARCHAR(255) NOT NULL DEFAULT '',
    stock      INT          NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at DATETIME,
    updated_at DATETIME,
    PRIMARY KEY (id),
    CONSTRAINT uk_inventory_product_option UNIQUE (product_id, option_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 주문 단위 재고 예약. 보상(해제)의 근거이자 멱등성 기준. 한 주문이 여러 품목이면 같은 order_id로 여러 행.
CREATE TABLE stock_reservation
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    order_id   BIGINT       NOT NULL,
    product_id BIGINT       NOT NULL,
    option_key VARCHAR(255) NOT NULL DEFAULT '',
    quantity   INT          NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    expires_at DATETIME     NOT NULL,
    created_at DATETIME,
    PRIMARY KEY (id),
    KEY idx_stock_reservation_order_id (order_id),
    KEY idx_stock_reservation_status_expires (status, expires_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
