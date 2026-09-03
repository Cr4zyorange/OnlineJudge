# Issue #369｜05_management 证据索引

采集时间：2026-09-03 CST
结论：BLOCKED

本索引遵守 #369 的边界：只记录已经发生且可核验的事实。当前分支从 origin/dev 的 c56b16f916b4a4c3d33915aa37beab6b05c72888 创建；最终提交 head、云盘上传、成员确认和非 Owner 权限尚未发生或无法以本会话身份核验，不能标为完成。

## 平台与访问

| 材料 | 地址或本地路径 | 访问要求 | 采集结论 |
| --- | --- | --- | --- |
| GitHub 仓库 | https://github.com/Cr4zyorange/OnlineJudge | 私有仓库；需要受邀 GitHub 账号 | PASS：Owner 会话可读；匿名探针 HTTP 404，不能作为教师或助教访问证明。 |
| GitHub Project | https://github.com/users/Cr4zyorange/projects/3 | 私有 Project；需要受邀 GitHub 账号 | PASS：Owner 会话读取 Project #3 Team planning；匿名探针 HTTP 404。 |
| Issue #369 | https://github.com/Cr4zyorange/OnlineJudge/issues/369 | 同仓库访问权限 | PASS：Assignee 为 Cr4zyorange，Project 状态为 In progress。 |
| Notion 项目 | https://app.notion.com/p/3aa66e71b0d9810a9692ed6819bee1ca | 天枢OS Notion 工作区权限 | PASS：项目为进行中；本次读取前已先阅读适用 AI 手册。 |
| Notion 日报知识库 | https://app.notion.com/p/25166e71b0d980d59888f7fdbb071679 | 同上 | PASS：用于逐日来源索引；不将临时图像签名 URL 写入仓库。 |
| 云盘和最终提交地址 | 无可审计原件 | 最新课程群或教师通知与非 Owner 访问账号 | BLOCKED：未提供地址、发布者、发布时间或访问回执。 |

## 目录材料

| 文件 | 用途 | 原始来源 |
| --- | --- | --- |
| [daily-materials.md](daily-materials.md) | 应有开发日、站会和看板来源、日期级缺口 | 任务书 Notion 页面与 8 月 26 日至 9 月 3 日日报。 |
| [contributions.md](contributions.md) | 六人事实、建议权重、确认状态 | GitHub Issue、PR、commit 查询与日报公开引用。 |
| [review-rework.md](review-rework.md) | 返工链与终审证据 | GitHub Pull Request Review API。 |
| [official-notice-and-access.md](official-notice-and-access.md) | 时间、云盘冲突、外部访问门槛 | #321、任务书执行基线、Notion D10 Action、匿名访问探针。 |
| [evidence/snapshot.md](evidence/snapshot.md) | 当前 Project、环境、命令和计数 | 可复现查询命令及其精确结果。 |

## 完整性与隐私

SHA256SUMS 覆盖本目录的固定 Markdown 材料，不含自身。提交前按其中命令重算。敏感文本扫描覆盖常见 Token、Bearer/JWT、私钥和密码赋值；任何真实命中必须先停止并人工处置。

## 当前交付判断

EVIDENCE_READY 不能发布。当前已固化 Project、正式 Review/返工、可回查的贡献事实和逐日来源；仍缺 D1 原始日报、若干日期可证明的站会原始记录、最终官方通知与云盘、六人真实确认，以及指定非 Owner 账号的访问回执。
