-- 결제 취소 라우팅용 PG 구분(pg_provider)과 카카오 결제준비 거래ID(tid) 추가.
ALTER TABLE payments
    ADD COLUMN pg_provider VARCHAR(20) NULL AFTER status,
    ADD COLUMN tid         VARCHAR(100) NULL AFTER pg_transaction_id;
