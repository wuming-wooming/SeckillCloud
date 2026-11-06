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