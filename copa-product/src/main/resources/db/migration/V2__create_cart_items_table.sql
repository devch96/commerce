CREATE TABLE cart_items
(
    id         BIGINT NOT NULL AUTO_INCREMENT,
    user_id    BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity   INT    NOT NULL,
    added_at   DATETIME,
    PRIMARY KEY (id),
    -- 한 회원의 같은 상품은 한 행으로 관리(담기 시 수량 누적).
    CONSTRAINT uk_cart_items_user_product UNIQUE (user_id, product_id),
    KEY idx_cart_items_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
