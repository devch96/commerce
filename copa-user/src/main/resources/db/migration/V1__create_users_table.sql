CREATE TABLE users
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    name       VARCHAR(30)  NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    created_at DATETIME,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
