package org.sangoria.seckillorder.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.sangoria.seckillapi.constant.KafkaTopics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * T4.2 消费者错误处理：重试 + 死信。
 * <p>策略：
 * <ul>
 *   <li>JSON 反序列化失败 → 不重试（毒消息确定性失败），直接进 DLT</li>
 *   <li>业务异常（如 product 库存不足但已记录）→ 已处理完，不走这里（Consumer 内部 catch）</li>
 *   <li>网络/超时/product 2006 → 重试 3 次（1s 间隔），耗尽进 DLT</li>
 * </ul>
 * <p>DLT topic 命名：{original-topic}.DLT（DeadLetterPublishingRecoverer 默认行为）
 */
@Slf4j
@Configuration
public class KafkaErrorHandlerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);
        // 1s 固定间隔，最多重试 3 次，总约 3s
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));

        // 不重试的异常：反序列化失败（毒消息）
        handler.addNotRetryableExceptions(
                JsonProcessingException.class,
                IllegalArgumentException.class);

        // 重试耗尽或毒消息的监听器
        handler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("kafka retry: topic={}, partition={}, offset={}, attempt={}, ex={}",
                        record.topic(), record.partition(), record.offset(),
                        deliveryAttempt, ex.getClass().getSimpleName()));

        // 默认 10 次重试改成 3 次（覆盖 DefaultErrorHandler 的默认值）
        handler.setLogLevel(org.springframework.kafka.KafkaException.Level.WARN);
        return handler;
    }

    /** 显式声明 DLT Topic Bean（可选，首次自动创建也行） */
    @Bean
    public NewTopic seckillOrderDlt() {
        return new NewTopic(
                KafkaTopics.SECKILL_ORDER_DLT, 3, (short) 1);
    }
}