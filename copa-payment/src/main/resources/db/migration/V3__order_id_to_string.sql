-- 주문 참조를 주문 서비스의 외부 주문번호(orderNo, 예: ORD-20260704-8F3K2M)로 전환.
-- 엔티티(Payment.orderId)는 이미 String이었으나 컬럼이 BIGINT여서 숫자 문자열만 저장 가능했다.
-- 유니크 제약(uk_payments_order_id)은 컬럼 타입 변경 후에도 유지된다.
ALTER TABLE payments
    MODIFY order_id VARCHAR(30) NOT NULL;
