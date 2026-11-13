# 秒杀链路契约（Seckill Contract）

> 本文件是 seckill-product / seckill-seckill / seckill-order 三服务共享的**技术契约**。
> 任何对 Redis Key、Lua 返回码、状态机、Kafka Topic、跨服务 HTTP 接口的变更，
> 必须同步更新本文件与 `docs/api.md`，并修改所有引用方。

## 1. Redis Key 契约

| Key 模板 | 类型 | TTL | 写入方 | 读取方 | 用途 |
|---|---|---|---|---|---|
| `seckill:stock:{activityId}` | String | 预热时算（≥1h，活动结束+1h） | product（T2.5） | seckill（Lua） | 库存余量，Lua `DECR` |
| `seckill:users:{activityId}` | Set | 同 stock | seckill（Lua） | seckill（Lua） | 已购用户，`SADD userId` 去重 |
| `seckill:activity:meta:{activityId}` | Hash | 同 stock | product（T2.5） | seckill | 活动快照，字段：`startTime`/`endTime`/`status`（ISO-8601 / int） |
| `seckill:result:{requestId}` | String(JSON) | 3600s | seckill | seckill | 秒杀结果状态机，见 §3 |
| `product:deduct:{requestId}` | String | 3600s | product（T2.6） | product | DB 扣库存幂等状态机，取值 `PROCESSING`/`SUCCESS` |

**Key 常量唯一来源**：`org.sangoria.common.cache.CacheKey`。禁止在业务代码中字面量拼接前缀。

## 2. Lua 脚本契约

### `seckill-seckill/src/main/resources/lua/seckill_stock.lua`

| 项 | 内容 |
|---|---|
| KEYS[1] | `seckill:stock:{activityId}` |
| KEYS[2] | `seckill:users:{activityId}` |
| ARGV[1] | `userId`（字符串） |
| 返回 | `1` 成功 / `0` 库存不足 / `-1` 重复秒杀 |
| 原子性 | 单 Redis 命令，全程原子 |

**变更影响面**：`SeckillCode.from()` 映射、`SeckillCodeTest`、本文件 §3 状态机。

## 3. 秒杀结果状态机

```
QUEUING ── 时间校验失败（ACTIVITY_NOT_FOUND / NOT_STARTED / ENDED）──▶ FAIL
QUEUING ── Lua 成功（SUCCESS=1）──▶ SUCCESS
QUEUING ── Lua 失败（STOCK_EMPTY=0 / DUPLICATE=-1）──▶ FAIL
QUEUING ── 执行异常（Redis 抖动 / 未来 Kafka 发送失败）──▶ 保留 QUEUING
```

**终态**（SUCCESS / FAIL）幂等：同 requestId 重试返回原结果，不再执行 Lua。
**非终态**（QUEUING）：同 requestId 重试返回 `2006`，客户端应轮询 `/seckill/result/{requestId}`。
**超时兜底**：QUEUING 最长保留 1h（TTL）。1h 后过期，用户可用同 requestId 重新提交。

| 场景 | 落盘状态 | 后续动作 |
|---|---|---|
| Lua 成功 | SUCCESS | 前端轮询到 SUCCESS，视为抢到资格 |
| 库存不足 / 重复 / 未开始 / 已结束 / 活动不存在 | FAIL(2001/2004/2002/2003/2005) | 前端展示对应错误，用户可重试（生成新 requestId） |
| 执行异常（Redis 或未来 Kafka 发送） | 保留 QUEUING | T5.3 对账任务：扫 QUEUING，比对 `seckill:users:{id}` 与 DB 订单，修正状态 |

## 4. Kafka 契约（Sprint 2 E4 待实施，当前为 TODO 占位）

| 项 | 值 |
|---|---|
| Topic | `seckill-order-topic` |
| Partition Key | `activityId`（同活动消息保序） |
| Producer | seckill 服务，`acks=all` + 幂等 producer（`enable.idempotence=true`） |
| Consumer Group | `seckill-order-consumer-group` |
| 发送时机 | **Lua 成功后、写 SUCCESS 前**（见 `SeckillService.trySeckill` TODO 标注） |

消息体（JSON）：
```json
{
  "requestId": "uuid",
  "activityId": 2,
  "userId": 1001,
  "productId": 1,
  "quantity": 1,
  "seckillPrice": 4999.00,
  "createdAt": "2026-11-13T10:00:00"
}
```

投递语义：**至少一次**。消费方按 `requestId` 幂等落库（唯一索引 `uk_request_id`）。

## 5. 跨服务 HTTP 契约

### seckill-product 提供的 internal 接口（供 seckill / order 调用，**不对公网暴露**）

| 接口 | 用途 | 幂等 |
|---|---|---|
| `POST /internal/activity/{id}/warmup?force=` | T2.5 库存+meta 预热 | 幂等（可重复执行，覆盖写） |
| `POST /internal/activity/{id}/stock/deduct` | T2.6 DB 扣库存 | requestId 幂等（Redis 状态机） |

**网关拦截**：`/internal/**` 本轮不拦截（Sprint 3 统一加 403）。**生产部署必须禁止直连**。

## 6. 变更流程

1. 修改本文件对应章节
2. 若涉及对外接口，同步 `docs/api.md`
3. 更新 `CacheKey` / `ErrorCode` / Lua 脚本 / 状态机常量
4. 通知 seckill-product / seckill-seckill / seckill-order 三方 owner
5. 更新对应的单元/集成测试