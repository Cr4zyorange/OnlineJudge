# Issue #379 DevOps 归档索引

## 冻结身份

| 字段 | 值 |
| --- | --- |
| issue | #379 |
| origin/dev final SHA（归档基线） | `3a26ed2fe9399305b5e44eeae581911e6d32710e` |
| 已归档的前一 dev 基线 | `c56b16f916b4a4c3d33915aa37beab6b05c72888`（仅作历史失败链） |
| PR 候选 head SHA | `82dd58d10eb49f1ceacec7965f7932c123891a1a` |
| PR 候选 Disposable 构建 SHA | `7402fc614933242f7982c2b68c44cb40dfa67045`（artifact 内部记录，不能冒充 head SHA） |
| 归档结论 | `BLOCKED`（等待新基线 CI/D3） |
| 最新 final CI run | 待 `3a26ed2f…` 推送后的新 run；#388 修复已进入该基线 |
| 最新 D3 尝试 | 待新 CI 成功后由 `d3-delivery` 触发；ce87 基线的旧 D3 已降为历史证据 |
| 合规 final D3 run | 待 final CI 成功后由 `d3-delivery` workflow 触发；PR run 不能替代 |

## 覆盖范围

- `source/` 冻结了 origin/dev final SHA 中实际存在的 workflow、Docker/Compose、Kubernetes/Kind、平台清单、数据库迁移/seed/账号矩阵和运行脚本；候选 PR 未修改这些 canonical source。
- `evidence/actions/historical/baseline-ce87/` 保留前一 final SHA 的 CI failure artifact 和 D3 outcome；`evidence/actions/historical/baseline-c56/` 保留更早 dev 基线的 CI 与 D3 原始 artifacts。新 final SHA 的成功 CI/D3 原始证据尚未归档。
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
所以本归档保留历史失败证据。前一 final SHA `ce87…` 的 CI 因 Grade MySQL contract
静态计数回归失败；该问题已由 #388 修复并合入当前 `origin/dev`。新基线尚未形成可
归档的成功 final 证据。PR 候选 SHA 的
质量门禁和内置 Disposable delivery 已通过，但 `d3-delivery` 只接受 `dev` push；
需要新基线 CI 成功后产生合规的 final D3 run，当前不能写最终 `success=1`。

## EVIDENCE_READY

`EVIDENCE_READY issue=#379 final_sha=3a26ed2fe9399305b5e44eeae581911e6d32710e configs=submission/03_devops/source images=not-built-final-sha runs=33698399654,33698830921,33707236357,33227922081,33628385169 artifacts=21 success=1 failure=2 blocked=2 evidence=submission/03_devops/evidence`

`success=1` 仅统计已归档的历史成功 D3；PR 候选成功不计入 final，`failure=2`
统计已归档的两条历史失败链，`blocked=2` 统计前一 final 基线 CI/D3 阻断和等待新
基线 final D3。

候选证据的身份必须分开记录：PR head 是 `82dd58d…`，而候选 `ci-delivery`
artifact 的 manifest/image tags 使用 `7402fc…`；候选镜像 digest 因此不能用于
证明任一 final SHA 的镜像发布。

## 证据导航

- [前一 final 基线 CI 失败原始 artifacts](evidence/actions/historical/baseline-ce87/ci-33710740174/)
- [前一 final 基线 D3 阻断原始 artifacts](evidence/actions/historical/baseline-ce87/d3-33710760915/)
- [更早 dev 基线 CI 失败原始 artifacts](evidence/actions/historical/baseline-c56/ci-33698399654/)
- [更早 dev 基线 D3 阻断原始 artifacts](evidence/actions/historical/baseline-c56/d3-33698830921/)
- [历史成功 D3](evidence/actions/historical/success-d3-33227922081/)
- [历史 Kind 失败与诊断](evidence/actions/historical/failure-d3-33628385169/)
