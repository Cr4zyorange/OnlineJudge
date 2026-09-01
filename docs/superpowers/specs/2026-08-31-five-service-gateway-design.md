# Issue #317 五服务统一入口设计（已废弃）

> 本文仅保留旧架构决策历史。当前实现与验收以
> `2026-09-01-three-service-gateway-design.md` 和 #306 三业务服务冻结文档为准；不得按本文
> 恢复独立 Learning upstream。

## 1. 状态与适用范围

本文是 Issue #317 在五服务 v2 架构下的设计正本，替代
`2026-08-29-gateway-routing-design.md` 中的“四服务”和“Learning & Grade
合并上游”假设。实现必须以 `docs/开发/D6-D7-五服务架构冻结-305.md`、
`docs/开发/D6-D7-五服务共享契约-v2.md`、`docs/adr/ADR-006-五业务服务与可靠消息契约.md`、
`contracts/v2/` 与 `deploy/platform/workloads.json` 为上游正本。

Gateway 是独立基础设施 workload，不拥有业务事实，也不是鉴权正确性的唯一来源。
它只暴露浏览器公开入口，不暴露 `/internal/v2/**`、Assessment Worker、RabbitMQ、
MySQL、迁移任务或服务内部管理端点。

当前 Identity 与 Assessment 已在 `dev` 提供真实服务实现；Course、Grade、Learning
仍由各自 Issue 交付。因此本 Issue 分两阶段验收：先完成五服务契约驱动的 Gateway
及 disposable fixture 验证，再在五个真实服务全部可用后完成真实主链验证。第一阶段
不能以绿色 fixture 测试关闭 #317。

## 2. 方案选择

评估过三个方案：

1. **独立 Nginx + 请求 Header 白名单 Gateway（采用）**：保留 PR #333 已验证的
   Nginx 转发、无重试、错误映射和可回滚框架；迁移到 `services/gateway/` workload，
   关闭客户端请求 Header 的默认转发，再只重建公开契约允许的 Header。该方案能自然
   清除任意未来 `X-User-*` 和 `Connection` 声明的逐跳 Header，无需依赖不可写的
   Nginx/njs `headersIn` 对象。
2. **Spring Cloud Gateway**：自定义过滤器可以满足全部安全规则，但会引入新的响应式
   Java 运行时、依赖树和构建边界，超出 #317 所需范围。
3. **纯 Nginx 固定 Header 名单**：实现最小，但无法证明未知的 `X-User-*` 被剥离，
   不满足零信任验收，因此不采用。

## 3. 组件与配置边界

新增的 `services/gateway/` 包含独立镜像、启动入口和 Nginx 主配置。
`deploy/gateway/` 保存五服务公开路由矩阵、Header 白名单、环境模板和部署配置。旧
`deploy/nginx/gateway.conf.template` 只作为迁移输入，最终不得继续成为生产正本。

五个上游与 workload manifest 一致：

| 上游 | 默认服务地址 | 公开路由族 |
| --- | --- | --- |
| Identity | `identity-service:8081` | `/api/v1/auth/**`、`/api/v1/users/me/**`、`/api/v1/admin/**` |
| Course | `course-service:8082` | `/api/v1/courses/**`、`/api/v1/chapters/**`，但排除 Assessment 与 Grade 所属课程子资源 |
| Assessment | `assessment-api:8083` | `/api/v1/labs/**`、`/api/v1/homeworks/**`、`/api/v1/submissions/**`、`/api/v1/evaluations/**` 及对应课程子资源 |
| Grade | `grade-service:8084` | `/api/v1/grades/**` 以及既有成绩项、成绩记录、分析、发布和复核公开路径 |
| Learning | `learning-service:8085` | `/api/v1/learning/**`、`/api/v1/notifications/**`、`/api/v1/reminder-rules/**` |

路由按“精确路径、特定课程子资源、通用服务前缀”的顺序匹配，不做业务路径语义变更。
路由契约测试必须同时读取 `deploy/platform/workloads.json` 与公开 Controller 清单，发现
服务数、端口或公开前缀漂移时失败。未知 `/api/**` 不回退到单体 backend，而是返回
稳定 404；`/internal/v2/**` 在进入上游前返回 404。

Gateway 公开 `/health/startup`、`/health/live` 和 `/health/ready`，满足 workload manifest。
健康响应不泄漏上游地址、令牌、文件路径或异常栈。`/` 与静态资源转发给 frontend，
SPA deep link 由 frontend 自己处理。

## 4. 请求处理与零信任边界

请求进入 Gateway 后按以下顺序处理：

1. 校验 `X-Request-Id`。只接受 1–128 个 ASCII 字母、数字、点、下划线、冒号或连字符，
   且首字符必须是字母或数字；缺失或非法时生成新的不透明 ID。
2. 关闭客户端请求 Header 默认透传，只重建 `Host`、转发链、`Authorization`、
   `Content-*`、内容协商、Range 条件请求、`Idempotency-Key` 与最终 request ID。
   因此所有大小写形式和未来新增的 `X-User-*`、服务身份 Header、内部 Token 以及
   `Connection` 声明的扩展逐跳 Header 都不会进入上游。
