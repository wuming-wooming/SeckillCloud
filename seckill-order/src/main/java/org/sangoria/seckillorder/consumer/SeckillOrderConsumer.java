package org.sangoria.seckillorder.consumer;

import lombok.extern.slf4j.Slf4j;
import org.sangoria.seckillapi.dto.SeckillOrderMessage;
import org.sangoria.seckillorder.service.OrderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * T4.2 订单消费者。
 * <p>ack 策略：手动 ack（manual_immediate）——处理成功后显式 acknowledge。
 * <p>毒消息处理：JSON 解析失败直接 ack + 日志，不重试（DefaultErrorHandler 也声明了
 * JsonProcessingException 为 notRetryable）。
 */
@Slf4j
@Component
public class SeckillOrderConsumer {

    private final OrderService orderService;
    private final JsonMapper jsonMapper;

    public SeckillOrderConsumer(OrderService orderService, JsonMapper jsonMapper) {
        this.orderService = orderService;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(
            topics = "${seckill.order.topic:seckill-order-topic}",
            groupId = "${spring.kafka.consumer.group-id:seckill-order-consumer-group}",
            concurrency = "${spring.kafka.listener.concurrency:3}")
    public void onMessage(@Payload String payload,
                          Acknowledgment ack,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                          @Header(KafkaHeaders.OFFSET) long offset) {
        SeckillOrderMessage msg;
        try {
            msg = jsonMapper.readValue(payload, SeckillOrderMessage.class);
        } catch (JacksonException e) {
            // 毒消息：确定性失败，直接 ack 避免阻塞 partition，并记录完整 payload 供排查
            log.error("poison message, ack and skip: topic={}, partition={}, offset={}, payload={}",
                    topic, partition, offset, payload, e);
            ack.acknowledge();
            return;
        }

        try {
            orderService.process(msg, topic, partition, offset);
            ack.acknowledge();
        } catch (OrderService.RetryableOrderException e) {
            // 交 DefaultErrorHandler 按 FixedBackOff 重试；不 ack，offset 不提交
            log.warn("consume retryable failure, will retry: requestId={}, err={}",
                    msg.requestId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            // 非预期异常也交 error handler（会重试 → DLT）
            log.error("consume unexpected failure: requestId={}", msg.requestId(), e);
            throw e;
        }
    }
}