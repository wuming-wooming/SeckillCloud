# Sprint 2 兼容性 Spike 报告

> 分支：`spike-compatibility`
> 结论：4 个组件通过，1 个组件（Seata）不兼容，启用备选方案。

## 一、环境

| 项目 | 版本 |
|---|---|
| JDK | 23.0.2 |
| Spring Boot | 4.0.0 |
| Spring Cloud | 2025.1.0 |
| Spring Cloud Alibaba | 2025.1.0.0 |
| Nacos | 3.0.0 |
| PostgreSQL | 17 (alpine) |
| Redis | 7.4 |
| Kafka | 7.7.0 (Confluent cp-kafka) |

## 二、验证结果

| 组件 | 状态 | 版本 | 备注 |
|---|---|---|---|
| Actuator | ✅ | Boot 4.0.0 原生 | `/actuator/health` 返回 UP，db/redis/discovery 均健康 |
| Caffeine LoadBalancer 缓存 | ✅ | Caffeine 3.x | gateway 启动不再有 `default cache` 警告 |
| Spring Kafka | ✅ | Spring Kafka 4.0.x | 能发能收，控制台输出 `SPIKE RECEIVED: hello-kafka-spike` |
| Sentinel | ⚠️ 部分成功 | Sentinel 1.8.9 | 限流生效，但 `blockHandler` 未按预期执行，推测可能被全局异常处理器拦截 |
| Seata | ❌ | 2.5.0 / 2.6.0 / 2.7.0 | 内置 nacos-client 与 Nacos 3.0 认证协议不兼容，无法注册 |

## 三、详细记录

### 3.1 Actuator ✅

- 依赖：`spring-boot-starter-actuator`
- 配置（Nacos `seckill-common.yml`）：
  ```yaml
  management:
    endpoints:
      web:
        exposure:
          include: health,info,metrics
    endpoint:
      health:
        show-details: always
  ```
- 验证：`curl http://localhost:9001/actuator/health` 返回 `{"status":"UP"}`，含 db、redis、discovery 等详细组件。

### 3.2 Caffeine LoadBalancer 缓存 ✅

- 依赖（`seckill-gateway`）：`com.github.ben-manes.caffeine:caffeine`
- 验证：gateway 启动日志不再出现 `default cache` 警告。
- **隐患**：其他模块（user/product/order/seckill）未引入 Caffeine，若也使用 LoadBalancer 仍会有警告。建议在父 pom 统一引入，或统一不加（视后续需求而定）。

### 3.3 Spring Kafka ✅

- 依赖（`seckill-order`）：`spring-boot-starter-kafka`（Boot 4 新名称，非旧版 `spring-kafka`）
- 配置（Nacos `seckill-order.yml`）：`bootstrap-servers: 127.0.0.1:9092`，`acks: all`，手动 ack。
- 验证：发送 `hello-kafka-spike` 到 `spike-test-topic`，消费者成功接收。
- **注意**：Docker Kafka 版本 7.7.0（即 Kafka 3.7.x）与 Boot 4 自带的 Kafka clients 4.x 协议兼容，无需降级。

### 3.4 Sentinel ⚠️

- 依赖（`seckill-seckill`）：`spring-cloud-starter-alibaba-sentinel`
- 验证现象：
  - 连续两次调用 `/seckill/ping`，第二次被限流（返回 500）。
  - 移除 `@SentinelResource` 后不再限流，说明限流规则本身生效。
  - **问题**：`blockHandler` 未返回预期错误码，被全局异常处理器兜底为 `{"code":500,"data":null,"msg":null}`。
- 可能原因：
  1. `blockHandler` 方法签名不匹配（参数类型、返回值类型、异常类型需与原方法一致）。
  2. `BlockException` 被 `GlobalExceptionHandler` 优先捕获。
  3. 返回体 `R.fail(429, ...)` 与全局返回结构不一致。
- **处理计划**：在 T6.2 阶段先修正 `blockHandler` 签名，并在 `GlobalExceptionHandler` 中显式放行 `BlockException`，或添加专门的 `BlockExceptionHandler`。

### 3.5 Seata ❌

- 尝试版本：Seata 2.5.0 / 2.6.0 / 2.7.0。
- 现象：Seata Server 启动后无法注册到 Nacos 3.0，Nacos 返回 `401 User not found`。原因是 Seata 内置的 `nacos-client 1.4.6`（2.5.0/2.6.0）或 `2.0.4`（2.7.0）与 Nacos 3.0 的认证协议不兼容。
- 多次调整配置（`application.yml`、`seata.properties`、`SEATA_IP`、namespace 等）均无法解决。
- **决策**：放弃 Seata 集成，启用 **T5.4 本地消息表 + 定时补偿 + 幂等消费** 备选方案。
- **后续记录**：详见 `docs/tech-debt.md`。

## 四、结论

### 4.1 可继续主线的组件

- **Actuator** → 直接转入 T1.2 正式实施。
- **Caffeine LoadBalancer** → 直接转入 T1.3 正式实施（仅 gateway 模块）。
- **Spring Kafka** → 继续推进 T3.5、T4.1。
- **Sentinel** → 继续推进 T6.1、T6.2，但需修正 `blockHandler` 问题。

### 4.2 需要切换备选方案的组件

- **Seata** → 放弃，启用 T5.4 本地消息表方案。

### 4.3 阻塞项与解决时间

| 阻塞项 | 状态 | 解决时间 |
|---|---|---|
| Seata 无法注册 | 已决策放弃 | 2026-10-09 |
| Sentinel blockHandler 被拦截 | 待修正 | T6.2 阶段 |
| 非 gateway 模块 LoadBalancer 警告 | 待评估 | T1.3 阶段 |

## 五、行动项

1. **回退 Spike 代码**：`git checkout sprint2`，删除 `sprint2/spike-compatibility` 分支（保留本报告）。
2. **保留正式实现**：Actuator 与 Caffeine 的 Spike 代码直接转为正式代码，随 T1.2/T1.3 一起提交。
3. **正式实施顺序**：
   - T1.1 网关路由迁移 Nacos
   - T1.2 全服务接入 Actuator
   - T1.3 Caffeine LoadBalancer 缓存
   - T2.x Product 模块业务
   - T3.x 秒杀核心
   - T4.x Kafka 订单
   - **T5.4 本地消息表 + 补偿**（替换原 T5.1~T5.3 Seata 方案）
   - T6.x Sentinel
   - T7.x 验收