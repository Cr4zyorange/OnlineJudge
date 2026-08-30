# Issue #338 v2 契约本地证据

初始基线：`origin/dev@2d7103055fe4bd64d15afbac70d74b4213759f41`；第三轮合并复验基线：`origin/dev@2a0ce94262596820eefe905bcd3c301c474880cf`（PR #345 合入后的 5/5 Actions）。环境：macOS 26.6.2（arm64）、Node `v22.23.2`、npm `10.9.2`、Bash `3.2`、Microsoft OpenJDK `21.0.9`；CI 固定 Node 22、Java 21。该 Issue 是契约文档和校验器交付，未实现 RabbitMQ、Outbox/Inbox 或业务服务。

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
| RED（GRD/LRN 异步读取隔离） | Actions `33297934152` attempt 1，head `73301d2b3054dd7dea733bb638c67844ba6d9b06` | `GrdLrnIntegrationTest` 第 101 行失败：响应 `records=[]` 而 `total=1`/`unreadCount=1`，首次成绩发布后立即读取通知 | [run 33297934152](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33297934152) 原始日志 |
| GREEN（GRD/LRN 异步读取隔离） | `JAVA_HOME=...21... mvn -Dtest=GrdLrnIntegrationTest test`；`JAVA_HOME=...21... bash scripts/ci/backend-verify.sh "$PWD"` | focused 1/1；完整单元 426（0 failure/error，7 skipped）+ integration 22/22。首段改用已有 5s/50ms bounded polling，后续字段断言保持不变 | 本次 PR 终端原始输出 |
| GREEN（#345 合并后完整复验） | Node 22/npm 10.9.2 `bash scripts/ci/frontend-verify.sh "$PWD"`；Mermaid 实渲染；consumer/producer contracts；workflow/manifest；`git diff --check origin/dev...HEAD` | frontend 566/566、build 与 runner contracts PASS；Mermaid 六图语义匹配与反向边 mutation PASS；consumer 25/25、producer 27/27；workflow 50 checks + 12 gate mutations；manifest 10 workloads/19 tests；diff clean | 本次 PR 终端原始输出 |
| GREEN | `bash -n scripts/ci/contract-verify.sh`、`bash scripts/ci/check-workflows.sh "$PWD"`、`bash scripts/ci/verify-gate-chain.sh --checkout "$PWD" --dry-run` | 1 syntax check + 50 workflow checks + 5-job dry-run，均 exit 0 | `output/issue-338/green-workflow-contract.log` |
| GREEN | `JAVA_HOME="$(/usr/libexec/java_home -v 21)" PATH="$JAVA_HOME/bin:$PATH" bash scripts/ci/contract-verify.sh "$PWD" all` | v2 Node gate + 47 shell checks + README replay + consumer 25/25 + producer 27/27，均 exit 0 | `ci-artifacts/contracts-gate/gate.log` |

独立审核返工的 AsyncAPI 门禁额外断言每个事件的 eventType、aggregateType、aggregateId 模板、closed payload 和 `x-onlinejudge-ordering`，并用删除 ordering、清空 payload、篡改 aggregate 的三种 mutation 证明拒绝行为。第二轮 P1 返工还要求 `assessment.homework.published.v2` 含有界 `title`、RFC3339 `deadline` 与 `receiverScope=COURSE_ACTIVE_STUDENTS`（无 roster），并验证删除这三项事实的 mutation 和缺字段反例都会拒绝。内部 API 门禁要求每个 service-identity operation 都具有 `401 SERVICE_IDENTITY_INVALID` 与 `403 SERVICE_IDENTITY_FORBIDDEN` 的 Error response。

#338 冻结的是 v2 契约和权威文档迁移，不实现 RabbitMQ、Outbox/Inbox 或业务服务。既有 `publishRequired`/`HWK_5003` 回滚自动化属于 v1 历史证据；v2 运行时必须由 #337/服务拆分 Issue 证明“Homework + outbox 本地成功即 PUBLISHED，Learning/broker 不可用不回滚，只有本地事务失败返回 `503/HWK_5003`/DRAFT”。因此本文件将契约门禁 GREEN 与运行时产品验收明确分开。

Actions `33297934152` attempt 1 的后端失败不能由 rerun 掩盖：`GradeRecordService` 在来源事务提交后的 executor 中投递，`NotificationService.listNotifications` 先查询 `records` 再查询 `total`/`unreadCount`，投递若落在两次查询之间便会得到该原始响应。该测试后三段原已有同一 bounded polling，首段遗漏；本 PR 补齐首段，不改变产品 API 或异步事务语义。attempt 2 由本次返工执行者手工触发，仅用于诊断，不作为 GREEN 证据。

共享 `sharedTriggerPaths` 和前端 teardown 的 #336 修复属于 PR #345；本 PR 未重复修改其 manifest/前端，而是在其合入后的 `origin/dev@2a0ce94262596820eefe905bcd3c301c474880cf` 完成上述交叉复验。随后必须以本 PR 新 head 触发全新的 exact-head Actions；旧 run 的 rerun 不可代替。

完整 `contracts-gate` 使用本机 OpenJDK 21.0.9 完成；它的完整原始 stdout/stderr 由脚本写入 `ci-artifacts/contracts-gate/gate.log`。PR Actions 会以固定 Node 22、Java 21 复跑，并上传同路径的 `ci-contracts-gate-*` artifact。
