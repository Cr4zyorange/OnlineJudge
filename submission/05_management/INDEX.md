# Issue #369｜05_management 证据索引

采集时间：2026-09-03 CST
结论：BLOCKED

本索引遵守 #369 的边界：只记录已经发生且可核验的事实。当前分支从 origin/dev 的 c56b16f916b4a4c3d33915aa37beab6b05c72888 创建；最新书面课程通知、成员确认和 Project 的非 Owner 权限尚未取得，不能标为完整完成。

## 平台与访问

| 材料 | 地址或本地路径 | 访问要求 | 采集结论 |
| --- | --- | --- | --- |
| GitHub 仓库 | https://github.com/Cr4zyorange/OnlineJudge | 公开仓库 | PASS：`visibility=PUBLIC`；匿名探针 HTTP 200。 |
| GitHub Project | https://github.com/users/Cr4zyorange/projects/3 | 公开 Project | PASS：`public=true`；匿名探针 HTTP 200。 |
| Issue #369 | https://github.com/Cr4zyorange/OnlineJudge/issues/369 | 同仓库访问权限 | PASS：Assignee 为 Cr4zyorange，Project 状态为待审核，关联 PR #384。 |
| Notion 项目 | https://app.notion.com/p/3aa66e71b0d9810a9692ed6819bee1ca | 天枢OS Notion 工作区权限 | PASS：项目为进行中；本次读取前已先阅读适用 AI 手册。 |
| Notion 日报知识库 | https://app.notion.com/p/25166e71b0d980d59888f7fdbb071679 | 同上 | PASS：用于逐日来源索引；不将临时图像签名 URL 写入仓库。 |
| 云盘方式 | 无可审计原件 | 最新课程群或教师通知与非 Owner 访问账号 | BLOCKED：#369 的 AC-369-06 明确要求保存最新官方通知的云盘方式；目前未提供地址、发布者或发布时间。实际上传回执不属于本 Issue 的验收项。 |

## 目录材料

| 文件 | 用途 | 原始来源 |
| --- | --- | --- |
| [daily-materials.md](daily-materials.md) | 日报、站会口径、看板来源与日期级结论 | 任务书 Notion 页面与 8 月 26 日至 9 月 3 日日报。 |
| [看板原件与来源清单](evidence/notion-boards/README.md) | 14 张导出的 Notion 看板原件、日期绑定和 SHA256 | 8 张 Notion 日报页的“看板”段落。 |
| [contributions.md](contributions.md) | 六人事实、建议权重、确认状态 | GitHub Issue、PR、commit 查询与日报公开引用。 |
| [review-rework.md](review-rework.md) | 返工链与终审证据 | GitHub Pull Request Review API。 |
| [official-notice-and-access.md](official-notice-and-access.md) | 时间、云盘方式冲突、外部访问门槛 | #321、任务书执行基线、Notion D10 Action、匿名访问探针。 |
| [evidence/snapshot.md](evidence/snapshot.md) | 当前 Project、环境、命令和计数 | 可复现查询命令及其精确结果。 |

## 完整性与隐私

SHA256SUMS 覆盖本目录的固定 Markdown 材料，不含自身。提交前按其中命令重算。敏感文本扫描覆盖常见 Token、Bearer/JWT、私钥和密码赋值；任何真实命中必须先停止并人工处置。

## 当前交付判断

EVIDENCE_READY 不能发布。当前已固化 Project、正式 Review/返工、可回查的贡献事实，以及 8 个日报日期的 14 张原始看板图；仍缺最新书面官方通知（含云盘方式）和六人真实确认。
