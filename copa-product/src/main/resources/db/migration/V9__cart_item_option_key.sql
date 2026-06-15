-- 옵션별 장바구니 항목 지원: option_key 추가.
-- 옵션 없는 상품은 빈 문자열('')을 쓴다(NULL은 유니크에서 중복 허용이라 수량 누적이 깨짐).
ALTER TABLE cart_items
    ADD COLUMN option_key VARCHAR(255) NOT NULL DEFAULT '';

-- 한 회원의 같은 상품·옵션이 한 행이 되도록 유니크를 (user_id, product_id, option_key)로 재정의.
ALTER TABLE cart_items
    DROP INDEX uk_cart_items_user_product;

ALTER TABLE cart_items
    ADD CONSTRAINT uk_cart_items_user_product_option UNIQUE (user_id, product_id, option_key);
