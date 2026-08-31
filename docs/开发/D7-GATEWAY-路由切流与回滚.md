# D7-GATEWAY 五服务路由、切流与回滚

## 1. 目标与边界

Gateway 是 `deploy/platform/workloads.json` 定义的独立基础设施 workload，浏览器入口为
`http://gateway:8080`，不再把业务路由配置挂载到 frontend。它负责公开路径路由、请求
Header 零信任边界、request ID、请求大小、限流、超时和基础错误映射；Identity、Course、
Assessment、Grade、Learning 始终自行验证 JWT、权限和业务状态。

Gateway 不拥有业务事实，不执行 session introspection，不注入用户身份，不暴露
`/internal/v2/**`、Assessment Worker、RabbitMQ、MySQL、迁移任务或内部管理入口。
未知 `/api/**` 返回 404，不回退到单体 backend。

本实现分两阶段验收：

1. 五服务契约、独立 Gateway workload、disposable 五 upstream、切换和回滚可以提前完成。
2. #312 Course、#339 Grade、#342 Learning 完成后，在真实五服务环境完成登录到通知主链，
   才能关闭 #317。

## 2. 路由矩阵

| 服务 | 默认地址 | 公开路径 |
| --- | --- | --- |
| Identity | `identity-service:8081` | `/api/v1/auth/**`、`/api/v1/users/me/**`、`/api/v1/admin/**` |
| Course | `course-service:8082` | `/api/v1/courses/**`、`/api/v1/chapters/**`，排除下列 Assessment/Grade 课程子资源 |
| Assessment | `assessment-api:8083` | `/api/v1/labs/**`、`/api/v1/homeworks/**`、`/api/v1/submissions/**`、`/api/v1/evaluations/**`、课程下实验和作业 |
| Grade | `grade-service:8084` | `/api/v1/grades/**`、成绩项、成绩记录、汇总、发布、分析与复核公开路径，以及课程下成绩子资源 |
| Learning | `learning-service:8085` | `/api/v1/learning/**`、`/api/v1/notifications/**`、`/api/v1/reminder-rules/**` |

课程下的实验、作业与成绩正则路由先于通用 Course 路由匹配。Gateway 保留原始 URI、查询
参数和请求体，不将公开路径改写为 `/internal/v2/**`。

## 3. Header 与 request ID

每个代理 location 都引用 `deploy/gateway/proxy-request-headers.conf`。该文件先执行
`proxy_pass_request_headers off`，再只重建以下公开契约所需 Header：

- `Host`、`X-Real-IP`、`X-Forwarded-For`、`X-Forwarded-Proto`；
- `X-Request-Id`、`Authorization`；
- `Accept`、`Accept-Language`、`User-Agent`；
- `Content-Type`、`Content-Length`、`Content-Encoding`；
- `Range`、`If-Range`、`If-None-Match`、`If-Modified-Since`；
- `Idempotency-Key`。

因此任意大小写形式和未来新增的 `X-User-*`、
`X-OnlineJudge-Service-Authorization`、`X-Internal-Token`、标准 hop-by-hop Header，
以及客户端通过 `Connection` 声明的扩展 Header 都不会进入上游。Bearer 原样透传，
Gateway 不读取其 claims，也不把 claims 转成 Header。

`X-Request-Id` 只接受 1–128 个 ASCII 字母、数字、点、下划线、冒号或连字符，首字符
必须是字母或数字。合法值原样透传；缺失或非法值替换为 Nginx `$request_id`。最终值同时
发送给下游并通过响应 `X-Request-Id` 返回。

## 4. 限制、超时与错误

- 普通请求体上限 10 MB；Assessment 上传与提交为 55 MB。
- 连接超时 5 秒，普通读写超时 60 秒，Assessment 上传与提交读写超时 300 秒。
- Identity、普通查询、写入/上传分别使用独立限流 zone，超限返回 429。
- `proxy_next_upstream off` 全局生效；POST、PUT、PATCH、DELETE、multipart 和评测提交
  不会由 Gateway 自动重放。
- 下游 401、403、404、409、422 保持业务响应；连接失败、受控不可用和超时分别映射为
  502、503、504。
