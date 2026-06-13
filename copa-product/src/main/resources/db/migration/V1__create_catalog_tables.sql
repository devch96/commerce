CREATE TABLE categories
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    name       VARCHAR(50) NOT NULL,
    parent_id  BIGINT,
    created_at DATETIME,
    updated_at DATETIME,
    PRIMARY KEY (id),
    KEY idx_categories_parent_id (parent_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE products
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    product_code   VARCHAR(60)  NOT NULL,
    seller_id      BIGINT       NOT NULL,
    name           VARCHAR(100) NOT NULL,
    price          BIGINT       NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    stock_quantity INT          NOT NULL,
    description    VARCHAR(2000),
    created_at     DATETIME,
    updated_at     DATETIME,
    PRIMARY KEY (id),
    CONSTRAINT uk_products_product_code UNIQUE (product_code),
    KEY idx_products_seller_id (seller_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 셀러가 선택한 카테고리(값 컬렉션). 물리 FK는 .clauderules에 따라 생성하지 않는다.
CREATE TABLE product_categories
(
    product_id  BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    KEY idx_product_categories_product_id (product_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 조상 클로저(선택 + 모든 조상). 카테고리 필터 조인 대상.
CREATE TABLE product_category_paths
(
    product_id  BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    KEY idx_pcp_product_id (product_id),
    KEY idx_pcp_category_id (category_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE product_images
(
    product_id BIGINT       NOT NULL,
    image_url  VARCHAR(500) NOT NULL,
    KEY idx_product_images_product_id (product_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE product_specs
(
    product_id BIGINT       NOT NULL,
    spec_key   VARCHAR(100) NOT NULL,
    spec_value VARCHAR(500),
    PRIMARY KEY (product_id, spec_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
