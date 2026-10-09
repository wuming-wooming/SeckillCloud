| Epic | 任务 | 产出 | 依赖 | 验收标准 | 优先级 |
|---|---|---|---|---|---|
| E0 启动准备 | T0.1 建 Sprint 2 分支与任务板 | `sprint2` 分支、`docs/sprint2/tasks.md` | 无 | 任务可追踪 | P0 |
| E0 | T0.2 基础设施健康检查 | Docker Compose 状态 | 无 | Nacos/Redis/PG/Kafka 全可用 | P0 |
| E0 | T0.3 兼容性 Spike | 兼容性报告 | T0.2 | ✅ Actuator/Caffeine/Kafka 通过；⚠️ Sentinel 部分通过；❌ Seata 不兼容，启用备选 | P0 |
| E1 网关增强 | T1.1 网关路由迁移 Nacos | `seckill-gateway-routes.yml` | T0.2 | 改 Nacos 路由后动态生效，无需重启 | P0 |
| E1 | T1.2 全服务接入 Actuator | 各服务 `/actuator/health` | T0.2 | health UP，网关可访问 | P0 |
| E1 | T1.3 引入 Caffeine LoadBalancer 缓存 | 依赖与配置 | T0.2 | 启动无 LoadBalancer 缓存警告 | P1 |
| E2 Product | T2.1 表设计与初始化 SQL | `product`、`seckill_activity` 表 | T0.2 | SQL 可在 `temp` schema 执行 | P0 |
| E2 | T2.2 实体/Mapper/Service/Controller | Product 模块 CRUD 骨架 | T2.1 | 编译通过，MyBatis-Plus 正常 | P0 |
| E2 | T2.3 商品/秒杀活动查询接口 | `/product/{id}`、`/product/seckill/list`、`/product/seckill/{activityId}` | T2.2 | 走网关可查 | P0 |
| E2 | T2.4 Redis Cache-Aside + 空值防穿透 | 缓存层与 TTL | T2.3 | 缓存命中，空值短 TTL，DB 变更后失效 | P0 |
| E2 | T2.5 活动库存预热 Redis | `seckill:stock:{activityId}` | T2.4 | 活动开始前库存写入 Redis | P0 |
| E2 | T2.6 DB 扣库存接口 | 供订单服务调用 | T2.2 | 乐观锁/条件更新，库存不为负 | P0 |
| E3 秒杀核心 | T3.1 接口契约 | `POST /seckill/{activityId}/order`、`GET /seckill/result/{requestId}` | T2.3 | 接口文档明确 | P0 |
| E3 | T3.2 Redis Lua 原子预减库存 | Lua 脚本 | T2.5 | 并发下库存不超卖 | P0 |
| E3 | T3.3 用户重复秒杀去重 | Redis Set 或 Hash | T3.2 | 同一用户同一活动只能成功一次 | P0 |
| E3 | T3.4 活动时间校验 | 开始/结束时间判断 | T3.2 | 未开始/已结束拒绝 | P0 |
| E3 | T3.5 幂等 requestId + 发 Kafka | Kafka Producer | T3.2 | 重复请求返回同一排队结果 | P0 |
| E3 | T3.6 秒杀结果查询 | 结果缓存/DB 查询 | T3.5 | 可查“排队中/成功/失败” | P1 |
| E4 Kafka 订单 | T4.1 Topic 与生产者配置 | `seckill-order-topic` | T3.5 | `acks=all`、幂等 producer | P0 |
| E4 | T4.2 订单消费者 | 手动 ack、重试、死信 | T4.1 | 异常可重试，死信可追踪 | P0 |
| E4 | T4.3 订单落库 + 唯一索引幂等 | `seckill_order` 表 | T4.2 | 同一 requestId 不重复插入 | P0 |
| E4 | T4.4 调用 Product 扣 DB 库存 | `@HttpExchange` 客户端 | T2.6 | 扣库存成功/失败可感知 | P0 |
| E4 | T4.5 订单状态更新与结果回写 | 订单状态、Redis 结果 | T4.4 | 用户可查询最终结果 | P1 |
| E5 最终一致性 | T5.1 Seata 兼容性验证（Spike） | Spike 报告 | T0.3 | ❌ 不兼容，已记录 | P0 |
| E5 | T5.2 本地消息表设计与实现 | `local_message` 表 + 消息写入逻辑 | T4.4 | 订单落库与消息写入同一事务 | P0 |
| E5 | T5.3 定时补偿与幂等消费 | `@Scheduled` 补偿任务 + 幂等扣库存接口 | T5.2 | 未确认消息自动重试，重复请求不重复扣库存 | P0 |
| E5 | T5.4 最终一致性端到端验证 | 测试用例 + 对账脚本 | T5.3 | 订单失败库存回滚，对账无差异 | P0 |
| E6 Sentinel | T6.1 接入 Sentinel | 依赖与配置 | T0.3 | 最小限流生效 | P1 |
| E6 | T6.2 热点参数限流 | 活动 ID、用户 ID | T6.1 | 超阈值返回 429/业务码 | P1 |
| E6 | T6.3 规则持久化 Nacos | Sentinel 规则配置 | T6.2 | Nacos 改规则动态生效 | P1 |
| E7 验收 | T7.1 单元与集成测试 | 测试用例 | E2-E6 | Lua、幂等、回滚通过 | P0 |
| E7 | T7.2 JMeter/k6 压测 | 压测报告 | E2-E6 | 1000 并发，成功数=库存，无超卖 | P0 |
| E7 | T7.3 文档与面试话术 | Sprint 2 README | E7.2 | 可复现验收命令 | P0 |
