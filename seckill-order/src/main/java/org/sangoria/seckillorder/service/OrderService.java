package org.sangoria.seckillorder.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.sangoria.common.cache.CacheKey;
import org.sangoria.common.result.R;
import org.sangoria.seckillapi.client.ProductStockClient;
import org.sangoria.seckillapi.dto.DeductStockRequest;
import org.sangoria.seckillapi.dto.SeckillOrderMessage;
import org.sangoria.seckillorder.entity.SeckillOrder;
import org.sangoria.seckillorder.mapper.SeckillOrderMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * T4.3 + T4.4 + T4.5 订单处理编排。
 * <p>流程见 docs/sprint2/tasks.md T4.2~T4.5；执行流图见本轮对话输出。
 */
@Slf4j
@Service
public class OrderService {

    private static final long RESULT_TTL_SECONDS = 3600L;

    private final SeckillOrderMapper orderMapper;
    private final ProductStockClient productStockClient;
    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    public OrderService(SeckillOrderMapper orderMapper,
                        ProductStockClient productStockClient,
                        StringRedisTemplate redis,
                        JsonMapper jsonMapper) {
        this.orderMapper = orderMapper;
        this.productStockClient = productStockClient;
        this.redis = redis;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 幂等入口。同一 requestId 多次调用安全。
     * <p>返回值不用于 ack 判定——正常返回即 ack；异常抛出由 DefaultErrorHandler 重试/DLT。
     */
    public void process(SeckillOrderMessage msg,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("consume order msg: requestId={}, activityId={}, userId={}, partition={}, offset={}",
                msg.requestId(), msg.activityId(), msg.userId(), partition, offset);

        // Step A: 幂等快路径
        SeckillOrder existing = findByRequestId(msg.requestId());
        if (existing != null) {
            if (existing.getStatus() == SeckillOrder.STATUS_SUCCESS
                    || existing.getStatus() == SeckillOrder.STATUS_FAILED) {
                log.info("order already terminal, skip: requestId={}, status={}",
                        msg.requestId(), existing.getStatus());
                return;
            }
            log.info("order PENDING, resume from product call: requestId={}", msg.requestId());
            doDeductAndFinalize(existing, msg);
            return;
        }

        // Step B: 首次消费，落库 PENDING
        SeckillOrder order = new SeckillOrder();
        order.setRequestId(msg.requestId());
        order.setActivityId(msg.activityId());
        order.setProductId(msg.productId());
        order.setUserId(msg.userId());
        order.setQuantity(msg.quantity());
        order.setSeckillPrice(msg.seckillPrice());
        order.setTotalAmount(msg.seckillPrice()
                .multiply(BigDecimal.valueOf(msg.quantity())));
        order.setStatus(SeckillOrder.STATUS_PENDING);
        order.setKafkaTopic(topic);
        order.setKafkaOffset(offset);
        LocalDateTime now = LocalDateTime.now();
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        try {
            orderMapper.insert(order);
            log.info("order inserted PENDING: requestId={}, id={}", msg.requestId(), order.getId());
        } catch (org.springframework.dao.DuplicateKeyException dup) {
            // 并发消费同一消息（罕见），或上一轮消费被 kill 但 offset 未提交
            log.warn("order insert dup, re-query and resume: requestId={}", msg.requestId());
            SeckillOrder reloaded = findByRequestId(msg.requestId());
            if (reloaded == null) {
                throw dup; // 理论上不会，交 error handler
            }
            if (reloaded.getStatus() == SeckillOrder.STATUS_PENDING) {
                doDeductAndFinalize(reloaded, msg);
                return;
            }
            return;
        }

        // Step C + D
        doDeductAndFinalize(order, msg);
    }

    // -----------------------------------------------------------------------

    /**
     * 调 product 扣库存 → 更新订单终态 → 回写 seckill:result。
     * <p>product 侧 requestId 幂等保证：本方法重复调用不重复扣库存。
     */
    private void doDeductAndFinalize(SeckillOrder order, SeckillOrderMessage msg) {
        DeductStockRequest req = new DeductStockRequest(msg.requestId(), msg.quantity());

        R<Boolean> resp;
        try {
            resp = productStockClient.deductStock(msg.activityId(), req);
        } catch (Exception ex) {
            // 网络/超时/序列化异常 → 抛 Retryable 语义，由 DefaultErrorHandler 重试
            log.error("product call failed, will retry: requestId={}, activityId={}",
                    msg.requestId(), msg.activityId(), ex);
            throw new RetryableOrderException("product call failed", ex);
        }

        if (resp == null) {
            throw new RetryableOrderException("product returned null");
        }

        int code = resp.getCode();
        if (code == 200) {
            // 库存扣减成功（含幂等命中）
            finalizeSuccess(order);
            writeResultSuccess(msg);
            log.info("order SUCCESS: requestId={}, id={}", msg.requestId(), order.getId());
            return;
        }
        if (code == 2001) {
            // 库存不足：确定性业务失败，不重试
            finalizeFailed(order, "库存不足");
            writeResultFail(msg, 2001, "秒杀库存不足");
            log.info("order FAILED(stock empty): requestId={}, id={}", msg.requestId(), order.getId());
            return;
        }
        if (code == 2006) {
            // product 侧 PROCESSING（并发或上次未完成）→ 重试
            throw new RetryableOrderException("product processing, retry");
        }
        // 其他业务码 → 重试（保守策略，3 次后进 DLT 人工处理）
        throw new RetryableOrderException("product returned code=" + code + ", msg=" + resp.getMsg());
    }

    private void finalizeSuccess(SeckillOrder order) {
        SeckillOrder update = new SeckillOrder();
        update.setId(order.getId());
        update.setStatus(SeckillOrder.STATUS_SUCCESS);
        update.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(update);
    }

    private void finalizeFailed(SeckillOrder order, String reason) {
        SeckillOrder update = new SeckillOrder();
        update.setId(order.getId());
        update.setStatus(SeckillOrder.STATUS_FAILED);
        update.setFailReason(reason);
        update.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(update);
    }

    private SeckillOrder findByRequestId(String requestId) {
        return orderMapper.selectOne(new LambdaQueryWrapper<SeckillOrder>()
                .eq(SeckillOrder::getRequestId, requestId));
    }

    // -----------------------------------------------------------------------
    // T4.5 回写 seckill:result
    // -----------------------------------------------------------------------

    /**
     * 回写 SUCCESS。失败抛异常，让 Kafka 重试（订单已 SUCCESS，重试走 Step A 直接 return）。
     */
    private void writeResultSuccess(SeckillOrderMessage msg) {
        writeResult(msg, "SUCCESS", null, null);
    }

    private void writeResultFail(SeckillOrderMessage msg, int code, String errMsg) {
        writeResult(msg, "FAIL", code, errMsg);
    }

    private void writeResult(SeckillOrderMessage msg, String status, Integer code, String errMsg) {
        String key = CacheKey.SECKILL_RESULT.key(msg.requestId());
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("requestId", msg.requestId());
        result.put("activityId", msg.activityId());
        result.put("userId", msg.userId());
        result.put("status", status);
        result.put("errorCode", code);
        result.put("errorMsg", errMsg);
        result.put("createdAt", msg.createdAt().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        result.put("updatedAt", LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        result.put("terminal", true);

        try {
            String json = jsonMapper.writeValueAsString(result);
            redis.opsForValue().set(key, json, RESULT_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            // 回写失败必须抛，让 Kafka 重试（订单已落库，重试安全）
            throw new RetryableOrderException("write seckill:result failed", e);
        }
    }

    // -----------------------------------------------------------------------

    /** 可重试异常标记：DefaultErrorHandler 会按 FixedBackOff 重试 */
    public static class RetryableOrderException extends RuntimeException {
        public RetryableOrderException(String msg) { super(msg); }
        public RetryableOrderException(String msg, Throwable cause) { super(msg, cause); }
    }
}