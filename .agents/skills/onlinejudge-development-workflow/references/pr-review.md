# PR 审批评审门禁

来源：`docs/提炼skills/onlinejudge-pr-approval-reviewer.md`，叠加 codex skill `review-checklist.md` 的门禁与评审动作边界。适用于用户要求检查、巡检、批准或打回 OnlineJudge 的 issue/PR 时。

## 评审动作边界

除非用户当前消息明确要求送审/评审/提交/批准/打回/合并，做完本地自检即止并报告就绪。以下动作需要明确指示：为更新评审分支而 `git push`；`gh pr create/edit/comment`；`gh pr review --approve|--request-changes`；`gh pr merge` 或删分支；GitHub Project 状态变更。无需指示即可做：查元数据、跑本地测试、起草 PR body、准备评审文本、报告将要提交的内容。

## 门禁顺序

```text
在线状态检查 -> issue/PR 关联与 Project 状态维护 -> 硬门禁
-> issue 完成度门禁 -> 文档符合性门禁 -> 一般正确性检查 -> 批准
```

硬门禁失败即打回，不进入纯风格评审；但打回不代表看到第一个问题就停——继续收集同一轮能确认的全部阻塞项，给作者完整修复清单，避免"修一个冒一个"。批准 ≠ 合并：批准后询问用户是否合并，确认后才合并、删分支、调整 issue 状态。

## 启动检查

```bash
git status --short --branch
git fetch origin
gh --version && gh auth status
git remote -v
gh repo view --json nameWithOwner,defaultBranchRef
gh pr list --base dev --state open --json number,title,headRefName,baseRefName,isDraft,author,reviewDecision,statusCheckRollup,body,closingIssuesReferences,url
```

仓库身份以在线 `gh repo view` 为准，不信文件夹名或记忆。没有面向 `dev` 的开放 PR 时直接报告无可评审项。

## issue/PR 关联修复

评审要让关联关系和 Project 状态比发现时更好（在无歧义且安全时）。纯文字引用不够——GitHub 必须通过 `closingIssuesReferences`、`closedByPullRequestsReferences` 或 Project 项的 Linked pull requests 暴露认可链接。

配对无歧义时，评审者必须自己修复关联（即使 body 已有类似 `Closes #id` 文本但 `closingIssuesReferences` 仍为空）。修复阶梯按序：规范化 PR body 的全仓库关闭指令 → 把 issue 和 PR 加进同一 `Team planning` → 安全时用 GitHub linked-branch 流程 → 每步后复查元数据。

```bash
gh pr view <pr-number> --json body --jq '.body // ""' > /tmp/pr-body.md
perl -0pi -e 's/^\xEF\xBB\xBF//; s/\r\n/\n/g; s/\s*\z/\n\nCloses <owner>/<repo>#<issue-id>\n/' /tmp/pr-body.md
gh pr edit <pr-number> --body-file /tmp/pr-body.md
gh pr view <pr-number> --json closingIssuesReferences,url
gh issue view <issue-id> --json closedByPullRequestsReferences,projectItems,url
gh issue develop --list <issue-id>
```

多候选 PR、匹配靠猜意图、或缺失的 Project 项无法安全解析时，停止并报告歧义，不猜、不改。自动修复全失败时，报告尝试过的命令与阻塞，不给作者留"请自行关联 issue"的泛泛任务。

Project 状态维护：开放就绪 PR 关联 issue 后不得停留在 `Todo`（先移 `In progress` 再评审）；打回后保持 `In progress` 不回退；批准后移 `Ready to merge`（选项存在时）；已合并/已关闭的只检查报告，用户不要求清理就不改写。Project/字段/选项 ID 从在线数据解析。

## 硬门禁

任一失败立即 `gh pr review <number> --request-changes --body-file /tmp/pr-review.md`：

| 门禁 | 要求 |
| --- | --- |
| 目标分支 | PR 指向 `dev` |
| 草稿状态 | 非草稿 |
| issue 关联 | GitHub 元数据认可关联（可安全自动修复时必须先修再判） |
| Project 状态 | 关联 issue 在 `Team planning` 且反映评审状态 |
| 分支命名 | `feature/<issue-id>-<name>`、`fix/<issue-id>-<name>`、`docs/<issue-id>-<name>`、`test/<name>`、`release/<version>`、`hotfix/<issue-id>-<name>` |
| 范围 | 一个 PR 一个 issue、一个可评审交付单元 |
| 完成度声明 | PR 说明如何完成关联 issue 的文档化范围 |
| 工作流文档 | 不与 `AGENTS.md`/`README.md` 分支、commit、issue、验证规则冲突 |
| CI/测试 | 必要检查通过，或 PR 给出可信的书面理由与替代验证（无 CI 时记录本地命令结果） |
| 秘密/本地文件 | 无 token、密码、本地 env、临时输出、生成垃圾、无关产物 |
| 公共契约 | 无静默的 API/DTO/数据库/枚举/权限/事件/跨模块契约漂移 |

