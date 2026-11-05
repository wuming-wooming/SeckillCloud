package org.sangoria.seckillseckill.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class SeckillService {

    private static final String STOCK_KEY_PREFIX = "seckill:stock:";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> seckillStockScript;

    public SeckillService(StringRedisTemplate redisTemplate,
                          DefaultRedisScript<Long> seckillStockScript) {
        this.redisTemplate = redisTemplate;
        this.seckillStockScript = seckillStockScript;
    }

    /**
     * 尝试扣减一次库存（原子操作）
     * @param activityId 秒杀活动 ID
     * @return true 扣减成功（拿到秒杀资格）; false 库存不足
     */
    public boolean trySeckill(Long activityId) {
        String key = STOCK_KEY_PREFIX + activityId;
        Long result = redisTemplate.execute(
                seckillStockScript,
                Collections.singletonList(key)
        );
        return result != null && result == 1L;
    }
}