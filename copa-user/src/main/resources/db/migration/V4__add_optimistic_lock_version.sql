-- 동시 수정(비밀번호 변경·등급 변경·기본 배송지 전환) 보호용 낙관적 락 버전 컬럼.
ALTER TABLE users
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE user_addresses
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
