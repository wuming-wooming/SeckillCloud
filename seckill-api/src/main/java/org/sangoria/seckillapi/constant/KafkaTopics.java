package org.sangoria.seckillapi.constant;

/**
 * Kafka Topic 唯一来源。Producer（seckill）与 Consumer（order）共享。
 * 变更需同步 docs/seckill-contract.md §4。
 */
public final class KafkaTopics {

    private KafkaTopics() {
    }

    /** 秒杀下单消息 Topic */
    public static final String SECKILL_ORDER = "seckill-order-topic";

    /** 死信 Topic（Consumer 重试耗尽后投递） */
    public static final String SECKILL_ORDER_DLT = SECKILL_ORDER + ".DLT";

    /** 消费者组 */
    public static final String SECKILL_ORDER_GROUP = "seckill-order-consumer-group";
}