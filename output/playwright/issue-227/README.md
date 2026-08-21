# Issue #227 浏览器验收记录

- 验收时间：2026-08-21 11:35 CST
- 分支：`feature/227-auth-crs-grd-lrn`
- 基线：`origin/dev` / `7e5f864`
- 浏览器：Codex In-app Browser（Chromium 151）
- 本地环境：Vite `5173` + Spring Boot `8080` + H2 内存数据库 + fake sandbox
- 测试身份：仓库种子数据中的学生、教师、管理员账号

## 截图

| 文件 | 视口 | 身份 / 路由 | 验收重点 |
| --- | --- | --- | --- |
| `01-auth-login-1440.jpg` | 1440 × 900 | 公共 `/login` | 统一登录入口、桌面布局 |
| `02-auth-login-390.jpg` | 390 × 844 | 公共 `/login` | 登录表单移动端布局 |
| `03-student-learning-tasks-1440.jpg` | 1440 × 900 | 学生 `/learning/tasks` | 可信角色落点、统一外壳、中文任务状态、无 `role` 查询参数 |
| `04-student-grades-390.jpg` | 390 × 844 | 学生 `/courses/9501/grades` | 中文成绩状态、来源类型、隐藏来源及成绩项裸 ID |
| `05-teacher-grade-items-1440.jpg` | 1440 × 900 | 教师 `/courses/9501/grades/manage/items` | LAB/HWK 真实任务名称选择器、中文来源类型 |
| `06-teacher-grade-table-390.jpg` | 390 × 844 | 教师 `/courses/9501/grades/manage/table` | 成绩项名称筛选、移动端无页面级横向溢出 |
| `07-admin-auth-1440.jpg` | 1440 × 900 | 管理员 `/admin/auth` | 管理员专属导航入口、用户/角色/审计载体 |
| `08-admin-auth-390.jpg` | 390 × 844 | 管理员 `/admin/auth` | 权限管理移动端布局 |
| `09-forbidden-390.jpg` | 390 × 844 | 学生访问 `/admin/auth` | 角色越权进入友好 403 状态 |
| `10-not-found-1440.jpg` | 1440 × 900 | 未知路由 | 统一 404 状态与恢复入口 |

## 结果

- 上述 10 个视口均确认 `document.documentElement.scrollWidth <= window.innerWidth`。
- 浏览器控制台 `error` 日志为空。
- 学生、教师、管理员登录后分别进入角色对应工作台；学生无法进入管理员页面。
- 教师成绩项页面从真实 LAB/HWK API 加载任务名称，页面不再要求输入来源 ID。
- 学生成绩和教师成绩管理页面不再展示原始成绩枚举或来源 ID。
- LRN 页面由平台 AppShell 统一承载，不再嵌套重复返回栏和背景层。
- 未知路由进入 `/404`；权限不足进入 `/403`，均提供可恢复入口。

## 自动化验证

```text
npm --prefix frontend run test:unit  # 53 files / 495 tests passed
npm --prefix frontend run typecheck # passed
npm --prefix frontend run build     # passed
git diff --check                     # passed
```
