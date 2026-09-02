# Issue #319 可观测性与 HPA 审查记录

## Round 1 — 2026-09-02

审查基线：`origin/dev...feature/319-observability-hpa`；范围为 #319 的 HPA 清单、渲染器、实验运行器和契约测试。

| 编号 | 结论 | 可复现证据 | 统一修复 |
| --- | --- | --- | --- |
| R1-01 | 阻塞 | 实验配置要求 `finishedAtUtc`，运行器只写 `startedAtUtc`。 | 在 exit trap 写入完整结束时间。 |
| R1-02 | 阻塞 | 原始请求行不保留 `requestId`，无法与 Gateway/Assessment/Grade 日志关联。 | 每行记录 UTC、requestId、HTTP 状态和耗时；汇总器按新列解析。 |
| R1-03 | 阻塞 | RabbitMQ 为非关键 readiness 依赖仅存在声明，没有受控故障验证。 | 增加可选 RabbitMQ outage 模式：缩容 broker、确认 Assessment API 保持 Available、恢复原副本数并保留诊断。 |

上述问题修复并通过回归后才进入 Round 2；本记录不将尚未运行的真实 HPA 负载结果表述为 GREEN。

## Round 2 — 2026-09-02

| 编号 | 结论 | 可复现证据 | 统一修复 |
| --- | --- | --- | --- |
| R2-01 | 阻塞 | 运行器只采集 HPA 时间线，不验证 HPA 从基线扩容再缩容；无扩缩容仍可输出成功。 | 记录基线 replicas，增加有界等待：负载后必须大于基线、空闲后必须回到基线或更低，否则失败并保留原始 HPA 时间线。 |

## Round 3 — 2026-09-02

| 编号 | 结论 | 可复现证据 | 统一修复 |
| --- | --- | --- | --- |
| R3-01 | 阻塞 | RabbitMQ outage 是可选标志；省略标志仍可输出 `EXPERIMENT_READY`，未覆盖 AC-319-03。 | 每轮实验固定执行 outage 与恢复，移除可跳过路径。 |
| R3-02 | 阻塞 | 运行器只发匿名 GET，无法调用需 JWT 或 POST body 的 Assessment 提交/查询链。 | 增加不记录内容的 Authorization 文件、请求 method 与 body 文件参数；只在 raw evidence 留 requestId/status/耗时。 |

## Round 4 — 2026-09-02

| 编号 | 结论 | 可复现证据 | 统一修复 |
| --- | --- | --- | --- |
| R4-01 | 阻塞 | `git log origin/dev..HEAD` 显示 #319 提交之前还包含 9 个 dev-container/skill 维护提交；以当前分支创建 PR 会混入不属于本 issue 的变更。 | 从 `origin/dev` 新建干净的 #319 交付分支，仅顺序移植 #319 的测试、实现、文档和本审查记录提交；移植后重新执行完整回归。 |

Round 4 的静态与契约回归已通过；但真实集群实验尚未执行，原因是当前工作站未安装 `kubectl` 且没有 Kind 集群。该环境缺口不是代码 GREEN，不能以 `EXPERIMENT_READY` 代替 AC-319-01 至 AC-319-05 的实际证据。
