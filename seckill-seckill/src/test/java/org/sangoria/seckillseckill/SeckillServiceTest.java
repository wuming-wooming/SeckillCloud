package org.sangoria.seckillseckill;

import org.junit.jupiter.api.Test;
import org.sangoria.seckillseckill.service.SeckillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class SeckillServiceTest {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void testConcurrentSeckill_NoOversell() throws InterruptedException {
        Long activityId = 1001L;
        String stockKey = "seckill:stock:" + activityId;
        int initialStock = 10;
        int concurrentRequests = 20;

        // 1. 预热库存
        redisTemplate.opsForValue().set(stockKey, String.valueOf(initialStock));

        // 2. 双闸门：所有线程就位后一次性放行，制造真实并发
        ExecutorService pool = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch ready = new CountDownLatch(concurrentRequests);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(concurrentRequests);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < concurrentRequests; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    if (seckillService.trySeckill(activityId)) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await(10, TimeUnit.SECONDS);

        // 3. 断言
        String finalStock = redisTemplate.opsForValue().get(stockKey);
        System.out.printf("成功=%d, 失败=%d, 剩余库存=%s%n",
                successCount.get(), failCount.get(), finalStock);

        assertEquals(initialStock, successCount.get(), "成功数必须等于初始库存");
        assertEquals(concurrentRequests - initialStock, failCount.get());
        assertEquals("0", finalStock, "最终库存必须归零，不允许负数");

        pool.shutdown();
    }
}