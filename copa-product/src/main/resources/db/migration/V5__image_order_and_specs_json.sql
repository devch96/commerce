-- 상품 이미지 순서 보장: @OrderColumn(image_order) 대응 컬럼 추가.
ALTER TABLE product_images
    ADD COLUMN image_order INT NOT NULL DEFAULT 0;

-- 상품 스펙을 값 테이블(product_specs) 대신 JSON 컬럼으로 저장.
ALTER TABLE products
    ADD COLUMN specs JSON NULL;

DROP TABLE product_specs;
