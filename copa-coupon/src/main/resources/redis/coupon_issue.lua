-- 선착순 쿠폰 발급 원자 연산.
-- KEYS[1] = 재고 키 (coupon:{id}:stock)
-- KEYS[2] = 발급자 집합 키 (coupon:{id}:issued)
-- ARGV[1] = userId
-- 반환:  1 = 발급 성공
--       -1 = 재고 소진(품절)
--       -2 = 이미 발급받음(1인 1매)
--       -3 = 이벤트 미오픈(재고 키 없음)
-- SISMEMBER→GET→DECR/SADD를 단일 Lua로 원자 실행해 초과 발급·중복 발급을 원천 차단한다.
if redis.call('EXISTS', KEYS[1]) == 0 then
  return -3
end
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
  return -2
end
local stock = tonumber(redis.call('GET', KEYS[1]))
if stock == nil or stock <= 0 then
  return -1
end
redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])
return 1
