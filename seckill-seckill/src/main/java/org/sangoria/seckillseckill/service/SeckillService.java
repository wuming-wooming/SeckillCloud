package org.sangoria.seckillseckill.service;

import lombok.extern.slf4j.Slf4j;
import org.sangoria.common.cache.CacheKey;
import org.sangoria.common.exception.BizException;
import org.sangoria.common.result.ErrorCode;
import org.sangoria.seckillapi.constant.KafkaTopics;
import org.sangoria.seckillapi.dto.SeckillOrderMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SeckillService {

    private static final long RESULT_TTL_SECONDS = 3600L;
    private static final int SUPPORTED_QUANTITY = 1;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> seckillStockScript;
    private final SeckillMetaService metaService;
    private final JsonMapper jsonMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public SeckillService(StringRedisTemplate redisTemplate,
                          DefaultRedisScript<Long> seckillStockScript,
                          SeckillMetaService metaService,
                          JsonMapper jsonMapper,
                          KafkaTemplate<String, String> kafkaTemplate) {
        this.redisTemplate = redisTemplate;
        this.seckillStockScript = seckillStockScript;
        this.metaService = metaService;
        this.jsonMapper = jsonMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    // ==========================================================
    //  T3.5 秒杀下单（requestId 幂等 + 时间校验 + Lua 预减）
    // ==========================================================

    public SeckillResult trySeckill(Long activityId, Long userId, String requestId, int quantity) {
        // 参数兜底（@Valid 已在 Controller 层，此处为二次保护）
        if (requestId == null || requestId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (quantity != SUPPORTED_QUANTITY) {
            // Lua 脚本固定 DECR 1，多件需改 Lua（DECRBY），当前显式拒绝防误用
            throw new BizException(ErrorCode.PARAM_ERROR);
        }

        // ---- 1) 读优化：终态直接返回，避免无谓 SETNX ----
        SeckillResult cached = getResult(requestId);
        if (cached != null) {
            // P1 修复：不同用户复用同一 requestId 时，不能返回别人的结果
            if (!java.util.Objects.equals(cached.getUserId(), userId)) {
                throw new BizException(ErrorCode.PARAM_ERROR);
            }
            if (cached.isTerminal()) return cached;
            // QUEUING：并发同 requestId 或前次未完成
            throw new BizException(ErrorCode.SECKILL_REQUEST_PROCESSING);
        }

        // ---- 2) SETNX 抢占处理权 ----
        String key = CacheKey.SECKILL_RESULT.key(requestId);
        SeckillResult queuing = SeckillResult.queuing(requestId, activityId, userId);
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, toJson(queuing), RESULT_TTL_SECONDS, TimeUnit.SECONDS);

        if (!Boolean.TRUE.equals(acquired)) {
            // 并发竞态：另一请求已抢到，再读一次看是否已完成
            SeckillResult other = getResult(requestId);
            if (other != null) {
                if (!java.util.Objects.equals(other.getUserId(), userId)) {
                    throw new BizException(ErrorCode.PARAM_ERROR);
                }
                if (other.isTerminal()) return other;
            }
            throw new BizException(ErrorCode.SECKILL_REQUEST_PROCESSING);
        }

        // ---- 3) 时间校验 + Lua 预减 ----
        try {
            SeckillMetaService.SeckillMeta meta = metaService.getMeta(activityId);
            if (meta == null) {
                return writeAndReturn(key, queuing.fail(ErrorCode.SECKILL_ACTIVITY_NOT_FOUND));
            }
            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(meta.getStartTime())) {
                return writeAndReturn(key, queuing.fail(ErrorCode.SECKILL_NOT_STARTED));
            }
            if (!now.isBefore(meta.getEndTime())) {  // now >= endTime
                return writeAndReturn(key, queuing.fail(ErrorCode.SECKILL_ENDED));
            }

            String stockKey = CacheKey.SECKILL_STOCK.key(activityId);
            String userKey  = CacheKey.SECKILL_USERS.key(activityId);
            Long luaResult = redisTemplate.execute(
                    seckillStockScript,
                    Arrays.asList(stockKey, userKey),
                    String.valueOf(userId));
            SeckillCode code = SeckillCode.from(luaResult == null ? 0L : luaResult);

            if (code == SeckillCode.SUCCESS) {
                // 1. 构造消息（meta 需已扩展 productId/seckillPrice）
                SeckillOrderMessage msg = new SeckillOrderMessage(
                        requestId, activityId, userId,
                        meta.getProductId(), quantity, meta.getSeckillPrice(), LocalDateTime.now());
                String payload = toJson(msg);

                // 2. 同步发送，等待 acks=all 确认
                try {
                    kafkaTemplate.send(KafkaTopics.SECKILL_ORDER, String.valueOf(activityId), payload)
                            .get(3, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // 发送失败（超时/Broker不可用）：Lua 已 DECR+SADD，绝不能写 FAIL
                    // 保留 QUEUING，交 T5.3 对账扫描 seckill:users:{id} 补发
                    log.error("kafka send failed, keep QUEUING: requestId={}", requestId, e);
                    throw new BizException(ErrorCode.BIZ_ERROR);
                }

                // 3. 消息确已入 Kafka，再写 SUCCESS
                SeckillResult success = queuing.success();
                writeResult(key, success);
                log.info("seckill success: activityId={}, userId={}, requestId={}",
                        activityId, userId, requestId);
                return success;
            } else {
                SeckillResult fail = queuing.fail(code.errorCode());
                writeResult(key, fail);
                log.info("seckill fail: activityId={}, userId={}, requestId={}, code={}",
                        activityId, userId, requestId, code);
                return fail;
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // 执行异常：保守保留 QUEUING，不写终态，交 T5.3 对账
            // 原因：Lua 可能已 DECR + SADD 成功但响应丢失，写 FAIL 会让用户重试拿到 DUPLICATE
            log.error("seckill exec error, keep QUEUING for reconciliation, "
                            + "activityId={}, userId={}, requestId={}",
                    activityId, userId, requestId, e);
            throw new BizException(ErrorCode.BIZ_ERROR);
        }
    }

    // ==========================================================
    //  T3.6 秒杀结果查询
    // ==========================================================

    /**
     * @return null 表示 requestId 未提交过 / 已过期 / 结构不兼容（旧版本数据）
     */
    public SeckillResult getResult(String requestId) {
        String key = CacheKey.SECKILL_RESULT.key(requestId);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) return null;
        try {
            return jsonMapper.readValue(json, SeckillResult.class);
        } catch (Exception e) {
            // 兜底：结构不兼容（如本次字段类型升级留下的旧数据）→ 删除 key 允许重试
            // 场景：旧数据 createdAt 是 ISO-8601 字符串，新实体 @JsonFormat pattern 解析失败
            log.warn("result parse error, drop key to allow retry: requestId={}", requestId, e);
            redisTemplate.delete(key);
            return null;
        }
    }

    // ---------- 内部工具 ----------

    private void writeResult(String key, SeckillResult result) {
        redisTemplate.opsForValue()
                .set(key, toJson(result), RESULT_TTL_SECONDS, TimeUnit.SECONDS);
    }

    private SeckillResult writeAndReturn(String key, SeckillResult result) {
        writeResult(key, result);
        return result;
    }

    private String toJson(Object o) {
        try {
            return jsonMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }
}