# D6-AUTH 独立身份服务交付（v2）

本文件是 Issue #311 的交付与消费契约。身份服务唯一入口为 `services/identity`，是五服务架构中的 `identity` 服务；它独立拥有账号、角色、权限、会话、安全版本、签名密钥和身份失效事实。既有 `/api/v1/auth/**` 兼容入口保留，但 Bearer 凭证已改为短时、受众绑定的 JWT。

## 1. 信任边界

- `identity` 只包含身份领域及最小 Web、安全、异常代码，不包含 course、assessment、grade、learning、评测、存储或业务事件实现。
- `identity` 只拥有 `t_auth_*` 身份表和 `t_identity_outbox_event`；其他服务不得直接查询或写入这些表。
- 网关只做 TLS 终结、限流、路由并剥离外部伪造身份 Header；它不是唯一信任边界。业务服务必须用缓存 JWKS 在本地验证签名、`iss`、`aud`、允许的 `alg`、`kid`、`iat`、`exp` 和其本地安全版本投影。
- Identity 不可用时，已缓存公钥且未过期的会话仍可离线验证；JWKS 未缓存、未知 `kid` 的受限刷新及新登录会失败关闭，不能降级信任 Header 或跳过验证。

## 2. 用户 JWT、JWKS 与轮换

登录成功创建 15 分钟有效的服务端会话记录并签发 RS256 JWT。JWT 头部必须为 `alg=RS256` 并有 `kid`；负载至少含：

```text
userId, roles[], permissions[], sessionId, securityVersion, iat, exp,
iss=onlinejudge.identity.v2, aud=onlinejudge.api
```

`GET /.well-known/jwks.json` 返回当前与轮换重叠窗口内的 RSA 公钥，键均含 `kty=RSA`、`use=sig`、`alg=RS256`、`kid`、`n`、`e`。旧公钥必须保留至少一个最大 Token 生命周期加消费端缓存窗口。私钥只由 `IDENTITY_JWT_SIGNING_KEY` 注入；Compose/生产显式关闭临时开发密钥。

`OfflineJwtVerifier` 是业务服务应复用的纯协议实现，没有 Identity HTTP 客户端或 Identity 数据库依赖。它在请求路径拒绝未知 `kid`、非 RS256、签名错误、错误 issuer/audience、未来签发、过期和低于本地最小 `securityVersion` 的令牌。消费端负责定时 JWKS 刷新、一次受限未知 `kid` 刷新和安全版本事件投影，不得用网关 Header 代替验证。

## 3. 会话失效和安全版本

`securityVersion` 初始为 1。登出、密码变更、角色变更、角色权限变更和账号状态变更，在同一数据库事务中撤销相应会话、增加安全版本，并写入 PENDING outbox 事实 `identity.security-version.changed.v2`。事件负载含 `userId`、`securityVersion`（大于等于 1）与 `changeReason`：`LOGOUT`、`PASSWORD_CHANGED`、`ROLE_CHANGED`、`PERMISSION_CHANGED`、`ACCOUNT_STATUS_CHANGED`。

#337 负责将 outbox 事实以至少一次语义投递给业务服务；本 Issue 只创建原子、可投递的事实而不虚报投递器已完成。Identity 当前用户解析同时验证 JWT、会话状态、账号状态和安全版本，因此禁用、登出或版本落后的会话不能继续使用。

## 4. API

兼容 API 继续使用 `{code, message, data}` 包络。

| 分组 | 方法与路径 | 说明 |
| --- | --- | --- |
| 会话 | `POST /api/v1/auth/register` | 公开注册，仅学生身份 |
| 会话 | `POST /api/v1/auth/login` | 返回短时 RS256 Bearer JWT 与到期时间 |
| 会话 | `POST /api/v1/auth/logout` | 撤销当前会话并提升安全版本 |
| 会话 | `GET /api/v1/auth/me` | 返回经 JWT、会话和版本校验的主体 |
| 密钥 | `GET /.well-known/jwks.json` | 供业务服务缓存与离线验签的当前/重叠公钥 |
| 鉴权 | `POST /api/v1/auth/check-permission` | 校验当前主体权限码 |
| 资料、管理、审计 | 既有 `/api/v1/users/**`、`/api/v1/admin/**` | 保持 AUTH 兼容行为并提升安全版本 |

`contracts/v2/openapi/identity.openapi.json` 中的 `POST /internal/v2/service-tokens` 是服务工作负载 mTLS 令牌契约；在完整 mTLS 运行时配置落地前，不得用共享静态密钥或伪造 Header 代替它。

## 5. 数据库、配置与镜像

MySQL 身份迁移正本：

```text
database/migrations/identity/DB-IDENTITY-01-identity-user-session.sql
```

Compose 仅在初始化空 `onlinejudge_identity` 数据库时只读挂载此文件。业务账号、根口令和 JWT 私钥必须由环境显式提供，不能提交。

| 变量 | 默认/用途 | 要求 |
| --- | --- | --- |
| `IDENTITY_DATABASE_HOST` / `PORT` | `identity-db` / `3306` | Identity 独占数据库地址 |
| `IDENTITY_DATABASE_NAME` | `onlinejudge_identity` | Identity 独占 schema |
| `IDENTITY_DATABASE_USERNAME` | `onlinejudge_identity` | 最小权限业务账号 |
| `IDENTITY_DATABASE_PASSWORD` / `ROOT_PASSWORD` | 无 | 必填密钥 |
| `IDENTITY_JWT_SIGNING_KEY` | 无 | 必填 base64 PKCS#8 RSA 私钥 |
| `IDENTITY_JWT_KID` | 无 | 必填可轮换 key id |
| `IDENTITY_JWT_PREVIOUS_PUBLIC_KEYS` | 空 | `kid:base64-x509` 逗号列表 |
| `IDENTITY_JWT_ISSUER` / `AUDIENCE` | v2 默认值 | 消费端精确匹配 |
| `IDENTITY_SEED_DATA_ENABLED` | `false` | 仅 DEV/CI 可启用 |

Dockerfile 位于 `services/identity/Dockerfile`，镜像为 `onlinejudge/identity-service`，使用固定摘要基础镜像和 non-root `10001:10001`。Compose 文件为 `deploy/docker/compose.identity.yml`，只引用当前完整 SHA 标签。

## 6. 构建、探针和验收

```powershell
mvn -f services/identity/pom.xml test
mvn -f services/identity/pom.xml package -DskipTests
./scripts/test/verify-identity-service-boundary.ps1

$env:GIT_SHA = git rev-parse HEAD
docker build --build-arg "GIT_SHA=$env:GIT_SHA" -f services/identity/Dockerfile -t "onlinejudge/identity-service:$env:GIT_SHA" .
docker compose -f deploy/docker/compose.identity.yml up -d --wait
```

默认端口为 `8081`。`/health` 只检查进程，`/readiness` 执行最小数据库查询且依赖故障返回 503，`/version` 返回 `service=identity-service`、版本和 revision。验收覆盖 JWT 声明、JWKS 格式、轮换重叠、离线验签、issuer/audience/alg/kid/exp/securityVersion 拒绝、禁用/登出失效、迁移、非 root 镜像与 readiness。

## 7. 合并和集成门槛

#338 已确定五服务与 v2 契约，#311 可独立实现和评审。#309 服务分库迁移仍是最终非草稿/合并门槛：在其合并及使用方完成契约消费前，不切换生产流量，也不声称业务服务已接入在线 JWKS 刷新或 outbox 投递。