- Gateway 自有 404、413、429、502、503、504 使用统一 JSON，包含 `code`、`message`、
  `requestId` 和 `retryable`，不包含上游地址、Bearer、内部身份、文件路径或异常栈。

## 5. 镜像与启动

独立镜像入口为 `services/gateway/Dockerfile`。容器启动时：

1. 要求五个 `*_UPSTREAM` 环境变量全部存在且符合小写 `host:port`；
2. 从 `deploy/gateway/gateway.conf.template` 渲染到临时文件；
3. 拒绝未解析 token，原子替换运行配置；
4. 执行 `nginx -t`；
5. 验证成功后才启动 Nginx。

没有 `backend:8080` 默认值，也没有 Learning/Grade 共用目标。
`deploy/docker/compose.gateway.yml` 提供独立 Gateway overlay，并移除 frontend 的公开端口；
#318 负责将五个真实服务、Worker、RabbitMQ、MySQL、frontend 与 Gateway 编排成最终
disposable 环境。Kind 的 D3 frontend 已解除 Gateway ConfigMap 挂载，#318 提供独立
`deployment/gateway` 后，本文切换脚本即可对该 workload 执行 reload。

## 6. 单服务切换与回滚

状态目录默认是 `tmp/gateway-runtime`。`targets.env` 必须恰好包含五个键：

```dotenv
IDENTITY_UPSTREAM=identity-service:8081
COURSE_UPSTREAM=course-service:8082
ASSESSMENT_UPSTREAM=assessment-api:8083
GRADE_UPSTREAM=grade-service:8084
LEARNING_UPSTREAM=learning-service:8085
```

旧 `AUTH_UPSTREAM`、`CRS_UPSTREAM` 或 `LEARNING_GRADE_UPSTREAM` 状态文件会被明确拒绝，
不会被静默解释。示例：

```powershell
bash scripts/gateway/switch-gateway-target.sh --mode compose --service grade --target grade-canary:9084
bash scripts/gateway/switch-gateway-target.sh --mode compose --service learning --target learning-canary:9085
```

可用服务名为 `identity`、`course`、`assessment`、`grade`、`learning`。脚本只修改一个
目标，验证完整五键文件，渲染并重载 Compose `gateway` 或 Kind `deployment/gateway`，再执行
健康和受保护 smoke。后置检查失败时恢复完整前一快照并重复验证；回滚验证成功时原操作
退出 1，回滚也无法验证时退出 2。

## 7. 验证入口

不需要 Docker 的契约：

```powershell
node scripts/gateway/tests/request-boundary.test.mjs
node scripts/gateway/tests/gateway-routing-contract.test.mjs
node scripts/gateway/tests/gateway-workload-contract.test.mjs
node scripts/gateway/tests/identity-assessment-runtime-contract.test.mjs
bash scripts/gateway/tests/render-gateway-config.test.sh
bash scripts/gateway/tests/gateway-default-config.test.sh
bash scripts/gateway/tests/switch-gateway-target.test.sh
bash scripts/gateway/tests/verify-gateway.test.sh
bash scripts/gateway/tests/kind-gateway-config.test.sh
```

Docker Linux 引擎可用后执行五 disposable upstream：

```powershell
bash scripts/gateway/tests/gateway-runtime.test.sh
```

引擎不可用时脚本打印单行阻塞原因并退出 69，不产生容器或伪造通过。真实 Identity 与
Assessment 验证需要预先启动服务，并提供安全密码文件：

```powershell
$env:IDENTITY_BASE = 'http://127.0.0.1:8081'
$env:ASSESSMENT_BASE = 'http://127.0.0.1:18083'
$env:GATEWAY_BASE = 'http://127.0.0.1:8088'
$env:TEST_USERNAME = 'gateway-probe-user'
$env:TEST_PASSWORD_FILE = '/secure/path/gateway-probe.password'
$env:IDENTITY_CONTAINER = 'onlinejudge-auth-identity-service-1'
bash scripts/gateway/tests/identity-assessment-runtime.test.sh
```

脚本经 Gateway 登录，访问 Assessment 的缺失评测资源并得到鉴权后的 404，停止 Identity
后使用同一未过期 JWT 再次得到 404，从而证明 Assessment 本地验签；退出路径始终恢复
Identity。密码、Bearer 和登录响应只保存在权限受限的临时目录，日志不输出凭据。
