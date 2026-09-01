# GitHub 协作与项目规则

来源：`AGENTS.md`、codex skill 的 `project-collaboration.md` 与 `workflow.md`、`docs/提炼skills/onlinejudge-project-issue-planning.md`。适用于任何涉及 GitHub 状态、协作证据、共享文件或汇报的任务。主要仓库文档源：`AGENTS.md`、`docs/过程/项目管理/GitHub协作与评审规范.md`、`docs/过程/项目管理/每日站会与汇报工作规范.md`。

## 证据链

保持工程链可追溯：

```text
阶段计划/决策 -> GitHub Issue -> 合规分支 -> 红测试或可执行验收契约
-> 实现/文档变更 -> commit -> PR 到 dev -> 自动评审证据
-> 项目负责人终审 -> 合并 -> Issue/Project/证据回填
```

GitHub 存 Issue、分支、commit、PR、评审与测试证据；微信用于即时协调与阻塞；Notion 存已验证的计划、决策与日报。不用聊天记录替代仓库证据。

## 分支模型

| 分支 | 用途 | 来源 | 合并去向 | 环境 | 可直接提交 |
| --- | --- | --- | --- | --- | --- |
| `main` | 稳定主分支 | `release/*` 或 `hotfix/*` | 无 | PRO | 否 |
| `dev` | 开发集成分支 | `main` 或稳定 `dev` | `test/*`、`release/*` | DEV | 否 |
| `feature/<issue-id>-<name>` | 单 issue 功能 | 最新 `dev` | PR → `dev` | 无 | 是 |
| `fix/<issue-id>-<name>` | 开发期修复 | 最新 `dev` | PR → `dev` | 无 | 是 |
| `docs/<issue-id>-<name>` | 文档 | 最新 `dev` | PR → `dev` | 无 | 是 |
| `test/<name>` | 功能验收测试 | `dev` 或指定集合 | `release/*` 或回 `dev` | FAT | 限测试修复 |
| `release/<version>` | 预上线/UAT/冻结 | `test/*` 或稳定 `dev` | `main` 和 `dev` | UAT | 限发布修复 |
| `hotfix/<issue-id>-<name>` | 线上紧急修复 | 最新 `main` | `main` 和 `dev` | 无 | 是 |

环境对应：DEV=`dev` 与功能分支；FAT=`test/*`；UAT=`release/*`；PRO=`main`。功能分支从最新 `dev` 拉：`git switch dev && git pull --ff-only origin dev && git switch -c feature/<issue-id>-<short-name>`。禁止直接在 `dev`、`main`、`release/*` 写常规功能代码；`hotfix/*` 完成后必须同时回合 `main` 与 `dev`。不建平行 `develop` 分支。

## Commit 与 PR 规则

- Commit 用 `type(scope): message`；type 限 `feat/fix/docs/style/refactor/perf/test/chore`。
- 一次 commit 同一类别、不超过 3 个紧密相关问题；不混功能/修复/格式化/重构/测试/文档。
- 未推送的提交不合规时优先 `git commit --amend`；拆分用 `git reset --soft|--mixed`；禁用 `git reset --hard`（除非用户明确要求且确认不丢他人改动）。
- 提交前确认无临时代码、调试输出、无关文件、密钥、本地环境文件。
- PR 描述必含：Goal、Changes、Verification（确切命令与观察结果）、Risks and boundaries、AI usage（面向评审者）以及 `Closes #<issue-id>`。
- 一 issue 一 PR；不混入无关联 issue 的改动。合入 `dev` 前先 `git fetch origin && git merge origin/dev` 本地解冲突并重跑验证；`mergeStateStatus=DIRTY` 的 PR 不可批准。
- 每次成功 push 更新 PR 分支后立即做简要汇报，至少给出 PR 编号或链接、本次推送的 commit、本次 PR 完成内容和实际验证结果；只汇报已推送内容，不把本地未提交或未推送改动算入本次交付。

## Projects 状态流转

