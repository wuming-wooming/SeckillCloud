# 快速开始

## 前置依赖

- JDK 21+
- Maven 3.9+
- Docker Desktop（含 Compose）
- IntelliJ IDEA（推荐）

## 1. 启动基础设施

```bash
docker compose up -d
```

确认容器状态：

```bash
docker compose ps
```

应看到：nacos-3、redis-dev、pg-dev、zk-dev、kafka-dev 全部 Up。

Nacos 控制台：http://localhost:8080/nacos
账号：nacos / nacos

## 2. 初始化数据库

```bash
docker exec -i pg-dev psql -U dev -d micro_db < docs/sql/init.sql
```

验证：

```bash
docker exec -it pg-dev psql -U dev -d micro_db -c "\dt temp.*"
```

应列出 5 张表：sys_user、sys_role、sys_user_role、sys_permission、sys_role_permission。

## 3. 导入 Nacos 配置

进入 Nacos 控制台，命名空间选择 `dev`，Group 为 `DEFAULT_GROUP`，按 `docs/nacos/*.yml` 逐个新建配置：

| Data ID | 说明 | 必须 |
|---|---|---|
| seckill-common.yml | 公共配置（JWT、密码加密） | 是 |
| seckill-gateway.yml | 网关专属 | 否 |
| seckill-user.yml | 用户服务专属 | 是 |
| seckill-product.yml | 商品服务专属 | 是 |
| seckill-order.yml | 订单服务专属 | 是 |
| seckill-seckill.yml | 秒杀服务专属 | 是 |

配置内容见 `docs/nacos/`。

## 4. 构建

```bash
mvn -pl seckill-common -am clean install -DskipTests
```

`seckill-common` 是其它模块的依赖，必须先 install 到本地仓库。

## 5. 启动服务顺序

按依赖顺序启动，前一个就绪后再启动下一个：

1. seckill-gateway（8081）
2. seckill-user（9001）
3. seckill-product（9002）
4. seckill-order（9003）
5. seckill-seckill（9004）

各服务启动后，Nacos 控制台的「服务管理 → 服务列表」应能看到对应服务名。

## 6. 冒烟测试

```bash
# 注册
curl -X POST http://localhost:8081/api/user/register \
  -H "Content-Type: application/json" \
  -d '{"username":"u1","password":"password1","email":"u1@test.com"}'

# 登录
TOKEN=$(curl -s -X POST http://localhost:8081/api/user/login \
  -H "Content-Type: application/json" \
  -d '{"username":"u1","password":"password1"}' | jq -r .data.accessToken)

# 认证通过
curl http://localhost:8081/api/user/me -H "Authorization: Bearer $TOKEN"

# 认证失败
curl -i http://localhost:8081/api/user/me

# 跨服务透传
curl http://localhost:8081/api/product/ping -H "Authorization: Bearer $TOKEN"
curl http://localhost:8081/api/order/ping   -H "Authorization: Bearer $TOKEN"
curl http://localhost:8081/api/seckill/ping -H "Authorization: Bearer $TOKEN"
```

预期均返回 `{"code":200,"data":"pong from xxx, userId=1","msg":"success"}`。

## WSL 用户注意

WSL 2 的 localhost 与 Windows 宿主机隔离，需使用宿主机 IP：

```bash
HOST_IP=$(ip route show | grep -i default | awk '{ print $3}')
curl http://$HOST_IP:8081/api/user/me
```

或在 Windows 用户目录的 `.wslconfig` 开启 mirrored 网络模式后重启 WSL。