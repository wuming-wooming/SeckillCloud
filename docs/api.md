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