GitHub Project 的 `Status` 是交付状态唯一来源：`Todo -> In progress -> 待审核 -> Done`。开始工作时移 `In progress`；自测完成创建目标 `dev` 的非草稿 PR 并写 `closes #<issue_id>`，Projects 自动移 `待审核`；负责人通过 PR Review 提交正式 `Request changes` 才会退回 `In progress`（普通评论不算打回）；负责人合并后自动 `Done`。除自动化结果外，任何人不得手动把未合并/未验收 issue 标 `Done`。Project 变更同样只在用户当前任务授权时执行；无权限时准备变更内容并报告确切状态或阻塞。

## Issue 规划模式（Team planning）

阶段任务按固定模式组织：六模块任务 + 规范 + 整合 + 审查，设计类阶段共 9 个 Project 项（用户权限与平台安全 / 课程与教学资源 / 学习过程与通知提醒 / 实训实验模块 / 作业与自动评测模块 / 成绩评价与教学分析 / 阶段规范 / 阶段整合 / 阶段审查）。模块 issue 标题 `<模块名> - <阶段名>`，协调 issue 标题 `<阶段名>规范|整合|审查`。GitHub Project：仓库 `Lucio-ball/OnlineJudgeForSE`，Project 3 `Team planning`。模块归属沿用既有指派（wyx-1236/MontesquieuE/luoZiHui-maker/linkverb0510/terrana37/Lucio-ball），用户明确覆盖时除外。创建后用 `gh project item-list 3 --owner "@me" --format json --limit 100` 对 Project 核验，而非只看创建输出；中文标题搜索不到时查全量列表。Project/字段/选项 ID 一律从在线数据解析，不硬编码记忆值。

## 评审与合并权限

- 自动评审（Codex/Copilot）仅是辅助证据，不是合并授权。
- 所有 PR 由项目负责人终审；领域负责人定标准、聚合产出，但不替代终审。
- 只有项目负责人做最终合并决定。
- 负责人可直接修复小于 30 分钟且不改需求/API/数据库/权限/架构/主交互流程的小评审问题；更大的问题连同证据和验收标准退回责任人。
- 批准不等于合并。push、PR 编辑、评审提交、Project 变更、合并、删分支各自需要用户在当前任务中的明确指示。

## 共享工作区碰撞守卫

编辑前：

```powershell
git status --short --branch
git diff --name-only
git diff -- <shared-file>
```

- 保留无关的脏改动和他人的工作；绕开他人改动，必要时只说明风险。
- 按模块拆分的阶段 issue 中，只编辑本模块拥有的章节、行、测试与图表资产。
- 不做全文档格式化、全局标题重编号、共享术语改写或其他模块内容清理，除非 issue 明确授予整合权。
- 确需为契约改动某段共享文字时，patch 最小化并声明跨模块影响；否则另开整合 issue。

## 汇报真实性

| 状态 | 含义 |
| --- | --- |
| `PASS` / 完成 | 命令或验收步骤实际运行且断言通过 |
| `FAIL` / 返工 | 已执行检查或评审发现可复现缺陷 |
| `BLOCKED` | 指名的外部依赖阻止执行；含责任人、日期、下一步与复测条件 |
| 进行中 | 已开始但完成证据尚不存在 |
| 风险 | 有证据但存在已知环境或验收缺口 |

绝不虚构他人状态、命令输出、评审结果、批准或交付日期。仓库当前没有 `.github/workflows/` CI；不得声称"CI 通过"，只记录真实本地命令与结果。

## PR Body 模板

本地起草或在最终报告中给出；用户明确要求送审前不执行 `gh pr create/edit/review/merge`：

```markdown
## Goal
- <issue 目标与验收边界>

## Changes
- <行为 1>
- <行为 2>

## Verification
- `<实际运行的命令>` — `<观察到的 PASS/FAIL/BLOCKED 结果>`

## Risks and boundaries
- Traceability: <FR/UC/OP/UI/API/SVC/DB/TC/MAN 编号>
- <共享文档边界、设计偏差、跳过的检查或残余风险>

## AI usage
- <AI 协助内容与人工验证内容；不得把对话文本插入交付文档>

Closes #<issue-id>
```
