-- 대기열 상위 N명 입장 허가(원자).
-- KEYS[1] = 대기열 ZSET (ticket:{eventId}:queue, score=진입 시각 millis)
-- ARGV[1] = 입장 배치 크기(N)
-- ARGV[2] = 입장 허가 TTL(초) — 이 시간 안에 발권하지 않으면 허가가 소멸한다
-- ARGV[3] = 입장 허가 키 prefix (ticket:{eventId}:entry:)
-- 반환: 이번 배치에서 입장 허가된 인원 수
-- pop과 허가 키 생성을 한 스크립트에서 실행해 "대기열에서 빠졌는데 허가는 못 받은" 유실 창을 없앤다.
-- 주의: 허가 키를 동적으로 구성하므로 Redis Cluster에서는 쓸 수 없다(단일 노드 전제).
local batch = tonumber(ARGV[1])
local admitted = redis.call('ZRANGE', KEYS[1], 0, batch - 1)
if #admitted == 0 then
  return 0
end
redis.call('ZREM', KEYS[1], unpack(admitted))
for _, userId in ipairs(admitted) do
  redis.call('SET', ARGV[3] .. userId, '1', 'EX', tonumber(ARGV[2]))
end
return #admitted