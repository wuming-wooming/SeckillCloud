package org.sangoria.seckillseckill.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.sangoria.common.cache.CacheKey;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 秒杀活动元数据快照读取。
 * 数据由 product 服务预热时写入 seckill:activity:meta:{id}（Hash）。
 */
@Slf4j
@Service
public class SeckillMetaService {

    private final StringRedisTemplate redis;

    public SeckillMetaService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * @return null 表示活动不存在或未预热
     */
    public SeckillMeta getMeta(Long activityId) {
        String key = CacheKey.ACTIVITY_META.key(activityId);
        Map<Object, Object> entries = redis.opsForHash().entries(key);
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        try {
            SeckillMeta meta = new SeckillMeta();
            meta.setStartTime(LocalDateTime.parse((String) entries.get("startTime")));
            meta.setEndTime(LocalDateTime.parse((String) entries.get("endTime")));
            meta.setStatus(Integer.parseInt((String) entries.get("status")));
            return meta;
        } catch (Exception e) {
            log.error("meta parse error: activityId={}, entries={}", activityId, entries, e);
            return null;
        }
    }

    @Data
    public static class SeckillMeta {
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Integer status;
    }
}