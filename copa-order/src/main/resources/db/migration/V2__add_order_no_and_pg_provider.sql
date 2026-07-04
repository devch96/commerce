-- 외부 노출용 주문번호(order_no) 도입: 순차 PK 열거 방지. 클라이언트 API·서비스 간 참조는 order_no를 쓴다.
-- 기존 행은 생성일+PK 기반 결정적 값으로 백필한다(유니크 보장).
ALTER TABLE orders
    ADD COLUMN order_no VARCHAR(30) NULL AFTER id;

UPDATE orders
SET order_no = CONCAT('ORD-', DATE_FORMAT(COALESCE(created_at, NOW()), '%Y%m%d'), '-', LPAD(id, 6, '0'))
WHERE order_no IS NULL;

ALTER TABLE orders
    MODIFY order_no VARCHAR(30) NOT NULL,
    ADD UNIQUE KEY uk_orders_order_no (order_no);

-- Phase 1에서 선택한 PG 저장(Phase 2 confirm에서 대조해 교차 PG 호출 차단). 이전 주문은 null 허용.
ALTER TABLE orders
    ADD COLUMN pg_provider VARCHAR(20) NULL AFTER coupon_id;