3. 显式清空标准 hop-by-hop headers，禁止客户端利用连接级语义影响上游。
4. 原样透传客户端 `Authorization`，不解析、不 introspect、不把 JWT claims 转成 Header。
5. 将最终 request ID 传给下游，并在响应 `X-Request-Id` 中返回同一值。
6. 按公开路径选择唯一业务上游；每个业务服务继续本地校验 JWT、audience、签名、
   securityVersion 与业务权限。

Gateway 不接受浏览器提供的服务 JWT，也不向公开请求注入
`X-OnlineJudge-Service-Authorization`。JWKS 由业务服务通过内部配置获取；浏览器不经
Gateway 使用 `/internal/v2/service-tokens`。

## 5. 可靠性、限制与错误

Gateway 为每个路由族定义连接、读取和发送超时。普通请求使用短时边界，评测提交与
附件上传使用现有业务允许的较长但有限边界。请求体默认受限，上传路由可使用明确的
较大上限；没有路由允许无限请求体。

限流至少区分匿名身份入口、普通查询和写入/上传。超过限制返回 429。Gateway 不缓存
业务成功响应，也不在客户端断连或上游失败时伪造成功。连接失败或无效上游响应返回
502，受控不可用返回 503，响应超时返回 504，超大请求返回 413。Gateway 自有错误均
采用统一 JSON 结构，包含稳定 `code`、安全 `message`、`requestId` 和 `retryable`，
不包含上游主机、端口、Bearer、内部身份、文件路径或异常栈。

Gateway 默认不进行代理重试。尤其 POST、PUT、PATCH、DELETE、multipart 和评测提交
不得被自动重放。若以后为某个 GET 增加一次有界重试，必须由单独契约明确声明并补充
失败测试；本 Issue 当前无需为了“支持重试”扩大行为面。

业务服务返回的请求体、状态码和安全响应头默认透传。401、403、404、409、422 等
业务错误不得被改写成 Gateway 成功或通用 5xx。

## 6. 切换与回滚

五个上游地址是部署输入，不来自浏览器 Header、查询参数或 Cookie。渲染器只接受
符合 `lowercase-host:port` 约束的值，并要求五个上游全部存在，不再提供单体 backend
默认值。配置变更流程为：渲染到临时文件、静态校验、原子替换、Nginx 配置验证、
reload、健康与路由 smoke；任一步失败均恢复上一个已验证配置并重新验证。

切换日志只记录服务逻辑名、配置版本、Git SHA、时间和结果。不得记录认证信息、环境
secret 或完整请求。旧的“四服务 target”状态文件不兼容五服务结构，升级时必须显式
迁移或拒绝，不能把 Learning 与 Grade 指向同一逻辑槽位。

## 7. 测试设计与阶段验收

所有行为遵守 RED–GREEN–REFACTOR。第一阶段先编写并亲眼确认以下失败测试：

- 旧渲染器缺少 Grade、仍允许单体默认值；
- 未知 `X-User-*`、服务身份 Header 或不在白名单中的自定义 Header 能到达 upstream；
- 合法 request ID 未被保留，非法 ID 未被替换；
- `/internal/v2/**` 或未知 `/api/**` 被错误转发；
- 五服务路径、课程子资源优先级或端口与 workload manifest 不一致；
- POST/上传在断连场景发生重试；
- 413、429、502、503、504 错误缺少 request ID 或泄漏内部信息；
- health、SPA、请求体和超时边界不符合契约。

GREEN 阶段只实现足够通过这些测试的 Gateway、配置渲染和五服务 disposable upstream。
随后接入真实 Identity 与 Assessment，至少验证登录/JWT 透传、Identity 停机下既有
有效 JWT 的业务服务离线验签边界，以及 Assessment 公开路由与独立鉴权。

第二阶段等待 #312、#339、#342 提供稳定公开 API 和真实 workload 后，补齐 Course、
Grade、Learning 路由及五服务 disposable 主链。最终关闭 #317 前必须证明浏览器经
Gateway 完成登录、课程、作业/实验、提交/评测、成绩、通知主链，并保留各服务独立
鉴权、故障和 request ID 证据。测试环境不得回退到单体 backend 或四服务 mock。

## 8. PR 与依赖状态

PR #333 在第一阶段改为 Draft，标题、描述和证据更新为五服务目标；在真实五服务验收
完成前不得使用绿色 CI 宣称 Issue 完成。第一阶段完成后向 #318、#320 发布
`STARTABLE_BY #317`，列出公开路由和 Gateway 入口。只有 #311、#312、#313、#339、
#342 的公开契约稳定且真实五服务验收通过后，才将 PR 转为非草稿并发布最终
`UNBLOCKED_BY #317`。

不属于 #317 的范围包括：业务数据库迁移、业务服务本地 JWT 实现、服务令牌签发、
RabbitMQ topology、outbox/inbox、业务 DTO 或公开 API 语义调整。发现正本之间冲突时，
Gateway 测试应失败并在 Issue 记录冲突，不以本地兼容分支静默修改公共契约。
