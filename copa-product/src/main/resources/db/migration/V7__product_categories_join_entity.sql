-- 상품-카테고리를 @ManyToMany 조인 테이블 → 조인 엔티티(ProductCategory)로 변경.
-- 대리키(id)와 (product_id, category_id) 유니크를 갖도록 재생성한다(데이터 없음 가정).
DROP TABLE product_categories;

CREATE TABLE product_categories
(
    id          BIGINT NOT NULL AUTO_INCREMENT,
    product_id  BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_categories UNIQUE (product_id, category_id),
    KEY idx_product_categories_product_id (product_id),
    KEY idx_product_categories_category_id (category_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
