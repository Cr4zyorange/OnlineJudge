# Issue #379 DevOps 归档索引

## 冻结身份

| 字段 | 值 |
| --- | --- |
| issue | #379 |
| origin/dev final SHA（归档基线） | `ce87dfabd54239b9d4138736cbb93b06e6c9b260` |
| 已归档的前一 dev 基线 | `c56b16f916b4a4c3d33915aa37beab6b05c72888`（仅作历史失败链） |
| PR 候选 head SHA | `82dd58d10eb49f1ceacec7965f7932c123891a1a` |
| PR 候选 Disposable 构建 SHA | `7402fc614933242f7982c2b68c44cb40dfa67045`（artifact 内部记录，不能冒充 head SHA） |
| 归档结论 | `BLOCKED`（等待候选合入后的 final CI/D3） |
| 最新 final CI run | [33710740174](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33710740174)，`failure`；backend 的 Grade MySQL contract 期望 5 个查询但实际为 4 个 |
| 最新 D3 尝试 | [33710760915](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33710760915)，因消费到已取消的质量门禁 run [33710071217](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33710071217) 而阻断，不能作为 final D3 成功/失败结论 |
| 合规 final D3 run | 待 final CI 成功后由 `d3-delivery` workflow 触发；PR run 不能替代 |

## 覆盖范围

- `source/` 冻结了 origin/dev final SHA 中实际存在的 workflow、Docker/Compose、Kubernetes/Kind、平台清单、数据库迁移/seed/账号矩阵和运行脚本；候选 PR 未修改这些 canonical source。
- `evidence/actions/current/` 保留最新 final SHA 的 backend failure artifact 和 D3 outcome；`evidence/actions/historical/baseline-c56/` 保留前一 dev 基线的 CI 与 D3 原始 artifacts。最新 final SHA 的成功 CI/D3 原始证据尚未归档。
- `evidence/actions/historical/` 保留可追溯的历史成功部署（旧三工作负载 D3）和真实 Kind 失败诊断；它们只用于证明成功/失败/诊断链路存在，不替代最终 SHA 结论。
- D8 HPA 配置已由 #319 合入；正式 HPA 原始实验仍引用 #319 的 canonical 过程证据，#379 不复制第二份日志。仓库仍没有 Helm source。

## 配置摘要

最终 `deploy/platform/workloads.json` 声明 9 个 workload：`gateway`、
`identity-service`、`course-service`、`assessment-api`、`assessment-worker`、
`grade-service`、`frontend`、`rabbitmq`、`mysql`；声明 4 个有序迁移 job：
`identity -> course -> assessment -> grade`。自建镜像使用完整 SHA tag，数据库和
运行时凭据通过 Secret 注入；归档只保留键名和引用关系。

## 当前阻断

前一 dev 基线 CI 的 backend gate 失败点是
`CourseApiCoverageTest.notificationsListReadAndDeleteEndpointsReturnMutationResults`
收到 HTTP 404 而期望 200。根据 D3 共享契约，质量门禁失败时不得继续 build/deploy，
所以本归档保留历史失败证据。最新 `origin/dev` 的 CI 因 Grade MySQL contract 回归
失败，未形成可归档的成功 final 证据；其一次 D3 尝试消费到被取消的源质量门禁 run，
已按契约阻断。PR 候选 SHA 的
质量门禁和内置 Disposable delivery 已通过，但 `d3-delivery` 只接受 `dev` push；
需要人工合入后产生合规的 final D3 run，当前不能写最终 `success=1`。

## EVIDENCE_READY

`EVIDENCE_READY issue=#379 final_sha=ce87dfabd54239b9d4138736cbb93b06e6c9b260 configs=submission/03_devops/source images=not-built-final-sha runs=33710740174,33710760915,33710071217,33710097381,33698399654,33698830921,33707236357,33227922081,33628385169 artifacts=24 success=1 failure=4 blocked=4 evidence=submission/03_devops/evidence`

`success=1` 仅统计已归档的历史成功 D3；PR 候选成功不计入 final，`failure=4`
统计已观测的失败 run，`blocked=4` 统计前一基线 D3、两次取消源 run 引起的 D3 阻断和
等待合入后的 final D3。

候选证据的身份必须分开记录：PR head 是 `82dd58d…`，而候选 `ci-delivery`
artifact 的 manifest/image tags 使用 `7402fc…`；候选镜像 digest 因此不能用于
证明任一 final SHA 的镜像发布。

## 证据导航

- [前一 dev 基线 CI 失败原始 artifacts](evidence/actions/historical/baseline-c56/ci-33698399654/)
- [前一 dev 基线 D3 阻断原始 artifacts](evidence/actions/historical/baseline-c56/d3-33698830921/)
- [历史成功 D3](evidence/actions/historical/success-d3-33227922081/)
- [历史 Kind 失败与诊断](evidence/actions/historical/failure-d3-33628385169/)
