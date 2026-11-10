package org.sangoria.seckillproduct.service;

import org.sangoria.common.cache.CacheKey;
import org.sangoria.common.exception.BizException;
import org.sangoria.common.result.ErrorCode;
import org.sangoria.seckillproduct.entity.Product;
import org.sangoria.seckillproduct.mapper.ProductMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
public class ProductService {

    /** 空值哨兵：真实 JSON 对象以 '{' 开头，永不与 "NULL" 冲突 */
    private static final String NULL_MARK = "NULL";
    /** TTL 抖动范围（±秒），防缓存雪崩 */
    private static final long TTL_JITTER_SECONDS = 300;

    private final ProductMapper productMapper;
    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    public ProductService(ProductMapper productMapper,
                          StringRedisTemplate redis,
                          JsonMapper jsonMapper) {
        this.productMapper = productMapper;
        this.redis = redis;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Cache-Aside 读路径：
     *   Redis miss → 查 DB → 存在写缓存(30min±5min)，不存在写哨兵(60s)
     *   反序列化失败 → 删脏 key → 回源（自愈）
     */
    public Product getById(Long id) {
        String key = CacheKey.PRODUCT_INFO.key(id);

        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            if (NULL_MARK.equals(cached)) {
                return null;                       // 防穿透：不存在的 ID 也命中缓存
            }
            try {
                return jsonMapper.readValue(cached, Product.class);
            } catch (Exception e) {
                redis.delete(key);                 // 脏数据 / 格式变更 → 自愈降级
            }
        }

        Product p = productMapper.selectById(id);

        if (p == null) {
            redis.opsForValue().set(key, NULL_MARK,
                    60,                  // 空值固定短 TTL 60s
                    TimeUnit.SECONDS);
            return null;
        }

        try {
            String json = jsonMapper.writeValueAsString(p);
            long ttl = CacheKey.PRODUCT_INFO.defaultTtlSeconds()
                    + ThreadLocalRandom.current().nextLong(-TTL_JITTER_SECONDS, TTL_JITTER_SECONDS);
            redis.opsForValue().set(key, json, Math.max(60, ttl), TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // 序列化失败不影响主流程
        }
        return p;
    }

    /**
     * 商品不存在抛 BizException，由 GlobalExceptionHandler 统一转 R.fail(ErrorCode)
     */
    public Product requireById(Long id) {
        Product p = getById(id);
        if (p == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return p;
    }
}