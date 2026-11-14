package org.sangoria.seckillapi.client;

import org.sangoria.common.result.R;
import org.sangoria.seckillapi.constant.InternalApiPaths;
import org.sangoria.seckillapi.dto.DeductStockRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * seckill-product 扣库存内部接口的客户端契约（T4.4 由 seckill-order 消费）。
 * <p>消费方通过 {@code @ImportHttpServices} 或 {@code HttpServiceProxyFactory}
 * 生成代理后注入使用。
 * <p><b>重要</b>：本项目 GlobalExceptionHandler 使 HTTP 恒为 200，业务码在
 * {@link R#getCode()}。消费方必须判断 {@code r.getCode() == 200}，
 * 且要区分「幂等命中」与「首次成功」时，再看 {@code r.getData()} 是否为 TRUE。
 * 不要用 HTTP 状态码判断业务成败。
 */
@HttpExchange
public interface ProductStockClient {

    @PostExchange(InternalApiPaths.PRODUCT_ACTIVITY_DEDUCT)
    R<Boolean> deductStock(@PathVariable("activityId") Long activityId,
                           @RequestBody DeductStockRequest request);
}