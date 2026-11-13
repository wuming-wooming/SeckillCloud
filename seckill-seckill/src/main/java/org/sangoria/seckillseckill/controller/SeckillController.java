package org.sangoria.seckillseckill.controller;

import jakarta.validation.Valid;
import org.sangoria.common.result.ErrorCode;
import org.sangoria.common.result.R;
import org.sangoria.seckillseckill.dto.SeckillOrderRequest;
import org.sangoria.seckillseckill.service.SeckillResult;
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
     * T3.5 秒杀下单（requestId 幂等 + Lua 预减，无真实订单；Kafka 见 TODO）
     * 返回体统一为 R<SeckillResult>，业务结果看 data.status
     */
    @PostMapping("/{activityId}/order")
    public R<SeckillResult> seckill(@PathVariable("activityId") Long activityId,
                                    @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                    @RequestBody @Valid SeckillOrderRequest req) {
        if (userId == null) {
            return R.fail(ErrorCode.UNAUTHORIZED);
        }
        SeckillResult result = seckillService.trySeckill(
                activityId, userId, req.getRequestId(), req.getQuantity());
        return R.ok(result);
    }

    /**
     * T3.6 秒杀结果查询
     * userId 校验防横向越权（requestId 泄露后仍仅本人可查）
     */
    @GetMapping("/result/{requestId}")
    public R<SeckillResult> result(@PathVariable("requestId") String requestId,
                                   @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            return R.fail(ErrorCode.UNAUTHORIZED);
        }
        SeckillResult r = seckillService.getResult(requestId);
        if (r == null) {
            return R.fail(ErrorCode.SECKILL_RESULT_NOT_FOUND);
        }
        if (!userId.equals(r.getUserId())) {
            return R.fail(ErrorCode.FORBIDDEN);
        }
        return R.ok(r);
    }
}