# Issue #317 五服务 Gateway 验证记录

## 1. 结论

当前阶段已完成五服务 Gateway 的契约、独立 workload、零信任请求 Header、request ID、
路由、限制、错误、单服务切换与回滚实现。Docker Linux 引擎恢复后，五 upstream 容器
运行时验收以及真实 Identity/Assessment 停机验收均已通过。

本记录不关闭 #317。#312 Course、#339 Grade、#342 Learning 尚未提供最终真实服务环境，
五服务主链也尚未运行，因此 PR 必须保持 Draft。

## 2. 环境与版本

| 字段 | 值 |
| --- | --- |
| 执行时间 | 2026-08-31（Asia/Shanghai） |
| 工作区 | Windows 11 + PowerShell + Git Bash/WSL |
| 分支 | `feature/317-gateway-routing` |
| `origin/dev` 基线 | `836eb38` |
| 静态验收代码 SHA | `d88990d` |
| Docker Client | 29.3.1 |
| Docker Server | 29.3.1（Docker Desktop Linux engine） |
| Java / Maven | Oracle JDK 24.0.2 / Maven 3.9.16 |
| 目标 Gateway | `nginx:1.27-alpine`，独立 `services/gateway/Dockerfile` |

Git Bash 在本机将 `python3` 解析到 WindowsApps 的无效占位程序，因此 workload validator
和 `verify-compose.test.sh` 使用 WSL Python 3.10 运行；Kind 脚本的 worktree Git 元数据校验
使用 Git Bash 运行。这些检查均使用仓库原脚本与原断言，未降低检查范围。

## 3. Red–Green 证据

| 行为 | RED（预期失败） | GREEN |
| --- | --- | --- |
| 五上游渲染 | 新模板缺失，renderer 退出 64；旧实现缺 Grade 且默认 `backend:8080` | 五个地址全部必填、校验并原子渲染 |
| 任意身份 Header | 白名单文件缺失，Node 返回 `ENOENT` | 关闭默认 Header 转发，只重建 17 个允许 Header |
| 五服务路由 | `identity-service needs a public proxy route` | workload/端口/公开路径、内部拒绝和错误边界通过 |
| 独立 workload | `services/gateway/Dockerfile` 缺失 | 镜像、entrypoint、Nginx 主配置和 Compose overlay 契约通过 |
| 五目标切换 | 旧脚本返回 `service must be auth, crs, assessment, or learning-grade` | Identity/Course/Assessment/Grade/Learning 独立切换与完整回滚通过 |
| Kind 边界 | 检出 frontend 仍挂载 `gateway-config` | frontend 与 Gateway 解耦，D3 清单回归仍通过 |
| Gateway 健康验证 | verifier 仍请求单体 `/api/v1/system/**` | 改为 `/health/live`、`/health/ready` |
| Nginx request ID 正则 | 容器启动失败：`unexpected "{"` | 以 Nginx 支持的带引号正则键渲染，运行时配置校验通过 |
| 运行时 fixture 前端 | Gateway `GET /` 为 502（fixture 固定监听 8080） | fixture 支持端口注入，前端按真实 `frontend:80` 监听 |
| Git Bash 挂载 | 1 秒超时模板未进入容器，慢请求错误返回 200 | Windows 挂载源路径显式转换并关闭该次 Docker 参数路径重写，504 断言通过 |
| 真实服务 JWKS | JWKS 健康探测返回 400（缺 request ID） | 验收脚本按 Identity 公共契约传递 request ID |
| 真实服务响应头 | CRLF 正则导致连续性断言误报 | 规范化响应头换行后精确比较 request ID |

每个生产行为均先出现对应失败，再补最小实现并复跑相关回归。

## 4. 已通过检查

### Gateway 专项

