package org.sangoria.seckillapi.constant;

/**
 * 内部服务间接口路径唯一来源。
 * <p>提供方 {@code @PostMapping} 与消费方 {@code @PostExchange} 均引用此处常量，
 * 改路径只改一处，双端编译期同步。
 * <p>本常量存的是「全路径」，不含 controller 类级前缀（提供方也统一去掉类级
 * {@code @RequestMapping}，见 InternalActivityController）。
 */
public final class InternalApiPaths {

    private InternalApiPaths() {
    }

    /** T2.5 活动库存 + meta 预热（运维/定时任务触发，无跨服务消费方） */
    public static final String PRODUCT_ACTIVITY_WARMUP =
            "/internal/activity/{activityId}/warmup";

    /** T2.6 DB 扣库存（requestId 幂等，T4.4 order 消费） */
    public static final String PRODUCT_ACTIVITY_DEDUCT =
            "/internal/activity/{activityId}/stock/deduct";
}