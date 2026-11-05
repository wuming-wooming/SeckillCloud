-- KEYS[1] = 库存 key，例如 seckill:stock:1001
-- 返回: 1 = 扣减成功, 0 = 库存不足
local stock = tonumber(redis.call('GET', KEYS[1]))
if stock and stock > 0 then
    redis.call('DECR', KEYS[1])
    return 1
end
return 0