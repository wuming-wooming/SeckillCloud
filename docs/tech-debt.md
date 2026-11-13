# 技术债与已知限制

以下问题在 Sprint 1 阶段不做修复，记录以便后续 Sprint 统一处理。

## 安全

- **下游服务不可暴露公网**：AuthFilter 透传 `X-User-Id`，下游服务信任此头，因此只能通过网关访问。当前 Docker Compose 未限制端口映射，正式部署时需确保仅 8081 映射到宿主机。
- **JWT secret 明文存 Nacos**：`seckill-common.yml` 中的 `seckill.jwt.secret` 为明文。后续可接入 Nacos 加密配置或环境变量。
- **Nacos 默认凭证**：`nacos/nacos` 仅用于开发，生产需修改 `NACOS_AUTH_*` 环境变量。

## 认证

- **JWT 无主动失效**：Access Token 无状态，签发后 15 分钟内无法撤销。通过短有效期 + Refresh Token 在 Redis 中可撤销缓解。
- **无细粒度权限校验**：当前所有登录用户等同权限，RBAC 数据库表已建但未接入。

## 工程

- **网关路由硬编码**：`application.yml` 中的路由尚未迁移到 Nacos 配置中心。
- **无健康检查端点**：未引入 `spring-boot-starter-actuator`，无可视化健康状态。
- **未做 Caffeine 缓存**：`spring-cloud-loadbalancer` 使用默认缓存实现，生产建议加 `spring-boot-starter-cache` + Caffeine。
- **未接入链路追踪**：无 Sleuth / Micrometer Tracing，问题定位能力弱。
### 版本兼容性（Sprint 2 新增）
- **Seata 与 Nacos 3.0 不兼容**：Seata 2.5.0/2.6.0 内置 nacos-client 1.4.6，2.7.0 内置 2.0.4，均与 Nacos 3.0 的认证协议（`NACOS_AUTH_IDENTITY_KEY` / `VALUE`）不兼容，无法注册。已放弃 Seata，改用「本地消息表 + 定时补偿 + 幂等消费」实现最终一致性。后续若 Nacos 升级至 3.x 兼容版本或 Seata 升级至支持 Nacos 3.0 的版本（预计 3.x），可重新评估。
- **Sentinel blockHandler 被全局异常处理器拦截**：限流生效但 `blockHandler` 未返回预期错误码。待 T6.2 阶段通过调整 `GlobalExceptionHandler` 或添加 `BlockExceptionHandler` 修复。
- **非 gateway 模块 LoadBalancer 默认缓存警告**：Caffeine 仅在 gateway 模块引入，其他模块若使用 LoadBalancer 仍会有 `default cache` 警告。Sprint 2 暂不处理，待有明确需求时统一在父 pom 引入。
### Jackson 2/3 共存（Sprint 2 插入任务）

- **现状**：Spring Boot 4.0.0 默认使用 Jackson 3（`tools.jackson.*`），但 classpath 上同时存在 Jackson 2（`com.fasterxml.jackson.core:*:2.20.x`），来源为：
    - `jjwt-jackson:0.12.6`（`seckill-common` 传递）——jjwt 编译期绑定 Jackson 2 `ObjectMapper`，**不可排除**；
    - `nacos-client:3.1.1`（Spring Cloud Alibaba 传递）——Nacos 官方要求 **3.3.0 之前必须保留 Jackson 2**，**不可排除**。
- **决策**：接受共存。Spring Boot 4 官方支持两者共存，Jackson 2 仅作为上述两个库的内部实现存在，项目代码**零 Jackson 2 databind 引用**。
- **代码规范**：
    - 注入 JSON 处理器统一用 `tools.jackson.databind.json.JsonMapper`（Spring Boot 4 自动配置的 Bean 类型），**禁止** `new ObjectMapper()`；
    - `@JsonFormat`、`@JsonProperty` 等注解保持 `com.fasterxml.jackson.annotation.*`，**不要**改为 `tools.jackson.annotation.*`（该包不存在）；
    - `@JsonSerialize` / `@JsonDeserialize` 需用 `tools.jackson.databind.annotation.*`；
    - `StringRedisTemplate` + `JsonMapper` 手动序列化的组合保持不变，**不切换**到 `RedisTemplate<String,Object>` 自动序列化，避免撞上 `GenericJackson2JsonRedisSerializer` 弃用与 default typing 行为变更。
- **后续评估**：Nacos 升级至 3.3+ 后可考虑排除其 Jackson 2；jjwt 官方宣布 Jackson 3 原生支持后再评估升级。

## 数据

- **使用 temp schema**：便于开发阶段自由变更，正式上线需迁移至独立 schema 或数据库。
- **无数据初始化脚本**：仅建表，未预置管理员账号。

## Sprint 2 计划处理

- Nacos 动态路由
- Sentinel 限流
- Spring Cloud CircuitBreaker 熔断降级
- Redis Lua 防超卖
- Kafka 异步削单
- ~~Seata AT 模式分布式事务~~ → **本地消息表 + 定时补偿 + 幂等消费**

## 压测发现的性能瓶颈（Sprint 2 新增）
- **高并发下 Tomcat 连接被拒**：在 JMeter 1000 并发（Ramp-up=1s）测试下，出现约 15% 的 `Connection refused`。经排查，并非 Redis 或 Lua 脚本瓶颈（成功请求下库存精确归零，无超卖），而是 Spring Boot 内置 Tomcat 默认最大线程数（200）及 Windows TCP 端口限制所致。**后续改进**：计划在 E6 阶段引入 Sentinel 限流，或在生产环境前置 Nginx / API 网关进行连接数控制与排队削峰。

## Sprint 2 新增
- `/internal/**` 本轮未在网关 AuthFilter 拦截 403，Sprint 3 统一做安全（T3.5 排期）。
- GlobalExceptionHandler 仅返回业务码，HTTP 恒 200；Sprint 3 按 ErrorCode→HttpStatus 映射，2006/2007→409、2001→400。
- 批量预热 `/internal/activity/warmup-all` 留 Sprint 3 定时任务，本轮仅单活动预热。
- **T2.6 参数校验 500 → 400 修复**：`@Valid` 失败抛 `MethodArgumentNotValidException`，原 `GlobalExceptionHandler` 无专用 handler 时被兜底为 500 且回传 `e.getMessage()`（泄露内部信息）。已补 5 类 handler（`MethodArgumentNotValidException` / `ConstraintViolationException` / `HttpMessageNotReadableException` / `MissingServletRequestParameterException` / `MethodArgumentTypeMismatchException`），`handleOther` 加 `log.error` 并返回固定提示。
- **T3.5 Kafka 占位**：`SeckillService.trySeckill` 成功分支有 `TODO[T3.5][E4-Kafka]` 标注，未来在**写 SUCCESS 之前**同步发送 Kafka，发送失败保留 QUEUING 交 T5.3 对账。
- **`/seckill/result` 横向越权防护**：已通过 `X-User-Id` 与 `result.userId` 比对实现；`requestId` 泄露场景下他人无法查询。