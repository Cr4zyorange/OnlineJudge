# Issue #338 v2 契约本地证据

基线：`origin/dev@2d7103055fe4bd64d15afbac70d74b4213759f41`。环境：macOS 26.6.2（arm64）、Node `v25.8.2`、Bash `3.2`、Microsoft OpenJDK `21.0.9`；CI 固定 Node 22、Java 21。该 Issue 是契约文档和校验器交付，未实现 RabbitMQ、Outbox/Inbox 或业务服务。

| 阶段 | 命令 | 通过/失败 | 原始日志 |
| --- | --- | --- | --- |
| RED | `node scripts/ci/verify-microservice-contract-v2.mjs`（仅校验器，v2 制品尚未创建） | 预期失败：10 problems，exit 1 | `output/issue-338/red-contract-v2.log` |
| GREEN | `node scripts/ci/verify-microservice-contract-v2.mjs` | 5 OpenAPI、8 AsyncAPI、1 valid、2 incompatible rejected，exit 0 | `output/issue-338/green-contract-v2.log` |
| RED（独立审核返工） | `node scripts/ci/verify-microservice-contract-v2.mjs`（先收紧校验器，尚未补充 typed event/401/403 制品） | 预期失败：85 problems，exit 1 | `output/issue-338/red-independent-review-v2.log` |
| GREEN（独立审核返工） | `node scripts/ci/verify-microservice-contract-v2.mjs` | 5 OpenAPI、8 typed AsyncAPI、1 valid、4 incompatible rejected、3 review mutations rejected，exit 0 | `output/issue-338/green-independent-review-v2.log` |
| RED（P1 v2 作业发布返工） | `node scripts/ci/verify-microservice-contract-v2.mjs`（先要求 Learning 自足字段/反例/mutation，尚未修改 AsyncAPI 和设计制品） | 预期失败：6 problems，exit 1；缺 `title`/`deadline`/`receiverScope` 的必填/边界约束，新增合法作业样例被拒绝，新增缺任务事实反例被错误接受 | 本次 PR 终端原始输出 |
| GREEN（P1 v2 作业发布返工） | `node scripts/ci/verify-microservice-contract-v2.mjs` | 5 OpenAPI、8 typed AsyncAPI、2 valid、5 incompatible rejected、6 mutations rejected，exit 0 | 本次 PR 终端原始输出 |
| RED（第三轮 SRS 回滚语义返工） | 在 `76528efedafbdd356c8570ff044e3ffc75fe0645` 对最终 SRS 执行无条件 v1 规则检测；随后把相同检查纳入 verifier、尚未修改文档 | 预期失败，exit 1；检测到 UC-LRN-01 的“必达通知同步加入来源事务，失败必须回滚关键业务状态”，正式 verifier 报 3 个文档闭环问题 | 本次 PR 终端原始输出 |
| GREEN（第三轮 SRS 回滚语义返工） | `node scripts/ci/verify-microservice-contract-v2.mjs` | 5 OpenAPI、8 typed AsyncAPI、2 valid、5 incompatible rejected、7 rejecting mutations rejected（新增 mutation 为重引无条件 v1 规则） | 本次 PR 终端原始输出 |
| GREEN（第三轮完整复验） | `PUPPETEER_EXECUTABLE_PATH=... bash scripts/test/verify-hwk-doc-test-closure.test.sh`；`JAVA_HOME=...21... bash scripts/ci/contract-verify.sh "$PWD" consumer|producer`；`bash scripts/ci/check-workflows.sh`；`bash scripts/ci/verify-workflow-gates.test.sh`；`git diff --check` | Mermaid 实渲染与反向边 mutation PASS；consumer 25/25、producer 27/27；workflow 50 checks + gates PASS；diff clean | 本次 PR 终端原始输出 |
| GREEN | `bash -n scripts/ci/contract-verify.sh`、`bash scripts/ci/check-workflows.sh "$PWD"`、`bash scripts/ci/verify-gate-chain.sh --checkout "$PWD" --dry-run` | 1 syntax check + 50 workflow checks + 5-job dry-run，均 exit 0 | `output/issue-338/green-workflow-contract.log` |
| GREEN | `JAVA_HOME="$(/usr/libexec/java_home -v 21)" PATH="$JAVA_HOME/bin:$PATH" bash scripts/ci/contract-verify.sh "$PWD" all` | v2 Node gate + 47 shell checks + README replay + consumer 25/25 + producer 27/27，均 exit 0 | `ci-artifacts/contracts-gate/gate.log` |

独立审核返工的 AsyncAPI 门禁额外断言每个事件的 eventType、aggregateType、aggregateId 模板、closed payload 和 `x-onlinejudge-ordering`，并用删除 ordering、清空 payload、篡改 aggregate 的三种 mutation 证明拒绝行为。第二轮 P1 返工还要求 `assessment.homework.published.v2` 含有界 `title`、RFC3339 `deadline` 与 `receiverScope=COURSE_ACTIVE_STUDENTS`（无 roster），并验证删除这三项事实的 mutation 和缺字段反例都会拒绝。内部 API 门禁要求每个 service-identity operation 都具有 `401 SERVICE_IDENTITY_INVALID` 与 `403 SERVICE_IDENTITY_FORBIDDEN` 的 Error response。

#338 冻结的是 v2 契约和权威文档迁移，不实现 RabbitMQ、Outbox/Inbox 或业务服务。既有 `publishRequired`/`HWK_5003` 回滚自动化属于 v1 历史证据；v2 运行时必须由 #337/服务拆分 Issue 证明“Homework + outbox 本地成功即 PUBLISHED，Learning/broker 不可用不回滚，只有本地事务失败返回 `503/HWK_5003`/DRAFT”。因此本文件将契约门禁 GREEN 与运行时产品验收明确分开。

第三轮复验基线为 `origin/dev@b79affb4af80572e0196106266e265a545ae43ab`，返工提交生成后会在其 exact head 上等待 PR Actions。共享 `sharedTriggerPaths` 的 #336 修复属于 PR #345；本 PR 不修改该 manifest，须在 #345 合入后 rebase/sync 最新 `dev` 并再次执行交叉验证。

完整 `contracts-gate` 使用本机 OpenJDK 21.0.9 完成；它的完整原始 stdout/stderr 由脚本写入 `ci-artifacts/contracts-gate/gate.log`。PR Actions 会以固定 Node 22、Java 21 复跑，并上传同路径的 `ci-contracts-gate-*` artifact。
