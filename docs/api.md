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

请求体：

```json
{ "requestId": "550e8400-e29b-41d4-a716-446655440000", "quantity": 1 }
```

- `requestId`：**前端生成的 UUID**，必填，长度 ≤ 64。同 requestId 重复提交返回相同结果（幂等）。
- `quantity`：当前恒为 1（Lua 固定 DECR 1），传其他值返回 400。

**响应统一结构**（HTTP 200，业务结果看 `data.status`）：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "requestId": "550e8400-...",
    "activityId": 2,
    "userId": 1001,
    "status": "SUCCESS | QUEUING | FAIL",
    "errorCode": null,
    "errorMsg": null,
    "createdAt": "2026-11-13T10:00:00",
    "updatedAt": "2026-11-13T10:00:01"
  }
}
```

| `data.status` | 含义 | 前端动作 |
|---|---|---|
| `SUCCESS` | 秒杀资格已获取（`errorCode` 为 null） | 进入支付/订单页 |
| `QUEUING` | 处理中（同 requestId 并发或前次未完成） | 轮询 `GET /result/{requestId}` |
| `FAIL` | 业务失败，看 `errorCode`（2001/2002/2003/2004/2005） | 展示对应错误；如需重试请**生成新 requestId** |

> `status=QUEUING` 时同时会返回 `code=2006`（见下），前端优先按 `code` 分流到轮询逻辑。
> **时间字段说明**：`createdAt` / `updatedAt` 遵循项目统一契约 `yyyy-MM-dd HH:mm:ss`（与 Product / SeckillActivity 一致）。
> 注意：Sprint 2 T3.5 早期版本曾使用 ISO-8601 格式，升级后 Redis 中的旧 `seckill:result:*` 数据会在下次访问时自动 drop，用户重试同 requestId 可正常处理。

**特殊情况**（R.fail 直接返回，无 data）：

| HTTP | code | 场景 |
|---|---|---|
| 200 | 2006 | `status=QUEUING` 并发冲突（可视为处理中） |
| 200 | 401 | 未携带 X-User-Id |
| 200 | 400 | requestId 空 / quantity ≠ 1 |

### GET /api/seckill/result/{requestId}

请求头：`Authorization: Bearer <accessToken>`

响应：同下单接口的 `data` 结构。

| 场景 | code | 说明 |
|---|---|---|
| 存在且 userId 匹配 | 200 | 返回 `data`（任一 status） |
| requestId 不存在 / 已过期 | 2008 | `秒杀结果不存在` |
| userId 不匹配 | 403 | 越权访问 |

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
| 2006 | 请求处理中（秒杀幂等状态机 QUEUING） |
| 2007 | 活动已开始，禁止预热 |
| 2008 | 秒杀结果不存在 |
| 3001 | 商品不存在 |