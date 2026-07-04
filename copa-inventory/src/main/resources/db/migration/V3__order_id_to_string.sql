-- 주문 참조를 주문 서비스의 외부 주문번호(orderNo, 예: ORD-20260704-8F3K2M)로 전환.
-- 순차 PK 노출을 없애는 주문 서비스 변경에 맞춘다. 기존 BIGINT 값은 문자열로 자동 변환된다.
ALTER TABLE stock_reservation
    MODIFY order_id VARCHAR(30) NOT NULL;
