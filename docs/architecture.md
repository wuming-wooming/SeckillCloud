# 系统架构

## 1. 整体拓扑

```
                        ┌────────────────────────┐
                        │      Client 端          │
                        │  Web / Qt6 / 小程序     │
                        └───────────┬────────────┘
                                    │ HTTPS
                                    ▼
┌──────────────────────────────────────────────────────────────┐
│                  Spring Cloud Gateway (8081)                 │
│   ┌─────────────┬─────────────┬─────────────┬─────────────┐  │
│   │  路由转发   │  AuthFilter │    CORS     │  限流(未来) │  │
│   └─────────────┴─────────────┴─────────────┴─────────────┘  │
└────────────────────────────┬─────────────────────────────────┘
                             │ lb://
        ┌────────────┬───────┴──────┬────────────┬────────────┐
        ▼            ▼              ▼            ▼            ▼
   seckill-user  seckill-product  seckill-order  seckill-seckill
      (9001)        (9002)           (9003)         (9004)
        │              │                │              │
        └──────────────┴────────────────┴──────────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
         PostgreSQL       Redis          Kafka
           (temp)      (未来缓存)      (未来削峰)
                             │
                             ▼
                          Nacos 3.0
                    (服务发现 + 配置中心)
```

## 2. 认证链路


1. Client POST /api/user/login
   → Gateway 白名单放行
   → User Service 校验密码（DelegatingPasswordEncoder）
   → 签发 Access Token (15m) + Refresh Token (12h)
   → Refresh Token 存入 Redis: seckill:refresh:{userId}

2. Client GET /api/user/me
   → Gateway AuthFilter 解析 Authorization: Bearer xxx
   → 校验签名、issuer、exp、type=access
   → 将 claims 写入请求头 X-User-Id / X-Username / X-User-Roles
   → 转发到 User Service

3. 下游服务通过 @RequestHeader("X-User-Id") 读取用户信息
   → 不再解析 JWT，信任网关透传
   → 前提：下游服务不暴露公网

## 3. 密码加密演进

- 当前：DelegatingPasswordEncoder，默认算法 bcrypt
- 密文格式：{bcrypt}$2a$10$...
- 老数据（无前缀）通过 setDefaultPasswordEncoderForMatches 兼容
- 用户登录时通过 upgradeEncoding 自动升级到当前默认算法
- 算法与强度通过 Nacos 配置 seckill.security.password.* 调整

## 4. 秒杀核心链路（防超卖 + 去重）

1. 用户请求 `POST /api/seckill/{id}/order`，网关 `AuthFilter` 解析 JWT，透传 `X-User-Id`。
2. 秒杀服务接收请求，将 `activityId` 和 `userId` 传给 Redis。
3. Redis 执行 Lua 脚本（单线程原子操作）：
   - 检查 `seckill:stock:{id}` 是否 > 0。
   - `SADD seckill:users:{id} {userId}`，返回 0 说明重复秒杀，直接拦截。
   - `DECR seckill:stock:{id}`，扣减库存。
4. 返回成功，后续进入 Kafka 异步下单流程（待实施）。