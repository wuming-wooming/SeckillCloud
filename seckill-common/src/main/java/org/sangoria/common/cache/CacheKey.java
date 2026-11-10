package org.sangoria.common.cache;

/**
 * 全局 Redis Key 契约。
 * 规定：prefix 必须全局唯一；以 ':' 结尾的才支持 suffix。
 */
public enum CacheKey {

    // ---------------- Product 服务 ----------------
    /** 商品详情缓存，suffix = 商品 ID。TTL 30min ±5min */
    PRODUCT_INFO("product:info:", 1800),
    /** T2.6 幂等 key，suffix = requestId。TTL 1h */
    DEDUCT_IDEM("product:deduct:", 3600),

    // ---------------- 跨服务共享 ----------------
    /** 活动详情缓存，suffix = 活动 ID。TTL 30min ±5min */
    ACTIVITY_INFO("seckill:activity:info:", 1800),
    /** 活动列表缓存（无 suffix）。TTL 60s + 0~60s 抖动 */
    ACTIVITY_LIST("seckill:activity:list", 60),
    /** 库存预热，suffix = 活动 ID。TTL 由预热时按活动结束时间决定（defaultTtl=0） */
    SECKILL_STOCK("seckill:stock:", 0),
    /** 用户去重集合，suffix = 活动 ID。TTL 由预热时决定（defaultTtl=0） */
    SECKILL_USERS("seckill:users:", 0),
    /** 秒杀结果，suffix = requestId。TTL 1h */
    SECKILL_RESULT("seckill:result:", 3600),
    ;

    private final String prefix;
    private final long defaultTtlSeconds;

    CacheKey(String prefix, long defaultTtlSeconds) {
        this.prefix = prefix;
        this.defaultTtlSeconds = defaultTtlSeconds;
    }

    public String prefix() {
        return prefix;
    }

    /** 无 suffix 的 key（如 ACTIVITY_LIST）。 */
    public String key() {
        return prefix;
    }

    /** 带 suffix 的 key（如 PRODUCT_INFO + 123）。prefix 不以 ':' 结尾会抛异常，拦截误用。 */
    public String key(Object suffix) {
        if (!prefix.endsWith(":")) {
            throw new IllegalStateException(
                    "CacheKey[" + name() + "] 的 prefix 无尾冒号，不支持 suffix");
        }
        if (suffix == null) {
            throw new IllegalArgumentException("CacheKey[" + name() + "] suffix 不能为 null");
        }
        return prefix + suffix;
    }

    /** 默认 TTL；0 表示"由调用方运行时决定"。 */
    public long defaultTtlSeconds() {
        return defaultTtlSeconds;
    }
}