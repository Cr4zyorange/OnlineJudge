# Issue #379 DevOps 归档索引

## 冻结身份

| 字段 | 值 |
| --- | --- |
| issue | #379 |
| origin/dev 基线 SHA | `c56b16f916b4a4c3d33915aa37beab6b05c72888` |
| PR 候选 SHA | `82dd58d10eb49f1ceacec7965f7932c123891a1a` |
| 归档结论 | `BLOCKED`（等待合入后的 final D3） |
| origin/dev 失败门 | `ci-quality-gate` run [33698399654](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33698399654) |
| 候选 CI run | [33707236357](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33707236357)，所有质量门禁和内置 Disposable delivery 成功 |
| 合规 D3 run | 待候选合入 `dev` 后由 `d3-delivery` workflow 触发；PR run 不能替代 |

## 覆盖范围

- `source/` 冻结了 origin/dev 基线 SHA 中实际存在的 workflow、Docker/Compose、Kubernetes/Kind、平台清单、数据库迁移/seed/账号矩阵和运行脚本；候选 PR 未修改这些 canonical source。
- `evidence/actions/current/` 保留最终 SHA 的 CI 与 D3 原始 artifacts，包括失败测试报告、环境信息、契约报告和阻断结果。
- `evidence/actions/historical/` 保留可追溯的历史成功部署（旧三工作负载 D3）和真实 Kind 失败诊断；它们只用于证明成功/失败/诊断链路存在，不替代最终 SHA 结论。
- 当前 origin/dev 基线没有 D8 HPA 实验证据文件；仓库中也没有 Helm source。两项均在验收矩阵中明确标记，未凭空补造。

## 配置摘要

最终 `deploy/platform/workloads.json` 声明 9 个 workload：`gateway`、
`identity-service`、`course-service`、`assessment-api`、`assessment-worker`、
`grade-service`、`frontend`、`rabbitmq`、`mysql`；声明 4 个有序迁移 job：
`identity -> course -> assessment -> grade`。自建镜像使用完整 SHA tag，数据库和
运行时凭据通过 Secret 注入；归档只保留键名和引用关系。

## 当前阻断

origin/dev 基线 CI 的 backend gate 失败点是
`CourseApiCoverageTest.notificationsListReadAndDeleteEndpointsReturnMutationResults`
收到 HTTP 404 而期望 200。根据 D3 共享契约，质量门禁失败时不得继续 build/deploy，
所以本归档保留失败证据。PR 候选 SHA 的同一套 CI 已通过，但 `d3-delivery` 只接受
`dev` push；需要人工合入后产生合规的 final D3 run，当前不能写最终 `success=1`。

## EVIDENCE_READY

`EVIDENCE_READY issue=#379 final_sha=82dd58d10eb49f1ceacec7965f7932c123891a1a configs=submission/03_devops/source images=candidate-ci-delivery-only runs=33698399654,33698830921,33707236357,33227922081,33628385169 artifacts=21 success=2 failure=2 blocked=2 evidence=submission/03_devops/evidence`

## 证据导航

- [当前 CI 失败原始 artifacts](evidence/actions/current/ci-33698399654/)
- [当前 D3 阻断原始 artifacts](evidence/actions/current/d3-33698830921/)
- [历史成功 D3](evidence/actions/historical/success-d3-33227922081/)
- [历史 Kind 失败与诊断](evidence/actions/historical/failure-d3-33628385169/)
