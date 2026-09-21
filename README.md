# SeckillCloud 高并发秒杀商城系统

基于 Spring Boot 4 + Spring Cloud 2025.1 + Nacos 3 的微服务秒杀系统，覆盖高并发、防超卖、异步削峰、分布式事务等核心场景。

## 技术栈

| 层级 | 组件 | 版本 |
|---|---|---|
| JDK | OpenJDK | 21+ |
| 基础框架 | Spring Boot | 4.0.0 |
| 微服务 | Spring Cloud | 2025.1.0 |
| 微服务 Alibaba | Spring Cloud Alibaba | 2025.1.0.0 |
| 注册/配置中心 | Nacos | 3.0.0 |
| 网关 | Spring Cloud Gateway (MVC) | 5.0.0 |
| ORM | MyBatis-Plus | 3.5.17 (spring-boot4-starter) |
| 数据库 | PostgreSQL | 17 |
| 缓存 | Redis | 7.4 |
| 消息队列 | Kafka | 7.7.0 |
| 认证 | JJWT | 0.12.6 |
| 密码加密 | Spring Security Crypto | BCrypt / PBKDF2 / DelegatingPasswordEncoder |

## 服务架构

| 服务 | 端口 | 职责 |
|---|---|---|
| seckill-gateway | 8081 | 路由、鉴权、CORS、限流（Sprint 2 起） |
| seckill-user | 9001 | 用户、认证、RBAC |
| seckill-product | 9002 | 商品、库存 |
| seckill-order | 9003 | 订单、支付 |
| seckill-seckill | 9004 | 秒杀活动、秒杀核心 |

调用链：

```
Client → Gateway(8081) → Nacos 服务发现 → 下游微服务
```

详细架构见 [docs/architecture.md](docs/architecture.md)。

## 快速开始

见 [docs/quick-start.md](docs/quick-start.md)。

## API 文档

见 [docs/api.md](docs/api.md)。

## 已知技术债

见 [docs/tech-debt.md](docs/tech-debt.md)。

## 迭代进度

- [x] Sprint 1：基础设施 + 服务骨架 + 认证闭环
- [ ] Sprint 2：核心秒杀逻辑 + 缓存 + 消息队列 + 分布式事务
- [ ] Sprint 3：容灾降级 + Vue3 后台
- [ ] Sprint 4：Qt6 桌面端 + 压测 + 部署