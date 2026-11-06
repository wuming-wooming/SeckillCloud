-- KEYS[1] = 库存 key (seckill:stock:{activityId})
-- KEYS[2] = 已购用户 Set (seckill:users:{activityId})
-- ARGV[1] = userId
-- 返回: 1=成功, 0=库存不足, -1=重复秒杀
local stock = tonumber(redis.call('GET', KEYS[1]))
if not stock or stock <= 0 then
    return 0
end
local added = redis.call('SADD', KEYS[2], ARGV[1])
if added == 0 then
    return -1
end
redis.call('DECR', KEYS[1])
return 1