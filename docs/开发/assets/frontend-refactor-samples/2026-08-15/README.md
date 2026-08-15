# 2026-08-15 前端视觉样板与路由基座验收

本记录对应 GitHub issue [#211](https://github.com/Cr4zyorange/OnlineJudge/issues/211) 与 Notion Action“落地页面视觉样板、路由壳层与共享设计基座”。它固定三类代表页面的重构后视觉证据、真实浏览器验收结果和前端构建预算；不是全部 LAB/HWK 页面已完成批量迁移的声明。

## 采集环境

| 项目 | 值 |
| --- | --- |
| 开发基线 | `e405290bc0e11a1279f53c01616f09af6ec20026` (`origin/dev`) |
| 分支 | `feature/211-frontend-visual-foundation` |
| 采集时间 | 2026-08-15，Asia/Shanghai |
| Node.js / npm | Node.js 25.8.2 / npm 11.11.1 |
| 后端 | Spring Boot + 干净 H2 内存库 + 固定演示数据 |
| 评测 | `fake` sandbox；只用于验证状态流转 |
| 前端 | Vite 开发服务 + 重新生成的生产构建 |
| 视口 | 桌面 `1440 x 900`；移动 `390 x 844` |

浏览器中使用了真实学生/教师会话和真实 API。为使教师提交队列具有可复核数据，学生在本次 H2 进程中提交了一份 LAB，fake sandbox 返回 `WRONG_ANSWER`。该写操作随内存库进程结束而丢失，不污染仓库数据。

## 三类可评审样板

| 构图样板 | 真实路由 | 1440 | 390 | 验收要点 |
| --- | --- | --- | --- | --- |
| LAB 学生任务列表 | `/courses/9501/labs` | [桌面](./01-lab-task-list-1440.png) | [移动](./02-lab-task-list-390.png) | 页头、四项摘要、搜索/状态筛选、任务卡和下一步操作同屏可见；含加载、空、失败恢复。 |
| HWK 学生详情/提交 | `/courses/9501/homeworks/950311/submit` | [桌面](./03-homework-submit-1440.png) | [移动](./04-homework-submit-390.png) | 状态、截止时间、当前提交、题目与作答分区；390px 下主提交操作固定可见。 |
| LAB 教师提交工作台 | `/courses/9501/labs/950211/manage/submissions` | [桌面](./05-lab-submission-workspace-1440.png) | [移动](./06-lab-submission-workspace-390.png) | 指标、筛选、卡片队列、详情与评分主从结构；390px 首屏可见队列，且提供直达筛选器。 |

### 相对 #209 基线的改善

- HWK 学生页从纵向长表单改为“内容 + 提交工作区”，移动端不再需要滚到页尾才能提交。
- LAB 教师页从创建表单和裁切表格改为专用提交队列/批阅工作台，移动端不再依赖横向表格。
- 任务列表把截止时间、提交版本、成绩可见性和下一步操作收敛到同一卡片，避免让学生逐页推断状态。

## 路由与权限验收

Vue Router 4 使用 HTML5 history 和懒加载 route records；`App.vue` 只承载 `RouterView`。会话只从 `/api/v1/auth/me` 获取，课程访问只使用 CRS `member/manageable`，新导航不再读取 query/localStorage 角色。

真实浏览器验收结果：

| 场景 | 结果 |
| --- | --- |
| 带 `?role=teacher&keyword=线性` 直达学生 LAB | `role` 被 `replace` 移除，其他允许查询参数保留 |
| LAB → HWK → 后退 → 前进 → 刷新 | URL、标题和 H1 均恢复到正确页面 |
| 学生直达教师 LAB 工作台 | 进入 `/403`，不渲染教师工作区 |
| 未知 URL | 进入确定性 `/404` |
| 无会话直达受保护路由 | 进入 `/session-expired` |
| 三个样板的移动页 | 无页面级横向溢出；浏览器 console 0 error |

路由单测另覆盖直达、重建路由、后退/前进、篡改 `role` 无效、非成员/非管理者 403、会话失效、旧成绩 URL 重定向和未知路由。

## 共享基座

- `AppShell` / `CourseShell` 统一平台与课程导航，包含跳到主内容链接、路由标题和 H1 焦点管理。
- `PageHeader`、`SummaryStrip`、`PageState`、`StatusBadge`、`FilterBar`、`DataTable` 只消费稳定展示模型，不直接依赖 LAB/HWK 原始枚举。
- `DataTable` 在 640px 以下切换为卡片展示；三类页面同时有中文加载、空、失败和恢复操作。
- 冻结令牌已落到全局 CSS：工作表面 `rgba(248,251,252,.92)`、卡片 12px、控件 8px、`blur(12px)` 与 4/8/12/16/24/32/48px 间距序列。

## 自动化与预算

| 验证 | 结果 |
| --- | --- |
| `npm run test:unit` | 38 个文件、201 项测试全部通过 |
| `npm run typecheck` | 通过 |
| `npm run build` | 144 个模块，通过 |
| `git diff --check` | 通过 |
| 主 JS | 118.84 KiB，gzip 43.55 KiB（预算 ≤ 250 KiB gzip） |
| 全局 CSS | 25.64 KiB，gzip 5.75 KiB（预算 ≤ 80 KiB gzip） |
| 最大路由 JS chunk | 50.98 KiB，gzip 14.35 KiB（预算 ≤ 200 KiB gzip） |
| 首张背景 | 1,263.68 KiB（单张预算 ≤ 1.5 MiB） |

## 残余边界

- fake sandbox 只能证明提交/评测状态流转，不是真实 Docker 编译、运行、资源限制或 60 秒门槛结论。
- `npm audit` 仍报告基线已有的 5 项依赖问题（4 high、1 critical）；本 issue 只引入 Vue Router，不在视觉基座 PR 中混入依赖大版本升级。
- 本 issue 到此只完成三类样板与共享基座；其他 LAB/HWK 页面在样板评审后再按独立 issue 迁移。
