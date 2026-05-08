---
name: onlinejudge-project-issue-planning
description: Use when planning or creating GitHub issues for OnlineJudgeForSE project phases, especially demand, design, detailed design, implementation, testing, or review task batches in the Team planning GitHub Project.
---

# OnlineJudgeForSE Project Issue Planning

## When To Use

Use this skill when the user asks to plan, create, or batch-create GitHub issues for `OnlineJudgeForSE`, especially when the work should be linked into the `Team planning` GitHub Project.

## Project Pattern

The team organizes each major phase as:

1. Six module tasks.
2. One specification task.
3. One integration task.
4. One review task.

For design-like phases, this produces 9 project items total:

- 用户权限与平台安全
- 课程与教学资源
- 学习过程与通知提醒
- 实训实验模块
- 作业与自动评测模块
- 成绩评价与教学分析
- 阶段规范
- 阶段整合
- 阶段审查

## Ownership Map

Use the existing `Team planning` assignment pattern unless the user explicitly overrides it.

| Area | Assignee |
|---|---|
| 用户权限与平台安全 | `wyx-1236` |
| 课程与教学资源 | `MontesquieuE` |
| 学习过程与通知提醒 | `luoZiHui-maker` |
| 实训实验模块 | `linkverb0510` |
| 作业与自动评测模块 | `terrana37` |
| 成绩评价与教学分析 | `Lucio-ball` |
| 阶段规范 | `linkverb0510` |
| 阶段整合 | `linkverb0510` |
| 阶段审查 | `Lucio-ball` |

## Issue Title Rules

For module tasks:

```text
<模块名> - <阶段名>
```

For coordination tasks:

```text
<阶段名>规范
<阶段名>整合
<阶段名>审查
```

Example for detailed design:

- 用户权限与平台安全 - 详细设计
- 课程与教学资源 - 详细设计
- 学习过程与通知提醒 - 详细设计
- 实训实验模块 - 详细设计
- 作业与自动评测模块 - 详细设计
- 成绩评价与教学分析 - 详细设计
- 详细设计规范
- 详细设计整合
- 详细设计审查

## GitHub Project Rules

Use GitHub CLI first.

| Item | Value |
|---|---|
| Repository | `Lucio-ball/OnlineJudgeForSE` |
| Project owner | `@me` or `Lucio-ball` |
| Project number | `3` |
| Project title | `Team planning` |

Create issues with:

```bash
gh issue create -R Lucio-ball/OnlineJudgeForSE \
  --title "<title>" \
  --body "" \
  --assignee "<assignee>" \
  --project "Team planning"
```

For newly created documentation/design-phase tasks:

- `Status`: `Todo`
- `Type`: `设计`

Current Project field IDs:

| Field | ID |
|---|---|
| Project ID | `PVT_kwHOBGs3_c4BVG7h` |
| Status field | `PVTSSF_lAHOBGs3_c4BVG7hzhQk9Jk` |
| Status `Todo` option | `f75ad846` |
| Type field | `PVTSSF_lAHOBGs3_c4BVG7hzhQ5Idc` |
| Type `设计` option | `a75641d9` |

Set `Type=设计` with:

```bash
gh project item-edit \
  --project-id PVT_kwHOBGs3_c4BVG7h \
  --id "<project-item-id>" \
  --field-id PVTSSF_lAHOBGs3_c4BVG7hzhQ5Idc \
  --single-select-option-id a75641d9
```

## Verification

After creating a batch, verify against the Project, not only issue creation output:

```bash
gh project item-list 3 --owner "@me" --format json --limit 100
```

Check that every intended issue exists, is assigned, is linked to `Team planning`, and has the expected Project fields.

If `gh issue list --search` misses Chinese titles, inspect the full issue list or the Project item list instead.
