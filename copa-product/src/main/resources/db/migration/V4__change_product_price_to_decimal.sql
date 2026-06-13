-- 금액은 BigDecimal/DECIMAL로 관리한다(.clauderules 통화 규약).
ALTER TABLE products
    MODIFY COLUMN price DECIMAL(19, 2) NOT NULL;
