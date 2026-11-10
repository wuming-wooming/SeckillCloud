package org.sangoria.seckillproduct.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.sangoria.common.cache.CacheKey;
import org.sangoria.common.exception.BizException;
import org.sangoria.common.result.ErrorCode;
import org.sangoria.seckillproduct.entity.SeckillActivity;
import org.sangoria.seckillproduct.mapper.SeckillActivityMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;


import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
public class SeckillActivityService {

    private static final String NULL_MARK = "NULL";
    private static final long TTL_JITTER_SECONDS = 300;

    private final SeckillActivityMapper activityMapper;
    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    public SeckillActivityService(SeckillActivityMapper activityMapper,
                                  StringRedisTemplate redis,
                                  JsonMapper jsonMapper) {
        this.activityMapper = activityMapper;
        this.redis = redis;
        this.jsonMapper = jsonMapper;
    }

    // ---------- 详情查询（Cache-Aside + 空值防穿透） ----------

    public SeckillActivity getById(Long id) {
        String key = CacheKey.ACTIVITY_INFO.key(id);

        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            if (NULL_MARK.equals(cached)) return null;
            try {
                return jsonMapper.readValue(cached, SeckillActivity.class);
            } catch (Exception e) {
                redis.delete(key);
            }
        }

        SeckillActivity a = activityMapper.selectById(id);
        if (a == null) {
            redis.opsForValue().set(key, NULL_MARK, 60, TimeUnit.SECONDS);
            return null;
        }

        try {
            String json = jsonMapper.writeValueAsString(a);
            long ttl = CacheKey.ACTIVITY_INFO.defaultTtlSeconds()
                    + ThreadLocalRandom.current().nextLong(-TTL_JITTER_SECONDS, TTL_JITTER_SECONDS);
            redis.opsForValue().set(key, json, Math.max(60, ttl), TimeUnit.SECONDS);
        } catch (Exception ignored) {}
        return a;
    }

    public SeckillActivity requireById(Long id) {
        SeckillActivity a = getById(id);
        if (a == null) {
            throw new BizException(ErrorCode.SECKILL_ACTIVITY_NOT_FOUND);
        }
        return a;
    }

    // ---------- 列表查询（列表空也缓存 "[]"，防穿透） ----------

    public List<SeckillActivity> listActive() {
        String key = CacheKey.ACTIVITY_LIST.key();

        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            try {
                return jsonMapper.readValue(cached, new TypeReference<List<SeckillActivity>>() {});
            } catch (Exception e) {
                redis.delete(key);
            }
        }

        List<SeckillActivity> list = activityMapper.selectList(
                new LambdaQueryWrapper<SeckillActivity>()
                        .eq(SeckillActivity::getStatus, 1)
                        .gt(SeckillActivity::getEndTime, LocalDateTime.now())
                        .orderByAsc(SeckillActivity::getStartTime));

        try {
            // 空 list 序列化为 "[]"，一样缓存，防穿透
            String json = jsonMapper.writeValueAsString(list);
            long ttl = CacheKey.ACTIVITY_LIST.defaultTtlSeconds()
                    + ThreadLocalRandom.current().nextLong(0, 60); // 60s + 0~60s
            redis.opsForValue().set(key, json, Math.max(10, ttl), TimeUnit.SECONDS);
        } catch (Exception ignored) {}
        return list;
    }
}