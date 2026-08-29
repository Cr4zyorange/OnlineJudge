# Issue #317 验证记录

关联交付：`closes #317`。

## 环境与版本

| 字段 | 值 |
| --- | --- |
| 执行时间 | 2026-08-29（Asia/Shanghai） |
| 执行环境 | Windows + Git Bash，Docker Desktop 4.66.1 |
| Docker | Client 29.3.1 / Server 29.3.1，Linux engine |
| Nginx | `nginx:1.27-alpine@sha256:65645c7bb6a0661892a8b03b89d0743208a18dd2f3f17a54ef4b76fb8e2f2a10` |
| Java / Maven | Oracle JDK 24.0.2 / Maven 3.9.16 |
| 基线 SHA | `6ca04f35d2b1ac139b8f765e5b0cb345904d0e76`（`origin/dev`） |
| 被测实现 SHA | `334ca75f2b7dba80bc65a3b8099a962404d344ac` |
| 分支 | `feature/317-gateway-routing` |

## Red-Green 记录

| 阶段 | 命令 | 可观察结果 |
| --- | --- | --- |
| RED：完整路由矩阵 | `mvn -q -Dtest=GatewayRoutingContractTest,DockerComposeContractTest test` | 15 条测试中 2 条失败；缺失精确根路径、直接成绩资源和 `proxy_next_upstream off` |
| RED：真实 Nginx 路由 | `bash scripts/gateway/tests/gateway-runtime.test.sh` | `/api/v1/courses` 返回 301，证明精确根路径没有进入 CRS |
| RED：Kind 交付 | `bash scripts/gateway/tests/kind-gateway-config.test.sh` | 退出 1；缺失 SPA 深链、挂载配置 `nginx -t` 与网关诊断采集 |
| RED：运维文档 | 部署文档/证据 `grep` 契约 | 退出 1；缺少默认目标、切流命令和 `closes #317` |
| GREEN / REFACTOR | 下表全部命令 | 全部退出 0；重构后再次完整复测 |

## 最终验证结果

| 范围 | 命令 | 总数 / 通过 / 失败 / 跳过 | 退出码 | 原始日志 |
| --- | --- | --- | --- | --- |
| 网关渲染、默认配置、路由矩阵、切流回滚、凭据保护、Kind 挂载、真实容器运行时 | 依次执行 `scripts/gateway/tests/*.test.sh` 的 7 个入口 | 7 / 7 / 0 / 0；运行时另含 12 条代表路由、401/403/404/502/504、1 次 multipart、1 条 SPA 深链 | 0 | [`logs/gateway-tests.log`](logs/gateway-tests.log) |
| Kubernetes 清单与 Kind 脚本 | `bash scripts/test/verify-k8s-manifests.test.sh`；`bash scripts/test/verify-kind-scripts.test.sh` | 2 / 2 / 0 / 0 | 0 | [`logs/kubernetes-contract-tests.log`](logs/kubernetes-contract-tests.log) |
| Compose 网关覆盖解析 | 渲染默认配置后执行 `docker compose -f deploy/docker/compose.yml -f deploy/docker/compose.gateway.yml config --quiet` 和 `config --services` | 2 / 2 / 0 / 0；服务为 mysql/backend/frontend | 0 | [`logs/compose-config.log`](logs/compose-config.log) |
| 后端完整测试 | `mvn test` | 412 / 405 / 0 / 7 | 0 | [`logs/backend-tests.log`](logs/backend-tests.log) |
| 工作树与临时资源 | `git diff --check`；查询 `oj-gateway-test-*` 容器和网络 | 2 / 2 / 0 / 0；残留容器 0、残留网络 0 | 0 | 本文件“安全与最终一致性” |

7 条跳过来自仓库既有的 Docker 沙箱和 MySQL 专项条件测试，不属于本 Issue 新增用例；本 Issue 新增的 3 条 JUnit 与容器运行时用例均执行并通过。

## 运行时验收覆盖

- 前端基址保持 `/api`，AUTH、CRS、Assessment、Learning & Grade 四个独立逻辑上游都收到各自公开路径；未选择的通用系统路径仍由 `backend:8080` 兼容处理。
- 课程下实验/作业/成绩路由优先于通用课程路由；`/courses`、`/homeworks`、`/notifications`、`/reminder-rules` 精确根路径和成绩直接资源路径均已实测。
- Bearer 到达目标上游，`X-User-Id`、`X-Username`、`X-User-Role`、`X-Permissions`、`X-Course-Ids`、`X-Manageable-Course-Ids` 六类浏览器伪造值全部被清除，并生成非空请求 ID。
- 2 MiB multipart 请求只到达 Assessment 一次；生产配置保留 55 MB 网关上限和 Assessment 300 秒读写超时，并显式关闭代理重试。
- 下游 401/403/404 保持状态；断连和超时稳定转换为脱敏 `GATEWAY_502` / `GATEWAY_504`；响应不包含上游主机名、堆栈或测试凭据。
- Nginx 容器内 `nginx -t` 与 SPA 深链通过；切流失败恢复前一目标并验证，临时测试容器和网络全部清理。

## 跨 Issue 联调边界

网关自身已使用四个独立一次性 HTTP 上游完成代理健康、路由、失败和核心 smoke。当前 `dev` 上 #312（CRS）、#313（Assessment）、#316（Learning & Grade）仍为 OPEN，AUTH 的 #328 PR 也尚未合并，因此本记录不冒充“当前 dev 已具备四个真实业务服务”。这些服务合并后，按部署文档第 5.9 节以各服务真实地址逐个执行同一切流命令和受保护 smoke；该外部依赖不改变本 Issue 已验证的网关配置、切流与自动回滚行为。

## 安全与最终一致性

- 原始日志未记录真实密码、真实 Token、私有镜像凭据、Kubernetes Secret 或未脱敏请求 Header；Compose 解析只使用字面量 `redacted-test-value`，运行时凭据为固定测试值。
- 证据生成后执行 `git diff --check` 为 0。
- `oj-gateway-test-*` 残留容器数为 0，残留网络数为 0。
