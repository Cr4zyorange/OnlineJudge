# Issue #379 DevOps 归档索引

## 冻结身份

| 字段 | 值 |
| --- | --- |
| issue | #379 |
| final SHA | `c56b16f916b4a4c3d33915aa37beab6b05c72888` |
| 基线 | `origin/dev`，2026-09-03 读取 |
| 归档结论 | `BLOCKED` |
| 失败门 | `ci-quality-gate` run [33698399654](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33698399654) |
| D3 run | [33698830921](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33698830921)，`quality_gate=failure`、build/deploy skipped |

## 覆盖范围

- `source/` 冻结了最终 SHA 中实际存在的 workflow、Docker/Compose、Kubernetes/Kind、平台清单、数据库迁移/seed/账号矩阵和运行脚本。
- `evidence/actions/current/` 保留最终 SHA 的 CI 与 D3 原始 artifacts，包括失败测试报告、环境信息、契约报告和阻断结果。
- `evidence/actions/historical/` 保留可追溯的历史成功部署（旧三工作负载 D3）和真实 Kind 失败诊断；它们只用于证明成功/失败/诊断链路存在，不替代最终 SHA 结论。
- 当前最终 SHA 没有 D8 HPA 实验证据文件；仓库中也没有 Helm source。两项均在验收矩阵中明确标记，未凭空补造。

## 配置摘要

最终 `deploy/platform/workloads.json` 声明 9 个 workload：`gateway`、
`identity-service`、`course-service`、`assessment-api`、`assessment-worker`、
`grade-service`、`frontend`、`rabbitmq`、`mysql`；声明 4 个有序迁移 job：
`identity -> course -> assessment -> grade`。自建镜像使用完整 SHA tag，数据库和
运行时凭据通过 Secret 注入；归档只保留键名和引用关系。

## 当前阻断

最终 CI 的 backend gate 失败点是
`CourseApiCoverageTest.notificationsListReadAndDeleteEndpointsReturnMutationResults`
收到 HTTP 404 而期望 200。根据 D3 共享契约，质量门禁失败时不得继续 build/deploy，
所以本归档可以证明阻断行为，但不能写 `success=1`。

## 证据导航

- [当前 CI 失败原始 artifacts](evidence/actions/current/ci-33698399654/)
- [当前 D3 阻断原始 artifacts](evidence/actions/current/d3-33698830921/)
- [历史成功 D3](evidence/actions/historical/success-d3-33227922081/)
- [历史 Kind 失败与诊断](evidence/actions/historical/failure-d3-33628385169/)
