# D7-GATEWAY 三业务服务路由、切流与回滚

## 1. 目标与边界

Gateway 是 `deploy/platform/workloads.json` 定义的独立基础设施 workload，浏览器入口为
`http://gateway:8080`。它负责公开路径路由、请求 Header 零信任边界、request ID、请求
大小、限流、超时和基础错误映射；Identity、Course、Assessment、Grade 始终自行验证
JWT、权限和业务状态。

当前唯一业务拓扑为 Course（CRS+LRN）、Assessment（LAB+HWK）和 Grade（GRD）。Learning
与通知由 Course 承载，不存在独立 Learning upstream、`oj_learning` 或第十个 workload。
Gateway 不拥有业务事实，不执行 session introspection，不注入用户身份，不暴露
`/internal/v2/**`、Assessment Worker、消息中间件、数据库、迁移任务或内部管理入口。
未知 `/api/**` 返回 404，不回退到单体 backend。

## 2. 路由矩阵

| 上游 | 默认地址 | 公开路径 |
| --- | --- | --- |
| Identity | `identity-service:8081` | `/api/v1/auth/**`、`/api/v1/users/me/**`、`/api/v1/admin/**`、`/.well-known/jwks.json` |
| Course | `course-service:8082` | 课程、章节、资源、成员、公告，以及 `/api/v1/learning/**`、`/api/v1/notifications/**`、`/api/v1/reminder-rules/**` |
| Assessment | `assessment-api:8083` | `/api/v1/labs/**`、`/api/v1/homeworks/**`、`/api/v1/submissions/**`、`/api/v1/evaluations/**`，以及课程下实验和作业 |
| Grade | `grade-service:8084` | `/api/v1/grades/**`、成绩项、记录、汇总、发布、分析、复核，以及课程下成绩子资源 |

课程下 Assessment 与 Grade 子资源的正则路由先于通用 Course 路由匹配。Gateway 保留原始
URI、查询参数和请求体，不将公开路径改写为 `/internal/v2/**`。

## 3. Header 与 request ID

每个代理 location 都引用 `deploy/gateway/proxy-request-headers.conf`。该文件先关闭默认
请求 Header 转发，再只重建公开契约允许的转发、请求追踪、Bearer、内容、条件请求、
Range 和幂等 Header。

因此任意大小写形式及未来新增的 `X-User-*`、服务身份 Header、内部 Token、标准
hop-by-hop Header，以及客户端通过 `Connection` 声明的扩展 Header 都不会进入上游。
Bearer 原样透传，Gateway 不读取 claims，也不将 claims 转换成 Header。

`X-Request-Id` 只接受 1–128 个 ASCII 字母、数字、点、下划线、冒号或连字符，首字符
必须是字母或数字。合法值原样透传；缺失或非法值替换为 Nginx `$request_id`，最终值同时
发送给上游并通过响应 Header 返回。

## 4. 限制、超时与错误

- 普通请求体上限 10 MB；Assessment 上传与提交为 55 MB。
- 连接超时 5 秒，普通读写超时 60 秒，Assessment 上传与提交读写超时 300 秒。
- Identity、普通查询、写入/上传分别使用独立限流 zone，超限返回 429。
- `proxy_next_upstream off` 全局生效，不自动重放非幂等请求。
- 主动断开或拒绝连接映射为 502；连接、读取或发送超时映射为 504；受控不可用保持 503。
- Gateway 自有 404、413、429、502、503、504 使用统一脱敏 JSON；业务 401、403、404、
  409、422 保持上游响应。

## 5. 镜像与启动

独立镜像入口为 `services/gateway/Dockerfile`。容器启动时要求 Identity、Course、Assessment、
Grade 和 Frontend 五个网络目标均为合法 `host:port`，原子渲染配置、拒绝未解析 token，
通过 `nginx -t` 后才启动。`LEARNING_UPSTREAM` 不是有效输入。

`deploy/docker/compose.gateway.yml` 提供独立 Gateway overlay，并移除 Frontend 的公开端口。
#318 负责把三业务服务、Identity、Assessment Worker、基础设施、Frontend 与 Gateway 编排为
最终 disposable 环境。

## 6. 单上游切换与回滚

状态目录默认为 `tmp/gateway-runtime`，`targets.env` 必须恰好包含四个上游目标：

```dotenv
IDENTITY_UPSTREAM=identity-service:8081
COURSE_UPSTREAM=course-service:8082
ASSESSMENT_UPSTREAM=assessment-api:8083
GRADE_UPSTREAM=grade-service:8084
```

缺项、多项、旧键或 `LEARNING_UPSTREAM` 均被明确拒绝。可用服务名为 `identity`、`course`、
`assessment`、`grade`。示例：

```powershell
bash scripts/gateway/switch-gateway-target.sh --mode compose --service grade --target grade-canary:9084
```

脚本只修改一个目标，验证完整四键文件，渲染并重载 Gateway，再执行健康和受保护 smoke。
后置检查失败时恢复完整前一快照并重复验证；回滚验证成功时原操作退出 1，回滚也无法验证
时退出 2。

Kind 模式只经 `scripts/kind/lib.sh` 的 `kindlib_kubectl` 操作 `kind-onlinejudge-ci` 上下文，
绝不依赖调用者当前 kubeconfig context。每次重载后，脚本临时转发 `svc/gateway` 的
`8080` 到 `127.0.0.1:${GATEWAY_KIND_LOCAL_PORT:-18090}`，验证结束或失败回滚前均回收该
进程。受保护 smoke 路径由所切服务固定：Identity 为 `/api/v1/auth/me`、Course 为
`/api/v1/courses`、Assessment 为 `/api/v1/homeworks`、Grade 为 `/api/v1/grades`；因此不能
用另一服务的健康响应误判本次切流成功。

## 7. 验证入口

无需真实业务服务即可运行 Gateway 契约、渲染、默认配置、切流回滚、部署与 disposable
运行时测试。`gateway-runtime.test.sh` 会启动四个独立 fixture upstream 与 Frontend，覆盖
路由、深链、查询串、Range 下载、流式响应、错误边界和逐上游停机隔离，并在退出时清理。

按项目负责人 2026-09-01 的 `SCOPE_GATE_RESET`，#317 的 AC-317-01～06 以四类固定 upstream
stub 的可重复验收收口，不等待 #355、#357、#356、#339、#318。真实服务、浏览器主链和跨服务
停机演练由 #318/#320/#340 继续负责；它们是后续集成证据，不能作为本 issue 保持 Draft 的条件。
