package org.sangoria.seckillseckill;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sangoria.common.exception.BizException;
import org.sangoria.seckillseckill.service.SeckillResult;
import org.sangoria.seckillseckill.service.SeckillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E3 秒杀核心集成测试。
 * <p>
 * 使用真实 Redis 验证 Lua 原子性；活动 ID 与 requestId 使用独立前缀，避免污染开发数据。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SeckillServiceTest {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // ===== 测试专用活动 ID（与开发数据错开） =====
    private static final Long ACT_OK          = 9101L; // 进行中
    private static final Long ACT_NOT_STARTED = 9102L; // 未开始
    private static final Long ACT_ENDED       = 9103L; // 已结束
    private static final Long ACT_EMPTY       = 9104L; // 库存为 0
    private static final Long ACT_MISSING     = 9199L; // 不存在

    private static final String REQ_PREFIX = "test-req-";

    @BeforeEach
    void setup() {
        cleanupAll();
    }

    @AfterEach
    void cleanupAll() {
        for (Long id : new Long[]{ACT_OK, ACT_NOT_STARTED, ACT_ENDED, ACT_EMPTY, ACT_MISSING}) {
            redisTemplate.delete("seckill:stock:" + id);
            redisTemplate.delete("seckill:users:" + id);
            redisTemplate.delete("seckill:activity:meta:" + id);
        }
        // 仅清理本测试创建的 result key（开发环境禁止 keys，测试环境可接受）
        Collection<String> keys = redisTemplate.keys("seckill:result:" + REQ_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // ================= helper =================

    /**
     * 造 meta + stock。meta 的 startTime/endTime 用 ISO-8601（LocalDateTime.toString()），
     * 与 SeckillMetaService 内部 LocalDateTime.parse(str) 默认格式一致。
     */
    private void warmup(Long activityId, int stock, LocalDateTime start, LocalDateTime end) {
        redisTemplate.opsForValue().set("seckill:stock:" + activityId, String.valueOf(stock));
        redisTemplate.opsForHash().putAll("seckill:activity:meta:" + activityId, Map.of(
                "startTime", start.toString(),
                "endTime",   end.toString(),
                "status",    "1"
        ));
    }

    private void warmupActive(Long activityId, int stock) {
        LocalDateTime now = LocalDateTime.now();
        warmup(activityId, stock, now.minusHours(1), now.plusHours(1));
    }

    private String stockOf(Long activityId) {
        return redisTemplate.opsForValue().get("seckill:stock:" + activityId);
    }

    // ================= T3.4 活动时间校验 =================

    @Test
    void testActivityNotFound() {
        SeckillResult r = seckillService.trySeckill(ACT_MISSING, 1L, REQ_PREFIX + "nf", 1);
        assertEquals(SeckillResult.Status.FAIL, r.getStatus());
        assertEquals(2005, r.getErrorCode());
    }

    @Test
    void testNotStarted() {
        LocalDateTime now = LocalDateTime.now();
        warmup(ACT_NOT_STARTED, 10, now.plusHours(1), now.plusHours(2));
        SeckillResult r = seckillService.trySeckill(ACT_NOT_STARTED, 1L, REQ_PREFIX + "ns", 1);
        assertEquals(SeckillResult.Status.FAIL, r.getStatus());
        assertEquals(2002, r.getErrorCode());
    }

    @Test
    void testEnded() {
        LocalDateTime now = LocalDateTime.now();
        warmup(ACT_ENDED, 10, now.minusHours(2), now.minusHours(1));
        SeckillResult r = seckillService.trySeckill(ACT_ENDED, 1L, REQ_PREFIX + "ed", 1);
        assertEquals(SeckillResult.Status.FAIL, r.getStatus());
        assertEquals(2003, r.getErrorCode());
    }

    // ================= T3.5 成功 / 幂等 / 去重 / 库存不足 =================

    @Test
    void testSuccess() {
        warmupActive(ACT_OK, 10);
        SeckillResult r = seckillService.trySeckill(ACT_OK, 1001L, REQ_PREFIX + "ok", 1);
        assertEquals(SeckillResult.Status.SUCCESS, r.getStatus());
        assertNull(r.getErrorCode());
        assertNotNull(r.getCreatedAt());
        assertNotNull(r.getUpdatedAt());
        assertEquals("9", stockOf(ACT_OK));
    }

    @Test
    void testIdempotentSameRequestId() {
        warmupActive(ACT_OK, 10);
        String reqId = REQ_PREFIX + "idem";
        SeckillResult r1 = seckillService.trySeckill(ACT_OK, 1001L, reqId, 1);
        SeckillResult r2 = seckillService.trySeckill(ACT_OK, 1001L, reqId, 1);
        assertEquals(SeckillResult.Status.SUCCESS, r1.getStatus());
        assertEquals(SeckillResult.Status.SUCCESS, r2.getStatus());
        // 库存只扣一次
        assertEquals("9", stockOf(ACT_OK));
    }

    @Test
    void testDuplicateUser() {
        warmupActive(ACT_OK, 10);
        SeckillResult r1 = seckillService.trySeckill(ACT_OK, 2002L, REQ_PREFIX + "dup1", 1);
        SeckillResult r2 = seckillService.trySeckill(ACT_OK, 2002L, REQ_PREFIX + "dup2", 1);
        assertEquals(SeckillResult.Status.SUCCESS, r1.getStatus());
        assertEquals(SeckillResult.Status.FAIL,    r2.getStatus());
        assertEquals(2004, r2.getErrorCode());
        assertEquals("9", stockOf(ACT_OK));
    }

    @Test
    void testStockEmpty() {
        warmupActive(ACT_EMPTY, 0);
        SeckillResult r = seckillService.trySeckill(ACT_EMPTY, 3003L, REQ_PREFIX + "empty", 1);
        assertEquals(SeckillResult.Status.FAIL, r.getStatus());
        assertEquals(2001, r.getErrorCode());
    }

    @Test
    void testInvalidQuantity() {
        warmupActive(ACT_OK, 10);
        BizException e = assertThrows(BizException.class,
                () -> seckillService.trySeckill(ACT_OK, 4004L, REQ_PREFIX + "q2", 2));
        assertEquals(400, e.getErrorCode().getCode());
    }

    // ================= T3.6 结果查询 =================

    @Test
    void testGetResult() {
        warmupActive(ACT_OK, 10);
        String reqId = REQ_PREFIX + "query";
        seckillService.trySeckill(ACT_OK, 5005L, reqId, 1);

        SeckillResult r = seckillService.getResult(reqId);
        assertNotNull(r);
        assertEquals(SeckillResult.Status.SUCCESS, r.getStatus());
        assertEquals(ACT_OK, r.getActivityId());
        assertEquals(5005L,  r.getUserId());
        assertNotNull(r.getCreatedAt());
        assertNotNull(r.getUpdatedAt());

        // 不存在的 requestId → null
        assertNull(seckillService.getResult("nonexistent-" + System.nanoTime()));
    }

    // ================= T3.2/T3.3 并发：不超卖 =================

    @Test
    void testConcurrentSeckill_NoOversell() throws InterruptedException {
        int initialStock       = 10;
        int concurrentRequests = 30;
        warmupActive(ACT_OK, initialStock);

        ExecutorService pool  = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch  ready = new CountDownLatch(concurrentRequests);
        CountDownLatch  start = new CountDownLatch(1);
        CountDownLatch  done  = new CountDownLatch(concurrentRequests);

        AtomicInteger successCount    = new AtomicInteger(0);
        AtomicInteger stockEmptyCount = new AtomicInteger(0);
        AtomicInteger otherFail       = new AtomicInteger(0);

        for (int i = 0; i < concurrentRequests; i++) {
            final long   userId = 10_000L + i;
            final String reqId  = REQ_PREFIX + "conc-" + i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    SeckillResult r = seckillService.trySeckill(ACT_OK, userId, reqId, 1);
                    if (r.getStatus() == SeckillResult.Status.SUCCESS) {
                        successCount.incrementAndGet();
                    } else if (Integer.valueOf(2001).equals(r.getErrorCode())) {
                        stockEmptyCount.incrementAndGet();
                    } else {
                        otherFail.incrementAndGet();
                    }
                } catch (Exception e) {
                    otherFail.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        boolean finished = done.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertTrue(finished, "并发测试超时");

        String finalStock = stockOf(ACT_OK);
        System.out.printf("成功=%d, 库存不足=%d, 其它失败=%d, 剩余库存=%s%n",
                successCount.get(), stockEmptyCount.get(), otherFail.get(), finalStock);

        assertEquals(initialStock, successCount.get(),     "成功数必须等于初始库存");
        assertEquals(concurrentRequests - initialStock, stockEmptyCount.get(), "其余均为库存不足");
        assertEquals(0, otherFail.get(),                    "不应出现其它失败");
        assertEquals("0", finalStock,                       "最终库存必须归零，不允许负数");

        // 用户去重集合 size 应等于成功数（每个成功 userId 一条，无残留）
        Long userSetSize = redisTemplate.opsForSet().size("seckill:users:" + ACT_OK);
        assertEquals((long) initialStock, userSetSize, "已购用户集合大小应等于成功数");
    }
}