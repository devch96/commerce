CREATE TABLE user_addresses
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    address_name   VARCHAR(50)  NOT NULL,
    receiver_name  VARCHAR(50)  NOT NULL,
    zipcode        VARCHAR(20)  NOT NULL,
    base_address   VARCHAR(255) NOT NULL,
    detail_address VARCHAR(255) NOT NULL,
    is_default     BIT(1)       NOT NULL,
    PRIMARY KEY (id),
    -- 물리 FK 제약은 .clauderules 규칙에 따라 생성하지 않는다. 무결성은 JPA @ManyToOne으로 논리 제어.
    KEY idx_user_addresses_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;