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
| Round 7 | 远端 `dev` 已从 `ce87…` 前进到 `3a26…`，并合入 #388/#386 及后续变更；归档快照和旧 current 证据已落后 | 同步分支到最新 `dev`，更新受影响的 Kubernetes/Kind、Grade/D3 校验脚本和 D3 契约快照，将 ce87 证据降为 historical，等待新 CI |
| Round 8 | #386 的新增 Kind 静态测试在缺少 `GIT_SHA` 时被 `openssl/node` 前置检查遮蔽；该测试未被当前 CI 调用且不属于 #379 | 不在本 PR 修改 #386；按快照边界不复制未被 CI 调用的新增测试，保留最新 Kind/D3 runtime 脚本并记录外部风险 |
| Round 9 | 同步后的 PR quality-gate run 33724384655 的 workflow contracts、backend、frontend、repo contracts、browser E2E 和 Disposable delivery 全部成功；候选 artifact 的内部构建 SHA 与 PR head/final dev SHA 仍需分开 | 将本轮 CI artifact 作为 candidate-only 在线证据记录；不冒充 final D3，不提前填写镜像 digest、9 workload/4 migration 部署和回滚结论 |
| Round 10 | 文档复核发现验收表把 PR candidate 描述成 origin/dev final CI、索引沿用上一候选的内部构建 SHA，且解阻条件编号跳号 | 统一改为 candidate-only，记录本轮内部 SHA `6ab2db…`，保留上一候选 `7402fc…` 的历史身份，并修正 D3 状态措辞和编号 |
| Round 11 | 复核 282 个清单文件、路径/旧 SHA、敏感值、Markdown 状态、workflow/manifest/HPA/Grade/D3 校验及 67 项平台单测，未发现新问题；`upstreams.env` 是 canonical 配置输入，不是 Secret 实值 | 保持当前候选与 final D3 的证据边界；归档可进入 PR review，唯一剩余前置是合入 `dev` 后取得正式 final D3 原始证据 |
| Round 12 | 提交前发现重建 `SHA256SUMS` 时使用已跟踪文件列表会漏掉被忽略但仍属于归档的原始 evidence，清单错误缩为 282 行 | 改用归档目录下除自身外的全部文件重建清单，恢复为 463 行并通过完整性校验 |
| Round 13 | 在 Round 12 修复后复核状态措辞、SHA 身份、历史记录边界、463 项完整性清单、空白和全部本地验证结果，未发现新问题 | 终审通过；PR 可进入人工 review，保留“合入 `dev` 后正式 D3”这一唯一外部前置 |
| Round 14 | 新提交 `2552a2a2…` 的 PR quality-gate run 33727688910 全部成功；下载的交付 artifact 内部 SHA `51585331…` 与 PR head/final dev SHA 不同 | 将最新 run/artifact metadata 替换为当前候选记录，保留 33724384655 和更早候选为历史记录，继续禁止候选证据冒充 final D3 |
| Round 15 | 文档复核发现最新候选 run 仍有部分位置引用上一轮 33724384655；本轮 artifact、PR head 和内部 SHA 已确认 | 统一切换“最新”引用到 33727688910 / `2552a2a2…` / `51585331…`，上一轮只保留在历史候选段落 |
| Round 16 | Round 15 修复后的只读终审未发现旧“最新”状态残留、SHA 身份错配、清单范围缩小或空白错误；463 项校验和全部通过 | 终审通过；本次归档文档可提交，正式 final D3 仍仅受合入 `dev` 这一外部前置约束 |

## 当前阻塞

- 当前 `origin/dev` final SHA 为 `3a26ed2fe9399305b5e44eeae581911e6d32710e`，已包含 #388 的 Grade MySQL
  静态契约修复；同步后的 PR quality-gate 已由 run 33727688910 全部通过，但正式 final D3 尚未产生。
- ce87 基线的 CI/D3 失败链已移动到 `historical/baseline-ce87/`，不能作为当前 final 结果。
- #386 新增 Kind 静态测试的前置校验顺序问题未在 #379 中修改；该测试未进入当前归档快照和 CI 调用链。
- 缺少的“最终候选 SHA 的成功 CI/D3 原始证据”属于 #379 本 issue 的验收任务；
  #319 的 HPA/observability 证据只能作为已合入的 cross-issue 输入，不能替代 #379
  的 final CI/D3 原始 artifacts。