`mergeStateStatus=DIRTY` 不是就绪：合并最新 `origin/dev`、本地解冲突、重跑验证后再判；仅在用户下达送审/更新指示后才 push。

## issue 完成度门禁

硬门禁过后，先读 issue 再评代码质量：

```bash
gh issue view <issue-id> --json number,title,body,state,labels,assignees,milestone,projectItems,comments,url
```

issue 是必交付范围：验收标准、模块、正文、任务清单、Project 字段、引用文档都要落实。按序：提取清单/验收/引用的页面 API 表测试 → 逐项映射到 PR 变更文件与验证输出 → 确认覆盖完整文档行为而非只有最易成功路径 → 有意出范围的项目必须有 PR body 或 issue 讨论说明并指向后续 issue。

完成度阻塞示例：issue 要完整纵向切片而 PR 只做了一层；文档化页面/API/表/用例缺失；只做成功路径缺失败/空/权限/会话过期/校验状态；代码有用但解决的是比 issue 更小或不同的问题。

## 文档符合性门禁

对照文档栈评审变更文件：`docs/开发/` 对应模块流程 → SRS → 概要设计 → 详细设计 → `docs/过程/` 对应源稿。最终提交文档为权威，过程稿用于发现缺失细节与追溯缺口。

逐模块核对：行为映射到 `FR-*`/`NFR-*`；UI 页面符合文档角色/状态/表单/列表/空态/失败/权限；REST 路径、请求响应字段、错误码、分页、鉴权符合设计；Service 遵循业务规则、状态流转、归属边界、异常行为；表/字段/约束/索引/枚举/种子数据符合详细设计；跨模块调用遵守 AUTH/CRS/LAB/HWK/GRD/LRN 依赖方向；测试证明文档行为而非框架管道。

符合性阻塞：实现了文档外行为且无说明；缺文档化的成功/失败/权限/空态路径；静默改公共契约；issue 要求纵向切片却接假数据；核心文档行为无测试或可重复验证却声称完成。

## 一般正确性检查

门禁全过后做常规工程评审：正确性/边界/事务/幂等/错误处理；安全（鉴权、数据归属、文件安全、沙箱与命令执行安全）；可维护性（命名、重复、模块边界、本地风格）；前端质量（加载/成功/失败/空/无权限/会话过期）；测试是否能在最可能的回归上失败。影响行为/安全/可维护性/测试可信度的通用问题可阻塞批准；纯外观问题不否决干净的文档一致实现。

```bash
git diff --name-only origin/dev...HEAD
git diff --check origin/dev...HEAD
rg -n "TODO|FIXME|console\.log|debugger|<<<<<<<|=======|>>>>>>>" backend frontend database
```

## 复审收敛：从单点修复扩展到同类边界

本仓库 PR 历史显示，单元测试、构建和第一轮针对性修复全部通过后，仍可能在下一轮发现相邻边界缺失。常见链条是：状态迁移遗漏终态或来源隔离 → 任务事实与业务投影不一致 → 跨服务 Outbox/来源成绩仍是旧值 → 历史版本或分页快照污染当前读取。文档、部署和前端 PR 也有相同结构：局部文件正确，但真实入口、目标运行时、引用链或交付记录仍不成立。

这不是要求每次做无限制的全面复查。触及下列任一高风险面时，在送审前增加一次**有界的反向扩展审查**；只检查本 PR 修改直接写入或消费的同一事实，不扩展为其他 issue 的功能开发：

- 状态机、异步任务、重试、重评、发布、Outbox、跨模块事件或来源成绩；
- 版本化记录、`is_final`/当前版本、分页快照、幂等键或并发更新；
- 公共 HTTP 路径、DTO、错误信封、请求头、前端入口/路由；
- 迁移、部署脚本、目标数据库/集群行为，或跨文档编号与追溯关系。

### 反向扩展审查步骤

