package org.sangoria.seckillapi.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * T3.5 → T4.2 秒杀下单消息体（服务间 Kafka 契约）。
 * <p>Producer: seckill 服务（Lua 成功后发送）。
 * Consumer: order 服务（幂等落库 + 扣 DB 库存）。
 * <p>字段契约见 docs/seckill-contract.md §4。时间字段序列化为
 * {@code yyyy-MM-dd HH:mm:ss}，与项目统一 JSON 契约一致。
 * <p>注意：record 序列化时 Jackson 按字段名输出，order 侧反序列化同一 record，
 * 字段名变更必须双端同步。
 */
public record SeckillOrderMessage(
        String requestId,
        Long activityId,
        Long userId,
        Long productId,
        Integer quantity,
        BigDecimal seckillPrice,
        LocalDateTime createdAt
) {
}