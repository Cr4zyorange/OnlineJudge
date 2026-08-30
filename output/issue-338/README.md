# Issue #338 v2 契约本地证据

基线：`origin/dev@2d7103055fe4bd64d15afbac70d74b4213759f41`。环境：macOS 26.6.2（arm64）、Node `v25.8.2`、Bash `3.2`、Microsoft OpenJDK `21.0.9`；CI 固定 Node 22、Java 21。该 Issue 是契约文档和校验器交付，未实现 RabbitMQ、Outbox/Inbox 或业务服务。

| 阶段 | 命令 | 通过/失败 | 原始日志 |
| --- | --- | --- | --- |
| RED | `node scripts/ci/verify-microservice-contract-v2.mjs`（仅校验器，v2 制品尚未创建） | 预期失败：10 problems，exit 1 | `output/issue-338/red-contract-v2.log` |
| GREEN | `node scripts/ci/verify-microservice-contract-v2.mjs` | 5 OpenAPI、8 AsyncAPI、1 valid、2 incompatible rejected，exit 0 | `output/issue-338/green-contract-v2.log` |
| GREEN | `bash -n scripts/ci/contract-verify.sh`、`bash scripts/ci/check-workflows.sh "$PWD"`、`bash scripts/ci/verify-gate-chain.sh --checkout "$PWD" --dry-run` | 1 syntax check + 50 workflow checks + 5-job dry-run，均 exit 0 | `output/issue-338/green-workflow-contract.log` |
| GREEN | `JAVA_HOME="$(/usr/libexec/java_home -v 21)" PATH="$JAVA_HOME/bin:$PATH" bash scripts/ci/contract-verify.sh "$PWD" all` | v2 Node gate + 47 shell checks + README replay + consumer 25/25 + producer 27/27，均 exit 0 | `ci-artifacts/contracts-gate/gate.log` |

完整 `contracts-gate` 使用本机 OpenJDK 21.0.9 完成；它的完整原始 stdout/stderr 由脚本写入 `ci-artifacts/contracts-gate/gate.log`。PR Actions 会以固定 Node 22、Java 21 复跑，并上传同路径的 `ci-contracts-gate-*` artifact。
