package org.sangoria.seckillproduct.controller;

import jakarta.validation.Valid;
import org.sangoria.common.result.R;
import org.sangoria.seckillproduct.dto.StockDeductRequest;
import org.sangoria.seckillproduct.service.SeckillActivityService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部接口：供 seckill-order 调用，不对公网暴露。
 * Sprint 3 统一在网关 AuthFilter 中拦截 /internal/**。
 */
@RestController
@RequestMapping("/internal/activity")
public class InternalActivityController {

    private final SeckillActivityService seckillActivityService;

    public InternalActivityController(SeckillActivityService seckillActivityService) {
        this.seckillActivityService = seckillActivityService;
    }

    /** T2.5 库存预热 */
    @PostMapping("/{activityId}/warmup")
    public R<Void> warmup(@PathVariable("activityId") Long activityId,
                          @RequestParam(value = "force", defaultValue = "false") boolean force) {
        seckillActivityService.warmupStock(activityId, force);
        return R.ok();
    }

    /** T2.6 DB 扣库存（Redis 状态机幂等） */
    @PostMapping("/{activityId}/stock/deduct")
    public R<Boolean> deduct(@PathVariable("activityId") Long activityId,
                             @RequestBody @Valid StockDeductRequest request) {
        return R.ok(seckillActivityService.deductStock(
                activityId, request.getRequestId(), request.getQuantity()));
    }
}