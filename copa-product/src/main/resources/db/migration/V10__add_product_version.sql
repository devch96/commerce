-- 재고/상태/가격 동시 수정 보호용 낙관적 락 버전 컬럼.
ALTER TABLE products
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
