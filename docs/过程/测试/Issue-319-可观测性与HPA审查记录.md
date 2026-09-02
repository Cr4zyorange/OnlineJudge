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
