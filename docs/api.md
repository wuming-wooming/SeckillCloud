# API 文档（Sprint 1）

统一返回体：

```json
{ "code": 200, "msg": "success", "data": {} }
```

- `code`：200 成功，其他为错误码（见 `ErrorCode`）
- `msg`：英文/中文消息
- `data`：业务数据，可能为 `null`

所有接口前缀 `/api`，由网关转发。

## 用户 / 认证

### POST /api/user/register

请求：

```json
{ "username": "u1", "password": "password1", "email": "u1@test.com" }
```

响应：

```json
{ "code": 200, "msg": "success", "data": null }
```

### POST /api/user/login

请求：

```json
{ "username": "u1", "password": "password1" }
```

响应：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "expiresIn": 900
  }
}
```

### POST /api/user/refresh

请求：

```json
{ "refreshToken": "eyJ..." }
```

响应：同 login。

### POST /api/user/logout

请求头：`Authorization: Bearer <accessToken>`

响应：

```json
{ "code": 200, "msg": "success", "data": null }
```

### GET /api/user/me

请求头：`Authorization: Bearer <accessToken>`

响应：

```json
{
  "code": 200,
  "msg": "success",
  "data": { "id": 1, "username": "u1", "email": "u1@test.com" }
}
```

## 健康检查（透传验证）

- `GET /api/product/ping`
- `GET /api/order/ping`
- `GET /api/seckill/ping`

需携带 `Authorization: Bearer <accessToken>`，响应包含 `userId`。

## 秒杀

### POST /api/seckill/{activityId}/order

请求头：`Authorization: Bearer <accessToken>`

路径参数：`activityId`（秒杀活动 ID）

响应（成功）：

```json
{ "code": 200, "msg": "success", "data": true }
```

响应（库存不足）：

```json
{ "code": 2001, "msg": "库存不足", "data": null }
```

响应（重复秒杀）：

```json
{ "code": 2004, "msg": "请勿重复秒杀", "data": null }
```

说明：Sprint 2 最小闭环仅完成 Redis Lua 原子预减库存 + 用户去重，**不生成真实订单**。后续 Sprint 通过 Kafka 异步下单、本地消息表补偿实现最终一致性。

### 字段约定（Sprint 2 新增）

- **日期时间**：所有 `LocalDateTime` 类型字段（如 `startTime`、`endTime`、`createdAt`、`updatedAt`）序列化为 **`yyyy-MM-dd HH:mm:ss`** 格式字符串，时区为服务器本地时区（开发阶段 `Asia/Shanghai`）。
- **金额**：`BigDecimal` 类型字段（如 `price`、`seckillPrice`）序列化为 JSON 数字，保留数据库精度（如 `6999.00`）。
- 前后端对接时，若使用 `LocalDateTime`，反序列化必须显式指定相同 pattern（Spring MVC 端已通过 `@JsonFormat` 声明）。

## 商品 / 秒杀活动（Sprint 2 新增）

### GET /api/product/{id}

商品详情（走 Cache-Aside，不存在的 ID 直接返回 3001，且 60s 内重复请求不落 DB）。

响应：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "id": 1,
    "name": "iPhone 17",
    "description": "新款旗舰秒杀",
    "price": 6999.00,
    "stock": 200,
    "status": 1,
    "createdAt": "2026-11-10 10:30:00",
    "updatedAt": "2026-11-10 10:30:00"
  }
}
```

不存在：

```json
{ "code": 3001, "msg": "商品不存在", "data": null }
```

### GET /api/product/seckill/list

进行中的秒杀活动列表（按 `startTime` 升序）。

### GET /api/product/seckill/{activityId}

秒杀活动详情。

不存在：

```json
{ "code": 2005, "msg": "活动不存在", "data": null }
```

## 错误码

| code | 说明 |
|---|---|
| 200 | 成功 |
| 400 | 参数错误 |
| 401 | 认证失败 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 业务异常 |
| 1001 | 用户不存在 |
| 1002 | 用户名或密码错误 |
| 1003 | 用户名已存在 |
| 1004 | 用户被禁用 |
| 1005 | Refresh Token 无效 |
| 1006 | Token 无效 |
| 2001 | 秒杀库存不足 |
| 2002 | 活动未开始 |
| 2003 | 活动已结束 |
| 2004 | 请勿重复秒杀 |
| 2005 | 活动不存在 |
| 3001 | 商品不存在 |