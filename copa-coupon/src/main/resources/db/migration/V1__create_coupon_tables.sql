-- 쿠폰 정의(템플릿). 발급되면 user_coupons에 사용자별 인스턴스가 생긴다.
CREATE TABLE coupons
(
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    name             VARCHAR(100)   NOT NULL,
    type             VARCHAR(20)    NOT NULL,
    value            DECIMAL(19, 2) NOT NULL,
    max_discount     DECIMAL(19, 2),
    min_order_amount DECIMAL(19, 2) NOT NULL DEFAULT 0,
    expiration_type  VARCHAR(20)    NOT NULL,
    valid_days       INT,
    start_date       DATETIME,
    end_date         DATETIME,
    total_quantity   INT,
    issued_quantity  INT            NOT NULL DEFAULT 0,
    target_type      VARCHAR(20)    NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    version          BIGINT         NOT NULL DEFAULT 0,
    created_at       DATETIME,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 사용자에게 발급된 쿠폰 인스턴스. (coupon_id, user_id) 유니크로 1인 1매 보장.
-- 외래키 제약은 DDL에 두지 않고 JPA @ManyToOne으로 논리 제어한다(.clauderules).
CREATE TABLE user_coupons
(
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    coupon_id         BIGINT         NOT NULL,
    user_id           BIGINT         NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    expires_at        DATETIME       NOT NULL,
    reserved_order_id BIGINT,
    used_order_id     BIGINT,
    discount_amount   DECIMAL(19, 2),
    version           BIGINT         NOT NULL DEFAULT 0,
    issued_at         DATETIME,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_coupons_coupon_user UNIQUE (coupon_id, user_id),
    KEY idx_user_coupons_user_id (user_id),
    KEY idx_user_coupons_reserved_order (reserved_order_id),
    KEY idx_user_coupons_used_order (used_order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
