# Issue #379 多轮自审记录

自审规则：每轮先从工作树、canonical source、Actions 元数据和归档索引中查找问题；
将同一轮发现统一记录后再修改；修改完成后重新执行全部检查。只有一轮检查没有新
问题，才把归档标为可合并。外部 issue 的失败不挪入本 PR，只记录为阻塞。

| 轮次 | 查找结果 | 统一处理 |
| --- | --- | --- |
| Round 1 | PR 候选 CI 成功，但 PR 事件不触发正式 `d3-delivery`；旧 final 证据仍是失败链 | 将候选标为 candidate-only，保留 D3 阻断，要求合入 `dev` 后重新取 final D3 |
| Round 2 | 候选 PR head `82dd…` 与 integrated delivery artifact 的 `gitSha=7402…` 不同；`EVIDENCE_READY final_sha` 误指向 PR head；远程 `dev` 已前进 | 分离 head/build SHA，修正 machine-readable 索引，并同步最新 `dev` source |
| Round 3 | 快照缺少 gateway Dockerfile 的 `scripts/gateway/**`、cached-runtime/验证输入；主机缺少 Node | 补齐 #379 交付链的验证输入；平台测试在开发容器中由 67 项全通过；移除无关的 #340 workflow |
| Round 4 | #319 合入后 D8 配置/证据状态已变化；最新 `dev` CI 的 Grade MySQL contract 失败 | 纳入 D8 配置并引用 #319 provenance；记录最新基线失败，不越界修改其他 issue 的业务验证脚本 |
| Round 5 | 发现旧 D3 路径、旧基线文字和 `SHA256SUMS` 中残留旧路径；测试产生的 `__pycache__` 也被错误纳入清单 | 统一修正归档路径与 `origin/dev` 文字，删除测试缓存，重建并验证完整性清单 |
| Round 6 | 未发现新的归档、路径、契约、语法、敏感值或完整性问题 | 清洁复核通过；保留外部 CI/D3 阻塞作为验收前置，不再修改本 PR |

## 当前阻塞

- 最新 `origin/dev` final SHA 为 `ce87dfabd54239b9d4138736cbb93b06e6c9b260`。
- CI run [33710740174](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33710740174)
  的 Grade MySQL contract 期望 5 个查询但实际找到 4 个，导致 Disposable delivery 跳过。
  该回归来自 #319 合入后的 dev 基线，不属于本 issue 的归档文字/快照修复。
- D3 run [33710760915](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33710760915)
  消费到已取消的 source run [33710071217](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33710071217)，
  因而不能作为 final D3 证据。
- 缺少的“最终候选 SHA 的成功 CI/D3 原始证据”属于 #379 本 issue 的验收任务；
  #319 的 HPA/observability 证据只能作为已合入的 cross-issue 输入，不能替代 #379
  的 final CI/D3 原始 artifacts。
