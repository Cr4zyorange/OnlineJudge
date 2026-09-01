# Issue #317 三业务服务 Gateway 验证记录

## 1. 当前结论

本分支已基于 #306 合入 `dev` 的三业务服务基线完成 Gateway 收敛：公开入口只包含
Identity、Course、Assessment、Grade 四类 upstream，Learning、通知与提醒由 Course 承载。
四上游 disposable 路由、零信任 Header、深链/文件/流式响应、错误语义、逐上游停机隔离、
切流与完整回滚已自动化验证。

项目负责人已于 2026-09-01 通过 `SCOPE_GATE_RESET` 明确：#317 不等待 #355、#357、#356、
#339、#318；AC-317-01～06 按四类固定 upstream stub 收口。真实服务、浏览器主链和跨服务
停机已移交 #318/#320/#340，不作为本 PR 保持 Draft 的理由。

## 2. 基线与范围

| 字段 | 值 |
| --- | --- |
| 执行日期 | 2026-09-01（Asia/Shanghai） |
| 分支 | `feature/317-gateway-routing` |
| `dev` 基线 | `f948869799e2e561d6cfa2208acaf26627aa1ba1` |
| 已验证实现 Head | `c15c734`（证据文档提交前） |
| 部署拓扑 | 9 workloads、4 个有序 migration jobs、无独立 Learning |
| Gateway 镜像 | `services/gateway/Dockerfile`，基于 `nginx:1.27-alpine` |

Git Bash 在本机把 `python3` 解析到 WindowsApps 的无效占位程序，因此 Python 部署验证使用
WSL Python 运行；测试脚本及断言未修改或降低。Docker 运行时使用独立临时 network/container，
退出后清理，不触碰现有 `opengaussdb`。

## 3. Red–Green 证据

| 行为 | RED（已亲眼确认） | GREEN |
| --- | --- | --- |
| 四上游归属 | 路由、workload、renderer、默认配置均因残留 Learning target 失败 | Learning/通知/提醒统一指向 Course，服务数为 4 |
| 四目标回滚 | 旧脚本要求五键状态并接受 Learning | 状态恰好四键，拒绝 Learning，失败时完整恢复四目标 |
| 运行时兼容 | fixture 无法证明查询串、Range 和流式响应 | 深链、查询串、206 下载和流式响应全部通过 |
| 故障隔离 | 停止容器后的实际连接行为与预期错误码不一致 | 停止任一 upstream 稳定返回脱敏 504，其余三类持续 200；主动断开另测 502 |
| 部署边界 | Kind 清单仍声称 five-service Gateway | 与 9 workloads、4 migrations、无 Learning 的 #306 契约一致 |
| Kind 切流安全 | 原脚本使用调用者当前 context，且未绑定所切服务 smoke | 固定 `kind-onlinejudge-ci`、临时转发 `svc/gateway`，并以服务专属 smoke 验证 |

每项生产行为均先由失败测试冻结预期，再补最小实现并复跑。

## 4. 当前自动化结果

### Gateway 专项

| 验证 | 结果 |
| --- | --- |
| 请求边界、路由、workload、真实服务脚本契约 | PASS |
| renderer、默认配置、切流/回滚、健康检查、Kind 解耦 | PASS |
| disposable 运行时 | PASS：`services=4 deep-link=pass stream=pass isolation=4/4 headers=request-allowlist status=401/403/404/413/429/502/503/504 retry=off` |
| Identity + Assessment 真实服务门禁 | PASS（既有可部署服务范围） |

### 三服务共享契约与部署

| 验证 | 结果 |
| --- | --- |
| `verify-microservice-contract-v2.mjs` | PASS：4 OpenAPI、10 AsyncAPI messages、4 正例、8 反例、18 mutations rejected |
| workload manifest validator | PASS：9 workloads、4 ordered migration jobs |
| Compose overlay 解析及 `verify-compose.test.sh` | PASS |
| Kubernetes 与 Kind 仓库脚本 | PASS |
| Java Gateway/Compose 定向测试 | PASS：16 tests、0 failures、0 errors、0 skipped |
| 后端完整 `mvn test` | PASS：503 tests、0 failures、0 errors、14 skipped |
| 前端 `typecheck`、`test:unit`、`build` | PASS：54 files、566 tests，生产构建成功 |
| `git diff --check` | PASS |

后端完整测试在 Maven 容器中运行，并只读挂载主仓库 Git 元数据，使 worktree 内的
`migrationChecksumsStayLfWhenGitAutocrlfIsEnabled` 保持原断言并通过。

## 5. AC 映射

| AC | 当前状态 | 证据/缺口 |
| --- | --- | --- |
| AC-317-01 | 自动化通过 | learning、notifications、reminder-rules 唯一转发到 Course；无第五业务 upstream |
| AC-317-02 | 自动化通过 | LAB/HWK→Assessment，Grade→Grade，身份→Identity；四 fixture 已实际命中 |
| AC-317-03 | 自动化通过 | 任意伪造身份 Header 已过滤，Bearer 保持原样；Gateway 不解析或注入身份信息 |
| AC-317-04 | disposable 通过 | 四个 upstream 逐个停止时目标路由稳定 504，其余三类与 Gateway 健康保持可用 |
| AC-317-05 | disposable 通过 | 深链、查询串、Range、下载、流式、multipart、分页契约通过 |
| AC-317-06 | 自动化通过 | 独立 build/image/start/health/contract 通过；Kind 切流固定 context、端口转发和服务专属 smoke 通过 |

## 6. 后续集成边界

真实 upstream、浏览器主链和跨服务停机由 #318/#320/#340 验收。它们不改变以上 #317 的
stub 验收结论。
