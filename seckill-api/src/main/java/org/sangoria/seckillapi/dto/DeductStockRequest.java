package org.sangoria.seckillapi.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * T2.6 / T4.4 扣库存请求体（服务间契约）。
 * <p>提供方 seckill-product 直接 import 作为 {@code @RequestBody} 类型，
 * 消费方 seckill-order 直接 import 作为 {@code @HttpExchange} 方法参数类型。
 * <p>字段契约见 docs/seckill-contract.md §5。
 * <p>字段与 {@code StockDeductRequest}（已删除）一一对应，迁移时零语义漂移。
 */
public record DeductStockRequest(

        /* 前端生成的 UUID，幂等键；product 侧用 product:deduct:{requestId} 做状态机 */
        @NotBlank(message = "requestId can't be blank")
        @Size(max = 64, message = "requestId can't be longer than 64")
        String requestId,

        /* 当前业务恒为 1（Lua 固定 DECR 1），校验层只限制 >= 1 */
        @NotNull(message = "quantity can't be null")
        @Min(value = 1, message = "quantity can't be less than 1")
        Integer quantity
) {
}