| 命令 | 结果 |
| --- | --- |
| `node scripts/gateway/tests/request-boundary.test.mjs` | PASS |
| `node scripts/gateway/tests/gateway-routing-contract.test.mjs` | PASS，五服务 |
| `node scripts/gateway/tests/gateway-workload-contract.test.mjs` | PASS |
| `node scripts/gateway/tests/identity-assessment-runtime-contract.test.mjs` | PASS |
| `bash scripts/gateway/tests/render-gateway-config.test.sh` | PASS |
| `bash scripts/gateway/tests/gateway-default-config.test.sh` | PASS |
| `bash scripts/gateway/tests/switch-gateway-target.test.sh` | PASS，含失败后完整回滚 |
| `bash scripts/gateway/tests/verify-gateway.test.sh` | PASS |
| `bash scripts/gateway/tests/kind-gateway-config.test.sh` | PASS |

### 共享契约与部署回归

| 命令 | 结果 |
| --- | --- |
| `node scripts/ci/verify-microservice-contract-v2.mjs` | PASS：5 OpenAPI、9 AsyncAPI message、4 正例、8 反例、16 mutation rejection |
| `python3 scripts/platform/validate_workload_manifest.py --manifest deploy/platform/workloads.json --schema deploy/platform/workload-manifest.schema.json` | PASS：10 workloads、5 ordered migration jobs |
| `docker compose -f deploy/docker/compose.yml -f deploy/docker/compose.gateway.yml config --quiet` | PASS；仅解析，不需要 Docker Server |
| `bash scripts/test/verify-compose.test.sh` | PASS |
| `bash scripts/test/verify-k8s-manifests.test.sh` | PASS |
| `bash scripts/test/verify-kind-scripts.test.sh` | PASS |
| `mvn -q test` | PASS：495 tests、0 failures、0 errors、13 skipped |
| `git diff --check` | PASS |
| `bash scripts/gateway/tests/gateway-runtime.test.sh` | PASS：五服务、Header 白名单、401/403/404/413/429/502/503/504、关闭重试 |
| `bash scripts/gateway/tests/identity-assessment-runtime.test.sh` | PASS：经 Gateway 登录；Identity 停止时 Assessment 本地校验 JWT 仍返回 404，并保留 request ID |

## 5. 当前阻塞且未计为通过

| 命令 | 当前结果 | 复测条件 |
| --- | --- | --- |
| 真实五服务主链 | 未执行 | #312、#339、#342 提供 `UNBLOCKED_BY`，#318 disposable 环境可用 |

运行时验收使用独立命名的临时 Docker network、七个临时容器和 H2 内存数据源；验收结束后
已全部删除，未触碰现有 `opengaussdb` 容器。

## 6. 已覆盖的契约

- 五个业务上游端口与 workload manifest 一致，Grade 与 Learning 不再共用目标。
- 未知 `/api/**` 和 `/internal/v2/**` 不进入任何业务服务。
- 请求 Header 默认不透传；任意 `X-User-*`、服务身份、内部 Token 和 hop-by-hop Header
  均不在允许集合，Bearer、内容 Header、条件请求和幂等键按契约保留。
- 合法 request ID 保留，非法或缺失值替换；Gateway 响应携带最终 request ID。
- 普通请求 10 MB，Assessment 上传 55 MB；连接 5 秒、普通 60 秒、上传 300 秒；
  Identity/查询/写入使用独立限流 zone。
- 全局禁止代理重试，尤其不重放非幂等提交和上传。
- 404、413、429、502、503、504 Gateway 错误使用脱敏 JSON；业务 401/403/404 保持透传。
- 切换状态必须恰好包含五个合法目标；旧四服务文件被拒绝，失败后恢复完整快照。
- 独立 Gateway workload 已提供；旧 Kind frontend 不再承担 Gateway 配置。

## 7. 剩余验收

#312、#339、#342 完成后，再通过 #318 的真实
disposable 环境执行：登录 → 课程 → 作业/实验 → 提交/评测 → 成绩 → 通知，并验证每个
下游独立 JWT/权限拒绝、request ID 连续、限流、超时、断连、超大请求和非幂等无重试。
只有这些结果通过后，才能更新本记录、将 PR 转为非草稿并发布最终 `UNBLOCKED_BY #317`。
