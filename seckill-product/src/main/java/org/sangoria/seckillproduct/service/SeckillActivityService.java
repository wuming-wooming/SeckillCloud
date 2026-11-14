package org.sangoria.seckillproduct.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.sangoria.common.cache.CacheKey;
import org.sangoria.common.exception.BizException;
import org.sangoria.common.result.ErrorCode;
import org.sangoria.seckillproduct.entity.SeckillActivity;
import org.sangoria.seckillproduct.mapper.SeckillActivityMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SeckillActivityService {

    private static final String NULL_MARK = "NULL";
    private static final long TTL_JITTER_SECONDS = 300;

    // ---------- T2.6 幂等状态机 ----------
    private static final String DEDUCT_STATE_PROCESSING = "PROCESSING";
    private static final String DEDUCT_STATE_SUCCESS    = "SUCCESS";
    private static final long   MIN_WARMUP_TTL_SECONDS  = 3600L;

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

    // ==========================================================
    //  T2.5 库存预热
    // ==========================================================

    /**
     * 将 DB available_stock 写入 Redis。
     *
     * @param activityId 活动 ID
     * @param force      true=运维强制覆盖（活动已开始也允许）；false=仅活动开始前允许
     */
    public void warmupStock(Long activityId, boolean force) {
        SeckillActivity activity = requireById(activityId);

        LocalDateTime now = LocalDateTime.now();
        // 活动已开始（含正好开始）且非强制 → 拒绝
        if (!force && !now.isBefore(activity.getStartTime())) {
            throw new BizException(ErrorCode.SECKILL_WARMUP_FORBIDDEN);
        }

        // TTL = 活动剩余时间 + 1h，最少 1h
        long ttlSec = Math.max(
                MIN_WARMUP_TTL_SECONDS,
                Duration.between(now, activity.getEndTime()).getSeconds() + MIN_WARMUP_TTL_SECONDS);

        redis.opsForValue().set(
                CacheKey.SECKILL_STOCK.key(activityId),
                String.valueOf(activity.getAvailableStock()),
                ttlSec, TimeUnit.SECONDS);

        log.info("warmup stock ok: activityId={}, stock={}, ttlSec={}, force={}",
                activityId, activity.getAvailableStock(), ttlSec, force);

        // 追加：写入活动元数据快照（startTime/endTime/status），供 seckill 服务做时间校验
        String metaKey = CacheKey.ACTIVITY_META.key(activityId);
        java.util.Map<String, String> meta = java.util.Map.of(
                "startTime", activity.getStartTime().toString(),  // ISO-8601
                "endTime",   activity.getEndTime().toString(),
                "status",    String.valueOf(activity.getStatus()),
                "productId", String.valueOf(activity.getProductId()),
                "seckillPrice", activity.getSeckillPrice().toPlainString());
        redis.opsForHash().putAll(metaKey, meta);
        redis.expire(metaKey, ttlSec, TimeUnit.SECONDS);

        log.info("warmup meta ok: activityId={}, start={}, end={}, status={}",
                activityId, activity.getStartTime(), activity.getEndTime(), activity.getStatus());
    }

    // ==========================================================
    //  T2.6 DB 扣库存（Redis 状态机幂等）
    // ==========================================================

    /**
     * 条件扣减 DB 库存，Redis 状态机保证 requestId 幂等。
     *
     * @return true=扣减成功或幂等命中；异常由 BizException 表达
     */
    public boolean deductStock(Long activityId, String requestId, int quantity) {
        if (requestId == null || requestId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (quantity <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }

        String key = CacheKey.DEDUCT_IDEM.key(requestId);

        // 1) 抢占处理权（SETNX）
        Boolean acquired = redis.opsForValue().setIfAbsent(
                key, DEDUCT_STATE_PROCESSING, Duration.ofSeconds(CacheKey.DEDUCT_IDEM.defaultTtlSeconds()));

        if (!Boolean.TRUE.equals(acquired)) {
            // 2) 抢占失败：读状态
            String state = redis.opsForValue().get(key);
            if (DEDUCT_STATE_SUCCESS.equals(state)) {
                return true; // 幂等返回
            }
            // PROCESSING（并发）或 null（刚过期）→ 保守抛 2006，交对账/重试
            throw new BizException(ErrorCode.SECKILL_REQUEST_PROCESSING);
        }

        // 3) 抢占成功：执行条件 UPDATE
        try {
            int rows = activityMapper.deductStock(activityId, quantity);
            if (rows == 0) {
                // 确定性失败：释放 key，允许后续重试
                redis.delete(key);
                throw new BizException(ErrorCode.SECKILL_STOCK_EMPTY);
            }
            // 4) 成功：置 SUCCESS
            redis.opsForValue().set(
                    key, DEDUCT_STATE_SUCCESS,
                    CacheKey.DEDUCT_IDEM.defaultTtlSeconds(), TimeUnit.SECONDS);
            return true;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // 5) 不确定性失败：保留 PROCESSING，不删 key，交 T5.3 对账
            log.error("deduct stock DB error, keep PROCESSING for reconciliation, "
                            + "activityId={}, requestId={}, qty={}",
                    activityId, requestId, quantity, e);
            throw new BizException(ErrorCode.BIZ_ERROR);
        }
    }
}