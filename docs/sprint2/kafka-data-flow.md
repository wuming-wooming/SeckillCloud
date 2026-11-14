# Kafka（秒杀）数据流汇总

> 本文汇总 Sprint 2 秒杀链路中 Kafka 的数据流、状态机、失败恢复路径。
> 与 `docs/seckill-contract.md` §4 互补：契约文档定义"是什么"，本文定义"怎么流转"。

## 一、Topic 拓扑

| Topic | Partitions | Retention | 用途 | Producer | Consumer |
|---|---|---|---|---|---|
| `seckill-order-topic` | 3 | 默认 7d | 主消息流 | seckill | order |
| `seckill-order-topic.DLT` | 3 | 默认 7d | 重试耗尽/毒消息 | order（DLT Recoverer） | 暂无（人工/告警） |

**为什么 3 个 partition**：
- 同活动消息按 `activityId` hash 到固定 partition，保证单活动内有序
- 3 个足够支撑单活动 QPS < 5000 的消费吞吐，且给并发消费（concurrency=3）留出并行度
- 未来若秒杀活动 > 1000 个同时进行，可扩到 6 或 12（注意扩分区后消息 rebalance）

## 二、消息生命周期

```
                 ┌────────────────────────────────────────────────────────────┐
                 │                    seckill 服务                              │
                 │  POST /seckill/{id}/order                                   │
                 │    │                                                        │
                 │    ├─ Redis SETNX seckill:result (QUEUING)                 │
                 │    ├─ 时间校验 + Lua 预减（DECR stock + SADD users）        │
                 │    ├─ Lua 成功 → 构造 SeckillOrderMessage                   │
                 │    ├─ kafkaTemplate.send(topic, key=activityId).get(3s)    │
                 │    │   ├─ 成功 → Redis result = SUCCESS，HTTP 200          │
                 │    │   └─ 失败 → 保留 QUEUING，HTTP 500（BIZ_ERROR）        │
                 │    └─ Lua 失败 → Redis result = FAIL(2001/2004...)，HTTP 200│
                 └──────────────────────────┬─────────────────────────────────┘
                                            │
                                            ▼ topic: seckill-order-topic
                 ┌────────────────────────────────────────────────────────────┐
                 │                    order 服务                               │
                 │  @KafkaListener(concurrency=3, manual_immediate ack)       │
                 │    │                                                        │
                 │    ├─ JSON 反序列化失败 → ack + ERROR 日志（毒消息）         │
                 │    ├─ Step A: 查 seckill_order WHERE request_id=?           │
                 │    │   ├─ SUCCESS/FAILED → return（幂等命中）               │
                 │    │   └─ PENDING → 跳到 Step C（重试恢复）                 │
                 │    ├─ Step B: INSERT seckill_order (PENDING)                │
                 │    │   └─ uk_request_id 冲突 → 重查 → 走 Step A            │
                 │    ├─ Step C: ProductStockClient.deductStock()              │
                 │    │   ├─ R.code=200, data=true → Step D                    │
                 │    │   ├─ R.code=2001 → 订单 FAILED + Redis FAIL(2001)      │
                 │    │   ├─ R.code=2006 → 抛 RetryableOrderException         │
                 │    │   └─ 网络/超时 → 抛 RetryableOrderException            │
                 │    ├─ Step D: 回写 Redis seckill:result=SUCCESS             │
                 │    └─ ack.acknowledge()                                     │
                 └──────────────────────────┬─────────────────────────────────┘
                                            │
                        ┌───────────────────┴────────────────────┐
                        │                                        │
                        ▼ 重试耗尽/毒消息                          ▼ 成功
                 ┌──────────────┐                        ┌──────────────────┐
                 │ DLT topic    │                        │ offset 提交       │
                 │ seckill-     │                        │ DB: SUCCESS      │
                 │ order-       │                        │ Redis: SUCCESS   │
                 │ topic.DLT    │                        └──────────────────┘
                 └──────────────┘
```

## 三、状态机全景

### 3.1 Redis `seckill:result:{requestId}`

```
                    ┌──────────┐
   用户提交 ──────▶ │ QUEUING  │ ─── Lua 失败 ────────▶ FAIL(2001/2002/2003/2004/2005)
                    │          │
                    │          │ ─── Kafka 发送失败 ──▶ [保持 QUEUING]（1h TTL 过期）
                    │          │
                    │          │ ─── Kafka 发送成功 ──▶ [保持 QUEUING]
                    │          │                              │
                    │          │                              ▼ Consumer 处理成功
                    │          │                        SUCCESS（终态）
                    │          │                              │
                    │          │                              ▼ Consumer 处理失败
                    │          │                        FAIL(2001 仅库存不足)
                    └──────────┘
```

