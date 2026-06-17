-- 같은 주문의 동일 옵션에 대한 예약 행이 중복 생성되는 것을 DB 레벨에서 막는다.
-- 애플리케이션의 existsByOrderId 멱등 가드와 reserve 사이의 경쟁(동시 중복 요청 시 이중 차감)을 차단하는 최종 방어선.
-- 한 주문은 (order_id, product_id, option_key)당 최대 1행만 가진다.
ALTER TABLE stock_reservation
    ADD CONSTRAINT uk_stock_reservation_order_product_option
        UNIQUE (order_id, product_id, option_key);
