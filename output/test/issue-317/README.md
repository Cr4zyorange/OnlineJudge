# Issue #317 三业务服务 Gateway 验证记录

## 1. 当前结论

本分支已基于 #306 合入 `dev` 的三业务服务基线完成 Gateway 收敛：公开入口只包含
Identity、Course、Assessment、Grade 四类 upstream，Learning、通知与提醒由 Course 承载。
四上游 disposable 路由、零信任 Header、深链/文件/流式响应、错误语义、逐上游停机隔离、
切流与完整回滚已自动化验证。

本记录不关闭 #317，PR #333 必须保持 Draft。#355、#357、#356、#339 尚未全部提供可部署
Head，#318 的最终 disposable 环境也尚未完成，因此四类真实服务 smoke、完整浏览器主链与
真实逐服务停机证据不能计为通过。

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
| AC-317-02 | 自动化通过，真实 smoke 待上游 | LAB/HWK→Assessment，Grade→Grade，身份→Identity；四 fixture 已实际命中 |
| AC-317-03 | 部分通过 | 任意伪造身份 Header 已过滤；Identity/Assessment 实服务门禁通过，Course/Grade 独立验签待可部署 Head |
| AC-317-04 | disposable 通过，真实环境待补 | 四个 upstream 逐个停止时目标路由稳定 504，其余三类与 Gateway 健康保持可用 |
| AC-317-05 | disposable 通过，浏览器待补 | 深链、查询串、Range、下载、流式、multipart、分页契约通过；真实浏览器流程待 #318 |
| AC-317-06 | 部分通过 | 独立 build/image/start/health/contract 通过；最终浏览器与四真实 upstream 证据待补 |

## 6. 剩余门禁

以下条件全部成立前，不把 PR 转为非草稿，也不发布 `READY_FOR_INTEGRATION`：

1. #355、#357、#356、#339 提供可部署且契约稳定的 Head；
2. #318 提供最终 disposable 环境；
3. 经 Gateway 完成登录 → 课程/通知 → 作业/实验 → 提交/评测 → 成绩的真实浏览器主链；
4. 四类真实服务 smoke、伪造身份、独立 JWT 验证、逐上游停机和恢复全部留存；
5. AC-317-01 至 AC-317-06 均有最终证据。