**关键约束**：
- 终态（SUCCESS/FAIL）幂等，同 requestId 重提返回原结果
- QUEUING 最长 1h（`RESULT_TTL_SECONDS=3600`），过期后允许同 requestId 重新提交
- **不可写 FAIL 的边界**：Lua 已 DECR + SADD 后，任何后续失败都不能写 FAIL（会库存永久丢失）

### 3.2 DB `seckill_order.status`

```
           INSERT                  deductStock=200
    ───▶ PENDING ──────────────────────────────────▶ SUCCESS (1)
           (0)  │
                │  deductStock=2001
                └──────────────────────────────────▶ FAILED (2)

    重试时命中 PENDING → 重走 deductStock（product 侧幂等）
```

### 3.3 product `product:deduct:{requestId}`

```
           SETNX                 DB UPDATE rows>0
    ───▶ PROCESSING ────────────────────────────────▶ SUCCESS
           │
           │ DB UPDATE rows=0 → 删 key，抛 2001
           └───────────────────（失败，无残留）
           │
           │ DB 异常 → 保留 PROCESSING，抛 BIZ_ERROR
           └───────────────────（T5.3 对账清理）
```

## 四、失败恢复矩阵

| 失败点 | 用户看到 | Redis result | DB order | Kafka 消息 | 恢复方式 |
|---|---|---|---|---|---|
| Lua 失败 | FAIL | FAIL(2001/2004...) | 无 | 无 | 用户新 requestId 重试 |
| Kafka 发送失败 | 500 BIZ_ERROR | QUEUING | 无 | 无（未入 topic） | T5.3 扫 QUEUING + seckill:users 反推 |
| Kafka 发送成功但 order 未启动 | QUEUING | QUEUING | 无 | 在 topic 中 | order 启动后消费（offset 保留） |
| order 消费时 product 未启动 | QUEUING → DLT | QUEUING（保持） | PENDING | 进 DLT | DLT 重投 + T5.3 对账 |
| product 返回 2006 | QUEUING | QUEUING | PENDING | 重试 3 次后 DLT | 同上一行 |
| product 返回 2001 | FAIL(2001) | FAIL(2001) | FAILED | offset 提交 | 终态，用户新 requestId 重试 |
| order 落库成功但回写 Redis 失败 | QUEUING | QUEUING | SUCCESS | 重试（走 Step A 幂等） | 重试成功后回写 SUCCESS |
| JSON 反序列化失败（毒消息） | 永卡 QUEUING | QUEUING | 无 | 直接 ack（不重试） | 人工排查 payload |

## 五、验证命令速查（Windows PowerShell）

```powershell
# 1. 查 topic 列表
docker exec -it kafka-dev kafka-topics --bootstrap-server localhost:9092 --list

# 2. 查 topic 详情（partitions、replication）
docker exec -it kafka-dev kafka-topics --bootstrap-server localhost:9092 --describe --topic seckill-order-topic

# 3. 看 topic 中最新消息（含 key）
docker exec -it kafka-dev kafka-console-consumer `
  --bootstrap-server localhost:9092 --topic seckill-order-topic `
  --from-beginning --max-messages 10 --property print.key=true

# 4. 查 consumer group lag（消息堆积情况）
docker exec -it kafka-dev kafka-consumer-groups `
  --bootstrap-server localhost:9092 --describe --group seckill-order-consumer-group

# 5. 查 DLT 消息
docker exec -it kafka-dev kafka-console-consumer `
  --bootstrap-server localhost:9092 --topic seckill-order-topic.DLT `
  --from-beginning --property print.headers=true

# 6. 查订单最终状态
docker exec -it pg-dev psql -U dev -d micro_db -c `
  "SET search_path TO temp; SELECT request_id, user_id, status, fail_reason FROM seckill_order ORDER BY id DESC LIMIT 10"

# 7. 查 Redis 结果
docker exec -it redis-dev redis-cli --scan --pattern "seckill:result:*" | ForEach-Object {
  docker exec -it redis-dev redis-cli GET $_
}
```

## 六、Sprint 3 演进方向

1. **DLT 消费者**：实现 `@KafkaListener(topics = "seckill-order-topic.DLT")`，落库到 `dead_letter_message` 表 + 告警，替代人工 console 查
2. **T5.3 对账任务**：`@Scheduled` 每 5 分钟扫 `seckill_order WHERE status=0 AND created_at < now()-5m`，比对 `product:deduct:{requestId}` 和 `seckill:users:{activityId}`，自动修正或告警
3. **DLT 重投工具**：管理端点 `POST /admin/dlt/replay?topic=&offset=` 把 DLT 消息重投回主 topic
4. **重试策略调优**：`FixedBackOff(1s, 3)` → `ExponentialBackOff(1s, 2.0, 15s)`，覆盖 product 重启场景
5. **监控指标**：Actuator 暴露 `kafka.consumer.fetch.manager.records.lag`、`seckill.order.pending.count`、`seckill.dlt.size`