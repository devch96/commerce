-- 상품 옵션(무한 뎁스 JSON 트리, leaf = 선언적 재고)과 옵션 할인 규칙을 JSON 컬럼으로 추가.
-- 옵션별 재고의 권위 원천은 별도 재고 서비스(optionKey 매핑)이며, 여기 leaf는 시드/표시용이다.
ALTER TABLE products
    ADD COLUMN options          JSON NULL,
    ADD COLUMN option_discounts JSON NULL;
