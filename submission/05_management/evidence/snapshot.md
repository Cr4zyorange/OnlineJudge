# 采集快照｜2026-09-03

## 身份、基线与工具

| 项 | 值 |
| --- | --- |
| 仓库 | Cr4zyorange/OnlineJudge，PUBLIC |
| 默认和目标分支 | dev |
| 基线 SHA | c56b16f916b4a4c3d33915aa37beab6b05c72888 |
| 工作分支 | feature/369-management-evidence |
| GitHub CLI | gh version 2.92.0，2026-04-28 |
| GitHub 会话 | Owner Cr4zyorange 已认证；认证 token 从未写入本目录 |
| Notion 前置 | 已读取天枢 OS AI 主操作手册及 00、01、02 子手册；检索遵守 Search 到 Read 顺序。 |

## GitHub Project #3 固化结果

采集命令：

    gh project list --owner Cr4zyorange --limit 100 --format json
    gh project item-list 3 --owner Cr4zyorange --limit 500 --format json
    gh api graphql -f query='<Issue #369 project field query>'

结果：

- Project 为 Team planning，地址 https://github.com/users/Cr4zyorange/projects/3，公开（`public=true`），item 总数 190，字段数 21；无认证 HTTP 探针返回 200。
- 当前状态汇总为 Done=176、In progress=3、Todo=6、待审核=5。
- #369 Project item 为 PVTI_lAHOBGs3_c4BVG7hzg5ASHg。
- #369 字段为 Status=待审核、Priority=P0、Size=M、Type=设计、start date=2026-09-03、target date=2026-09-03；关联 PR #384。
- #369 Assignee 为 Cr4zyorange；Issue timeline 在 2026-09-03T01:15:23Z 记录 Project 状态切换。

## 可复现 GitHub 采集命令

    gh issue view 369 --repo Cr4zyorange/OnlineJudge --json number,title,body,state,assignees,labels,projectItems,url
    gh api 'repos/Cr4zyorange/OnlineJudge/issues/369/timeline?per_page=100' --paginate
    gh pr view 360 --repo Cr4zyorange/OnlineJudge --json number,author,commits,reviewDecision,mergedAt,mergeCommit
    gh pr view 372 --repo Cr4zyorange/OnlineJudge --json number,author,commits,reviewDecision,mergedAt,mergeCommit
    gh pr view 373 --repo Cr4zyorange/OnlineJudge --json number,author,commits,reviewDecision,mergedAt,mergeCommit
    gh pr view 374 --repo Cr4zyorange/OnlineJudge --json number,author,commits,reviewDecision,mergedAt,mergeCommit
    gh pr view 377 --repo Cr4zyorange/OnlineJudge --json number,author,commits,reviewDecision,mergedAt,mergeCommit
    gh api 'repos/Cr4zyorange/OnlineJudge/pulls/360/reviews?per_page=100' --paginate
    gh api 'repos/Cr4zyorange/OnlineJudge/pulls/372/reviews?per_page=100' --paginate
    gh api 'repos/Cr4zyorange/OnlineJudge/pulls/374/reviews?per_page=100' --paginate
    gh api 'repos/Cr4zyorange/OnlineJudge/pulls/377/reviews?per_page=100' --paginate

## Notion 原始来源

| 页面 | 永久地址 | 已读取用途 |
| --- | --- | --- |
| AI 主操作手册 | https://app.notion.com/p/36566e71b0d981048067cd2b5a47e652 | Notion 前置与安全边界。 |
| 计划编排工作流 | https://app.notion.com/p/36566e71b0d981f49d5ffc0da6c00df7 | 每日计划素材读取顺序。 |
| 任务书执行基线 | https://app.notion.com/p/3c666e71b0d981a1996fed86722b7f19 | 正式日历、D10、管理证据要求。 |
| 站会与日报规范 | https://app.notion.com/p/3c666e71b0d981bba14ee712e8bdf5c6 | 09:00 站会、日报与证据口径。 |
| 项目页 | https://app.notion.com/p/3aa66e71b0d9810a9692ed6819bee1ca | 项目状态、风险、Next Action、日报关联。 |
| 2026-08-26—2026-09-03 小组日报 | 见 [看板原件来源清单](notion-boards/README.md) | 导出 14 张看板原件并绑定日报日期。 |

## 本地检查命令

    git diff --check
    rg -n -i 'gh[pousr]_[A-Za-z0-9_]+|Bearer[[:space:]]+|BEGIN (RSA|EC|OPENSSH) PRIVATE KEY|password[[:space:]]*=' submission/05_management
    shasum -a 256 README.md INDEX.md daily-materials.md contributions.md review-rework.md official-notice-and-access.md evidence/snapshot.md evidence/notion-boards/README.md evidence/notion-boards/*

原始结果路径是本目录各 Markdown 文件和 SHA256SUMS。不导出 GitHub 或 Notion 的 cookie、Token 或短时签名资源。
