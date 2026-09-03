# Issue #379 验收矩阵

状态含义：`PASS` 表示有对应 SHA 的配置或原始证据支撑；`BLOCKED` 表示
origin/dev 最终交付链路尚未满足，不以 PR 候选或历史运行替代。

| 项目 | 状态 | 依据 |
| --- | --- | --- |
| 配置入口与边界 | PASS | [SOURCE-MAP.md](SOURCE-MAP.md)、D3/D7/D8 契约快照；source 快照基线为 `3a26ed2f…` |
| 9 workloads 与 4 migration jobs | PASS | `source/deploy/platform/workloads.json`，本地 validator 输出待补入校验记录 |
| Docker/Compose、workflow、Kubernetes/Kind、迁移/seed/账号矩阵 | PASS | `source/` 快照和 SOURCE-MAP |
| workflow failure gate 与 final SHA 绑定 | PASS | final CI/D3 原始 artifacts；D3 在 quality gate failure 后 build/deploy skipped |
| 前一 dev 基线 CI quality gate | PASS (historical failure archived) | run 33698399654；backend Course API coverage 404/expected 200，原始证据已移入 `historical/baseline-c56/` |
| 同步后的 PR quality gate | PASS (candidate only) | run [33727688910](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33727688910)；workflow contracts/backend/frontend/repo contracts/browser E2E/Disposable delivery 全部成功，但尚未由 `dev` push 触发 |
| PR 候选 CI quality gate | PASS (candidate only) | run 33727688910；六项质量门禁均成功，不能替代正式 `d3-delivery` |
| origin/dev final D3 delivery | BLOCKED | 正式 final CI/D3 尚未由合入后的 `dev` push 产生；ce87 旧 D3 仅作历史阻断证据 |
| immutable image tags/digests + OCI revision | BLOCKED | final SHA 未进入 build；历史成功 digest 仅作旧 SHA 追溯 |
| 成功部署证据 | PASS (historical only) | run 33227922081；旧 SHA、旧 3-workload topology，不能升级为 final PASS |
| 真实失败与诊断 | PASS (historical only) | run 33628385169；backend CrashLoopBackOff、rollout timeout、diagnostics、bounded cleanup |
| rollback/recovery 证据 | BLOCKED | latest final SHA 没有成功部署可回滚；需合入后追加合规 D3 证据 |
| D8 HPA / observability | PASS (cross-issue input) | #319 已合入 `origin/dev`（PR #374），配置为 `source/deploy/platform/observability-hpa-experiment.json`；正式 Round 8 原始证据仍由 `docs/过程/测试/Issue-319-HPA实验证据-20260902T161736Z/` 维护，provenance tested SHA 为 `cf2979dc…`，不冒充当前 final D3 |
| Helm source | N/A | 当前 D7 契约使用渲染的 Kubernetes/Kind manifests，仓库无 Helm chart |
| Secret 值未归档 | PASS | source 仅保留键名/引用；原始 artifacts 未发现 Secret 实值，字面量密码的非 canonical compose 被排除 |
| hash、YAML/JSON/shell、链接检查 | PASS with limitation | [CHECKS.md](CHECKS.md)；Compose `!reset` 扩展标签由 Compose 语法保留，通用 PyYAML 解析不适用 |

## 解阻条件

1. 将已通过 PR quality gate 的 #379 分支合入最新 `dev`，让合并后的 `dev` SHA 产生正式 `ci-quality-gate=success`。
2. 让该合并 SHA 的 `d3-delivery` 完成镜像构建、9 workload/4 migration Kind 验收，并归档 image inspect、部署、诊断和恢复/回滚证据。
3. 若 #321 仍要求 D8 输入，再同步 HPA/observability 的真实运行证据；不得复用旧 SHA 的 PASS。
