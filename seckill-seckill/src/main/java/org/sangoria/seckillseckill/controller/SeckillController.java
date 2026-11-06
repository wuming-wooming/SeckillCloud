package org.sangoria.seckillseckill.controller;

import org.sangoria.common.result.ErrorCode;
import org.sangoria.common.result.R;
import org.sangoria.seckillseckill.service.SeckillCode;
import org.sangoria.seckillseckill.service.SeckillService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/seckill")
public class SeckillController {

    private final SeckillService seckillService;

    public SeckillController(SeckillService seckillService) {
        this.seckillService = seckillService;
    }

    /**
     * 秒杀下单（Sprint 2 最小闭环：仅 Redis 预减库存，不生成真实订单）
     * 契约见 tasks.md T3.1
     */
    @PostMapping("/{activityId}/order")
    public R<Boolean> seckill(@PathVariable Long activityId,
                              @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            return R.fail(ErrorCode.UNAUTHORIZED);
        }
        SeckillCode code = seckillService.trySeckill(activityId, userId);
        if (code == SeckillCode.SUCCESS) {
            return R.ok(true);
        }
        return R.fail(code.errorCode());
    }
}