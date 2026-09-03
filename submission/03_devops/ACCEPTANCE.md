# Issue #379 验收矩阵

状态含义：`PASS` 表示有 final SHA 配置或原始证据支撑；`BLOCKED` 表示当前
final SHA 因真实失败不能满足，不以历史运行替代。

| 项目 | 状态 | 依据 |
| --- | --- | --- |
| 配置入口与边界 | PASS | [SOURCE-MAP.md](SOURCE-MAP.md)、D3/D7 契约快照 |
| 9 workloads 与 4 migration jobs | PASS | `source/deploy/platform/workloads.json`，本地 validator 输出待补入校验记录 |
| Docker/Compose、workflow、Kubernetes/Kind、迁移/seed/账号矩阵 | PASS | `source/` 快照和 SOURCE-MAP |
| workflow failure gate 与 final SHA 绑定 | PASS | final CI/D3 原始 artifacts；D3 在 quality gate failure 后 build/deploy skipped |
| final SHA CI quality gate | BLOCKED | run 33698399654；backend Course API coverage 404/expected 200 |
| final SHA D3 delivery | BLOCKED | run 33698830921；`quality_gate=failure`，`build_images=skipped`，`deploy_kind=skipped` |
| immutable image tags/digests + OCI revision | BLOCKED | final SHA 未进入 build；历史成功 digest 仅作旧 SHA 追溯 |
| 成功部署证据 | PASS (historical only) | run 33227922081；旧 SHA、旧 3-workload topology，不能升级为 final PASS |
| 真实失败与诊断 | PASS (historical only) | run 33628385169；backend CrashLoopBackOff、rollout timeout、diagnostics、bounded cleanup |
| rollback/recovery 证据 | BLOCKED | 当前 final SHA 没有成功部署可回滚；D8/#318 证据未随 final SHA 进入本 checkout |
| D8 HPA / observability | BLOCKED | final SHA 无 HPA/observability source/evidence 文件 |
| Helm source | N/A | 当前 D7 契约使用渲染的 Kubernetes/Kind manifests，仓库无 Helm chart |
| Secret 值未归档 | PASS | source 仅保留键名/引用；原始 artifacts 未发现 Secret 实值，字面量密码的非 canonical compose 被排除 |
| hash、YAML/JSON/shell、链接检查 | PASS with limitation | [CHECKS.md](CHECKS.md)；Compose `!reset` 扩展标签由 Compose 语法保留，通用 PyYAML 解析不适用 |

## 解阻条件

1. 修复 final SHA 对应的 `CourseApiCoverageTest` 404 回归。
2. 重新运行并取得同一新 SHA 的 `ci-quality-gate=success`。
3. 让该 SHA 的 `d3-delivery` 完成镜像构建、9 workload/4 migration Kind 验收，并归档 image inspect、部署、诊断和恢复/回滚证据。
4. 若 #321 仍要求 D8 输入，再同步 HPA/observability 的真实运行证据；不得复用旧 SHA 的 PASS。