1. **写入者—读取者清单。** 对本 PR 修改的每个核心事实，列出所有写入者、同事务投影、Outbox/事件、直接 API 读取者和下游消费者。若一个动作只更新其中一项，必须说明为何其他项不应变化；否则补齐事务性更新或改为统一派生读取。
2. **生命周期时间线。** 为每个状态/版本变更列出：允许的起点、所有终态、失败/重试、重复请求、旧版本晚到完成、当前版本替换、权限失败和发布前后。至少选择最可能造成旧值泄露的一条交错序列，写成可执行回归；例如“旧版本成功 → 新版本成为 current 但未完成 → 旧版本重评或晚到完成”。
3. **聚合与快照。** 若事实按比任务更粗的键聚合（如 `sourceType:sourceId:studentId`），确认每次写入和失效都由当前有效记录围栏；行版本不得冒充全局快照水位。分页/重建需要在两条记录交错更新时仍能证明稳定性。
4. **真实契约入口。** 对新增或修改的公开 API，沿真实客户端/前端入口验证 method、URL、请求头、请求字段、响应 envelope、状态码和类型；组件直挂测试不替代真实路由验证。对迁移和脚本，在设计声明的目标运行时执行或使用可信兼容性检查，不以 H2、mock 或本地 stub 代替 MySQL/容器/集群语义。
5. **交付证据新鲜度。** 在当前 head、当前 `origin/dev` 基线和当前 PR 描述上复查命令、数量、风险与环境。重基、修复或证据变更后，旧 SHA、旧测试数、旧风险说明和旧镜像/入口说明必须更新；`mergeStateStatus=DIRTY` 直接阻断批准。

### 发布、重评与公开反馈的闭环审查

当 PR 同时触及评测终态、成绩发布或来源成绩时，除上述步骤外必须建立一条可执行的四阶段时间线：`评测完成 → 发布成绩 → 重评 → 重评终态`。逐阶段核对 Homework 状态、当前提交投影、来源成绩、Outbox、GRD 读取结果和学生可见字段；特别确认发布后的重评能够以明确规则恢复或撤销来源分，不能落入“已撤销但不能再次发布”的死状态。

若设计要求评测/批阅可追溯，检查任务表是否只是调度事实：重评必须新建业务评测历史、保留旧记录，并为重评、批阅和成绩发布写审计日志。单一任务行上的 generation、replay_count 或最后操作者字段不能替代历史记录。

对公共 HWK 写接口增加一次真实前端 wrapper 对照：请求方法、路径、请求头（含 request id）、请求 body、ID 类型、成功 envelope、错误 envelope 和返回字段必须逐项匹配。再以学生身份覆盖发布前结果查询，确认未公开的最终分、评语和私有结果不会因“本人可读”而泄露。前端构建或组件测试不能替代该契约对照。

CI 运行时是独立门禁：本地运行时与 CI 的 Java/Node/数据库版本不同，或 CI 已失败但日志尚不可用时，报告为阻断；不得用本地绿灯推断 CI 已通过。PR 描述中的测试数量、当前行为和风险必须随每次修复刷新。

### 收到 Request changes 后

不要只按评论所在行修补。先将评论归为“状态/投影/事件/版本/契约/迁移/入口/文档/交付证据”之一，并执行该类的最小反向扩展审查。新测试必须同时证明：原评论的故障会失败、最邻近的同类写入者不会留下不一致、以及成功/失败/当前版本或真实入口中的一个相反分支。完成后才运行全量或 PR 级验证。

复审报告须写明已覆盖的时间线或入口；只写“全量测试通过”不足以证明上述跨层行为。若发现缺少设计要求的历史记录、发布门、审计表、稳定快照或公开契约，视为 issue 完成度/文档符合性阻断，不将其降级为后续风格优化。

## 评审动作决策表

| 结果 | 动作 |
| --- | --- |
| 无可评审 PR | 报告无面向 `dev` 的开放 PR |
| 硬门禁失败 | `gh pr review <number> --request-changes` |
| 完成度失败 | 同上 |
| 文档符合性失败 | 同上 |
| 严重正确性/安全/测试问题 | 同上 |
| 全部通过 | `gh pr review <number> --approve` |
| 授权/网络/工具阻塞 | 报告确切阻塞，不批准 |

用户确认合并后完成交接：复查可合并且已批准 → `gh pr merge <number> --squash --delete-branch` → 验证 PR 已合并、远端分支已删、与 issue 关联仍在 → 调整 issue（PR 因指向 `dev` 而未自动关闭时手动关闭；Project 移 `Done`）→ 报告合并提交、删分支结果、issue 与 Project 状态。

## 评审输出格式

打回时阻塞项先行：

```text
Requesting changes.

Blocking issues found in this pass:
1. [hard gate / issue completion / doc conformance / correctness] <具体阻塞与证据>
2. <具体阻塞与证据>

Required before approval:
- <修复动作>
- <验证动作>

Review coverage:
- checked <issue / docs / changed files / tests / CI / local commands>;
- could not fully check <area> because <确切阻塞>（如适用）
```

批准时简短且基于证据：

```text
Hard gates passed: base dev, issue linkage, branch naming, checks, scope.
Issue completion passed: every documented item in #<issue-id> is covered by changed files and verification.
Document conformance passed: mapped changed behavior to <doc sections / requirement IDs>.
Verification reviewed: <tests/checks>.
Approved.
```

提交打回前，对变更文件清单和 issue 清单再过一遍，确认同一模块没有漏掉第二个阻塞项。评审意见区分确认的阻塞与低置信风险，不堆砌猜测性风格建议。
