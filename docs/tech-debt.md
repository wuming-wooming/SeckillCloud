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

## 数据

- **使用 temp schema**：便于开发阶段自由变更，正式上线需迁移至独立 schema 或数据库。
- **无数据初始化脚本**：仅建表，未预置管理员账号。

## Sprint 2 计划处理

- Nacos 动态路由
- Sentinel 限流
- Spring Cloud CircuitBreaker 熔断降级
- Redis Lua 防超卖
- Kafka 异步削单
- Seata AT 模式分布式事务