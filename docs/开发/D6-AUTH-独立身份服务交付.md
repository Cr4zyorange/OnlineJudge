# D6-AUTH 独立身份服务交付

本文档是 Issue #311 的服务消费与运维契约。身份服务位于 `services/auth-service`，保持现有 AUTH HTTP 路径与响应包络兼容，并独立拥有构建、配置、数据库、探针和容器交付物。

## 1. 服务边界

- 服务只包含 AUTH 领域代码及其最小公共 Web、安全和异常契约，不包含 CRS、LAB、HWK、GRD、LRN、评测、存储或跨模块事件实现。
- 数据库只拥有 `t_auth_user`、`t_auth_role`、`t_auth_permission`、`t_auth_user_role`、`t_auth_role_permission`、`t_auth_session`、`t_auth_audit_log`。
- 其他服务不得直接查询、写入或关联 `t_auth_*`。当前兼容入口是 AUTH HTTP API；后续内部身份契约由 #310 固化，统一入口与流量切换由 #317 完成。
- 为避免迁移期间破坏现有主流程，本 Issue 不删除单体中的 AUTH 兼容实现，也不修改前端路由。

## 2. HTTP API

除注册、登录和三个系统探针外，所有 `/api/v1/**` 请求都必须携带：

```http
Authorization: Bearer <opaque-session-token>
```

服务不信任 `X-User-Id`、`X-User-Name`、`X-User-Role` 等调用方可伪造 Header，不接受 Header 降级认证，也不把任意 JWT 字符串当作会话。

现有兼容端点如下：

| 分组 | 方法与路径 | 说明 |
| --- | --- | --- |
| 会话 | `POST /api/v1/auth/register` | 公开注册，仅允许学生身份 |
| 会话 | `POST /api/v1/auth/login` | 账号、邮箱或手机号登录，返回不透明 Bearer 会话 |
| 会话 | `POST /api/v1/auth/logout` | 只撤销本次携带的会话 |
| 会话 | `GET /api/v1/auth/me` | 返回当前可信身份主体 |
| 鉴权 | `POST /api/v1/auth/check-permission` | 校验当前主体的权限码 |
| 资料 | `GET /api/v1/users/me` | 查询当前用户资料 |
| 资料 | `PUT /api/v1/users/me` | 更新当前用户资料 |
| 资料 | `PUT /api/v1/users/me/password` | 修改密码并撤销该用户全部已有会话 |
| 管理 | `/api/v1/admin/users/**` | 用户查询、创建、状态和角色管理 |
| 管理 | `/api/v1/admin/roles/**` | 角色和角色权限管理 |
| 管理 | `GET /api/v1/admin/permissions` | 权限清单 |
| 审计 | `GET /api/v1/admin/audit-logs` | 审计日志分页查询 |

所有业务响应继续使用 `{code, message, data}` 包络。认证缺失、伪造、过期或已撤销会话返回 HTTP `401`、`ERR-AUTH-04`；主体已认证但权限不足返回 HTTP `403`、`ERR-AUTH-05`。账号禁用、冻结或锁定按既有 AUTH 错误码处理。依赖故障返回安全的通用错误，不向 HTTP 响应暴露连接串、口令或堆栈。

## 3. 可信身份主体

`GET /api/v1/auth/me` 的 `data` 字段保持以下字段：

```text
id, username, userType, displayName, phone, email, avatarUrl,
accountStatus, roles[], permissions[]
```

调用方只能把身份服务通过已验证 Bearer 会话返回的主体作为权限决策输入。身份服务不可用、超时或响应不合法时必须 fail closed：不得使用请求 Header、缓存中的过期角色或匿名默认角色继续执行受保护操作。

当前 #311 不新增未定稿的服务间 Header、JWT、刷新令牌或内部接口。需要跨服务传播身份时，必须等待 #310 的契约和 #317 的网关接入，不得自行扩展公共 DTO。

## 4. 数据库与种子数据

MySQL 正本位于：

```text
services/auth-service/src/main/resources/db/migration/DB-AUTH-01-auth-user-session.sql
```

Compose 将该文件只读挂载到空 `onlinejudge_auth` 数据库的初始化目录。`auth-db` 中的业务账号由 MySQL 官方镜像创建，只获得该数据库的权限；根口令和业务口令均必须由环境显式提供。

| 变量 | 默认值 | 要求 |
| --- | --- | --- |
| `AUTH_DB_HOST` | `auth-db` | Compose 服务名或数据库主机 |
| `AUTH_DB_PORT` | `3306` | MySQL 端口 |
| `AUTH_DB_NAME` | `onlinejudge_auth` | AUTH 独占数据库 |
| `AUTH_DB_USER` | `onlinejudge_auth` | AUTH 独占账号 |
| `AUTH_DB_PASSWORD` | 无 | 必填，不得提交到仓库 |
| `AUTH_DB_ROOT_PASSWORD` | 无 | 仅数据库容器初始化必填 |
| `AUTH_SEED_DATA_ENABLED` | `false` | 只允许 DEV/CI 显式启用 |

启用种子数据时会创建 `student001`、`teacher001`、`admin001` 三个既有演示账号及对应角色；默认和生产环境均关闭。已有持久卷不会重复执行 MySQL 初始化脚本；后续结构调整必须新增前向迁移，不能原地修改已发布迁移。

## 5. 构建、启动与探针

本地 Java 21 构建：

```powershell
mvn -f services/auth-service/pom.xml test
mvn -f services/auth-service/pom.xml package -DskipTests
java -jar services/auth-service/target/onlinejudge-auth-service-0.1.0-SNAPSHOT.jar
```

默认本地端口为 `8081`。三个公开探针为：

- `GET /api/v1/system/health`：进程存活，不探测外部依赖。
- `GET /api/v1/system/readiness`：执行最小数据库查询；数据库不可用时返回 HTTP `503`、`service unavailable`。
- `GET /api/v1/system/version`：返回 `service=auth-service`、版本与源码修订号。

边界与独立构建验证：

```powershell
$env:MAVEN_CMD = "mvn"
./scripts/test/verify-auth-service-boundary.ps1
```

## 6. 容器交付

镜像必须用当前完整 Git SHA 构建并标记，运行时使用固定基础镜像摘要和非 root 用户 `10001:10001`：

```powershell
$env:GIT_SHA = git rev-parse HEAD
docker build --build-arg "GIT_SHA=$env:GIT_SHA" `
  -f deploy/docker/auth-service.Dockerfile `
  -t "onlinejudge/auth-service:$env:GIT_SHA" .
```

提供 `AUTH_DB_PASSWORD`、`AUTH_DB_ROOT_PASSWORD` 和当前 `GIT_SHA` 后启动：

```powershell
docker compose -f deploy/docker/compose.auth.yml up -d --wait
```

Compose 不隐式构建镜像，只引用精确 SHA 标签。发布证据必须记录镜像 ID、OCI revision、运行用户、三个探针、数据库账号授权范围，以及读取 `mysql.user` 被拒绝的结果。

## 7. 验收边界

自动化覆盖现有注册、登录、登出、当前用户、资料、角色权限、审计、伪造 Header/Bearer、禁用账号、越权、并发会话、密码变更全会话撤销、探针、依赖故障、种子开关和数据库归属。最终证据索引位于 `output/test/issue-311/README.md`。

Issue #310 和 #317 未完成前，独立服务可单独构建、启动和验收，但生产流量仍不能擅自切换；这是明确的集成依赖，不是 #311 内自行发明契约的授权。
