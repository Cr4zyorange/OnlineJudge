# Issue #317 三业务服务 Gateway 设计

## 1. 目标与基线

本设计以 #306 合入 `dev` 的三业务服务正本 `f948869799e2e561d6cfa2208acaf26627aa1ba1`
为唯一基线，将 PR #333 从旧的独立 Learning 五上游实现收敛为以下四类入口：

- Identity：身份、会话、当前用户和 JWKS。
- Course：CRS 与 LRN，包括 learning、notifications。
- Assessment：LAB、HWK、提交、评测和文件流。
- Grade：成绩规则、成绩查询、发布、分析和复核。

Gateway 是独立基础设施 workload，不承载业务逻辑，不替代业务服务的 JWT、权限或课程成员校验。

## 2. 范围

### 2.1 包含

- 冻结公开 `/api/v1/**` 到四类上游的唯一映射。
- 删除 `LEARNING_UPSTREAM`、独立 Learning 切流目标和相关运行时假设。
- 将 `/api/v1/learning/**`、`/api/v1/notifications/**` 转发到 Course。
- 保留并验证请求 Header 白名单、Bearer 透传、request ID、body 上限、超时、限流、
  无代理重试、脱敏网关错误和单上游故障隔离。
- 让 renderer、Nginx、Compose、Kind、workload 契约、切流/回滚、测试和证据使用同一四类上游模型。
- 使用四类固定 upstream stub 完成可重复的 Gateway 验收，包括路由、安全边界和故障隔离。

### 2.2 不包含

- 不修改业务 API 字段、错误码、数据库表或事件载荷。
- 不在 Gateway 中解析 JWT、注入用户身份、访问业务数据库或实现降级业务响应。
- 不新增独立 Learning 服务、`oj_learning` schema、迁移任务或第十个 workload。
- 不替代 #355、#357、#356、#339、#318 的业务服务和平台交付。

## 3. 路由设计

| 路由族 | 上游 |
| --- | --- |
| `/api/v1/auth/**`、`/.well-known/jwks.json` | Identity |
| `/api/v1/courses/**` 中课程、章节、资源、成员、公告入口 | Course |
| `/api/v1/learning/**`、`/api/v1/notifications/**` | Course |
| `/api/v1/labs/**`、`/api/v1/homeworks/**`、`/api/v1/submissions/**`、`/api/v1/evaluations/**` | Assessment |
| `/api/v1/courses/{courseId}` 下 LAB/HWK 路由 | Assessment |
| `/api/v1/grades/**`、课程下成绩、规则、发布、分析和复核路由 | Grade |
| `/internal/v2/**`、未知 `/api/**` | Gateway 受控 404，不转发 |
| `/` 与非 API 深链 | Frontend |

更具体的路径以 `contracts/v2/openapi/*.openapi.json` 和 #306 的 workload manifest 为正本；
Gateway 测试必须在新增或移动公共路径时拒绝未归属、重复归属和恢复 Learning 上游。

## 4. 组件与配置

### 4.1 Renderer

`render-gateway-config.sh` 只接受 Identity、Course、Assessment、Grade、Frontend 五个网络目标。
目标必须是小写 `host:port`，全部必填，渲染通过临时文件原子替换。出现旧
`LEARNING_UPSTREAM` 不作为有效配置输入，也不得在输出中留下占位符。

### 4.2 Nginx

Nginx 模板维持统一连接超时、普通读取超时、Assessment 上传读取超时、请求大小、限流区、
`proxy_next_upstream off` 和脱敏错误页。Course 的 learning/notifications 路由可以采用独立限流
位置，但其 `proxy_pass` 必须指向 Course。

### 4.3 部署与切流

Compose/Kind 仅注入四类业务入口所需的四个上游变量与 Frontend。切流状态恰好包含
Identity、Course、Assessment、Grade 四个目标；任何缺项、多项或旧五目标状态均拒绝。
切流失败恢复完整快照，并验证未被操作的三个目标保持不变。Kind 操作只能经
`kind-onlinejudge-ci` context；每次验收临时转发 `svc/gateway`，并按所切服务使用唯一 smoke
路径，避免跨服务健康检查造成假阳性。

## 5. 安全与故障语义

- 默认关闭上游请求 Header 透传，只重建公开允许集合。
- 删除任意 `X-User-*`、内部身份、服务授权和 hop-by-hop Header；保留 Bearer、内容、范围、
  条件请求和幂等键。
- 合法 request ID 原样贯穿；缺失或非法值生成新 ID，响应始终携带最终值。
- Gateway 不验 JWT。四类真实上游必须各自拒绝无效或伪造身份；Identity 暂时不可用时，已缓存
  JWKS 的业务服务仍在其既有缓存语义内本地验签。
- 上游主动断开或拒绝连接映射为稳定 502；连接、读取或发送超时（包括停止容器后地址仍可路由
  但连接黑洞的情形）映射为 504；受控不可用保持 503。错误体不泄露主机、容器、堆栈或凭据。
- 任一上游失败只影响其路由；其他三类业务入口、Gateway 健康和 Frontend 继续可用。
- 非幂等提交、上传和发布请求不得由 Gateway 自动重放。

## 6. 验收设计

### 6.1 自动化契约

- 四上游 renderer 正反例与残留占位符检查。
- 路由唯一归属、Learning/Notification 归 Course、无 Learning workload 的静态检查。
- Header 白名单、request ID、body/timeout/limit、错误页、无重试检查。
- 四目标逐服务切换、旧五目标拒绝、失败回滚检查。
- Compose/Kind/workload manifest 与 #306 的 9 workloads、4 migrations 一致。

### 6.2 Disposable 运行时

- 四个独立 fixture 上游与 Frontend 启动，逐路由确认实际目标。
- 逐个停止 Identity、Course、Assessment、Grade，确认目标路由稳定失败、其余三类可用。
- 验证 401/403/404 业务响应透传，413/429/502/503/504 网关响应稳定且脱敏。
- 验证深链回退、分页查询串、Range/条件 Header、multipart 与下载/流式响应不退化。
- 验证写请求只到达上游一次。

### 6.3 后续集成（不属于 #317 AC 门禁）

- 使用 #355/#357/#356/#339 的可部署 Head 和 #318 disposable 环境执行四类真实 smoke。
- 经 Gateway 登录，分别验证 Course、Assessment、Grade 独立 JWT 校验及伪造身份头拒绝。
- 执行登录 → 课程/通知 → 作业/实验 → 提交/评测 → 成绩主链，并覆盖会话过期、权限不足、
  空数据和上游不可用提示。
- 前端构建、组件测试和相关浏览器用例必须通过，且浏览器只访问 Gateway 公共入口。

## 7. 交付与证据

PR #333 目标分支为 `dev`。按 2026-09-01 `SCOPE_GATE_RESET`，四类固定 stub、镜像、健康和
隔离证据覆盖 AC-317-01 至 AC-317-06 后即可转为 Ready；不得将 #318/#320/#340 的真实服务或
浏览器工作作为 Draft 门禁。证据必须记录：

- `base=f948869...` 或其后续等价 `dev` 祖先、最终 head SHA。
- 四类路由清单、配置和健康结果。
- 每条命令的精确通过/失败计数与原始日志路径。
- 四类固定 upstream stub 的路由、伪造身份头、逐上游停机、回滚和 Kind 切流结果。
- AC-317-01 至 AC-317-06 的逐项映射。

真实上游 smoke 与浏览器主链由对应后续 issue 留存，不改变本 PR 的验收状态。
