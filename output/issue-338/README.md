# Issue #338 v2 契约本地证据

基线：`origin/dev@2d7103055fe4bd64d15afbac70d74b4213759f41`。环境：macOS 26.6.2（arm64）、Node `v25.8.2`、Bash `3.2`、Microsoft OpenJDK `21.0.9`；CI 固定 Node 22、Java 21。该 Issue 是契约文档和校验器交付，未实现 RabbitMQ、Outbox/Inbox 或业务服务。

| 阶段 | 命令 | 通过/失败 | 原始日志 |
| --- | --- | --- | --- |
| RED | `node scripts/ci/verify-microservice-contract-v2.mjs`（仅校验器，v2 制品尚未创建） | 预期失败：10 problems，exit 1 | `output/issue-338/red-contract-v2.log` |
| GREEN | `node scripts/ci/verify-microservice-contract-v2.mjs` | 5 OpenAPI、8 AsyncAPI、1 valid、2 incompatible rejected，exit 0 | `output/issue-338/green-contract-v2.log` |
| RED（独立审核返工） | `node scripts/ci/verify-microservice-contract-v2.mjs`（先收紧校验器，尚未补充 typed event/401/403 制品） | 预期失败：85 problems，exit 1 | `output/issue-338/red-independent-review-v2.log` |
| GREEN（独立审核返工） | `node scripts/ci/verify-microservice-contract-v2.mjs` | 5 OpenAPI、8 typed AsyncAPI、1 valid、4 incompatible rejected、3 review mutations rejected，exit 0 | `output/issue-338/green-independent-review-v2.log` |
| GREEN | `bash -n scripts/ci/contract-verify.sh`、`bash scripts/ci/check-workflows.sh "$PWD"`、`bash scripts/ci/verify-gate-chain.sh --checkout "$PWD" --dry-run` | 1 syntax check + 50 workflow checks + 5-job dry-run，均 exit 0 | `output/issue-338/green-workflow-contract.log` |
| GREEN | `JAVA_HOME="$(/usr/libexec/java_home -v 21)" PATH="$JAVA_HOME/bin:$PATH" bash scripts/ci/contract-verify.sh "$PWD" all` | v2 Node gate + 47 shell checks + README replay + consumer 25/25 + producer 27/27，均 exit 0 | `ci-artifacts/contracts-gate/gate.log` |

独立审核返工的 AsyncAPI 门禁额外断言每个事件的 eventType、aggregateType、aggregateId 模板、closed payload 和 `x-onlinejudge-ordering`，并用删除 ordering、清空 payload、篡改 aggregate 的三种 mutation 证明拒绝行为。内部 API 门禁要求每个 service-identity operation 都具有 `401 SERVICE_IDENTITY_INVALID` 与 `403 SERVICE_IDENTITY_FORBIDDEN` 的 Error response。

完整 `contracts-gate` 使用本机 OpenJDK 21.0.9 完成；它的完整原始 stdout/stderr 由脚本写入 `ci-artifacts/contracts-gate/gate.log`。PR Actions 会以固定 Node 22、Java 21 复跑，并上传同路径的 `ci-contracts-gate-*` artifact。
