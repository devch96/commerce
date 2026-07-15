-- 선착순 발권 원자 연산.
-- KEYS[1] = 좌석 재고 키 (ticket:{eventId}:stock)
-- KEYS[2] = 발권자 집합 키 (ticket:{eventId}:issued)
-- KEYS[3] = 입장 허가 키 (ticket:{eventId}:entry:{userId})
-- ARGV[1] = userId
-- 반환:  1 = 발권 성공
--       -1 = 좌석 소진(매진)
--       -2 = 이미 발권함(1인 1매)
--       -3 = 이벤트 미오픈(재고 키 없음)
--       -4 = 입장 미허가(대기열 통과 전 또는 입장 TTL 만료)
-- 입장 검증→중복 검사→재고 차감→입장권 소모를 단일 Lua로 원자 실행해
-- 초과 발권·중복 발권·대기열 우회를 원천 차단한다.
if redis.call('EXISTS', KEYS[1]) == 0 then
  return -3
end
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
  return -2
end
if redis.call('EXISTS', KEYS[3]) == 0 then
  return -4
end
local stock = tonumber(redis.call('GET', KEYS[1]))
if stock == nil or stock <= 0 then
  return -1
end
redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])
redis.call('DEL', KEYS[3])
return 1