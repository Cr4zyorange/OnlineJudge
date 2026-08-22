# Issue #224 浏览器验收记录

## 验收环境

- 基线：`origin/dev@efdd34a63dc42239d05419e10157b5cf2f4c5f33`
- 分支：`feature/224-hwk-draft-delete`
- 前端：本地 Vite；后端：本地 Spring Boot、H2 内存库 `issue224`、fake sandbox
- 浏览器：Playwright Chromium
- 登录账号：`teacher001`

## 真实链路

1. 在 `/courses/9501/homeworks/new` 创建 TEXT 草稿 `Issue 224 逻辑删除验收草稿`（作业 ID `950312`）。
2. 在 `/courses/9501/homeworks/manage` 验证仅 DRAFT 行显示“删除草稿”，非草稿行不显示。
3. 首次点击后取消确认框：列表保留草稿，网络侧没有发送 DELETE 请求。
4. 再次点击并确认：`DELETE /api/v1/homeworks/950312` 返回 200；响应包含 `status: DRAFT`、`deleted: true` 和删除时刻 `updatedAt: 2026-08-22T12:25:04.438224`。
5. 页面自动刷新，总数由 3 变为 2，草稿从列表隐藏并显示成功反馈。

## 视口与截图

| 截图 | 视口 | 验证点 |
| --- | --- | --- |
| `01-delete-entry-1440.png` | 1440 × 900 | 桌面端 DRAFT 删除入口、非草稿隔离 |
| `02-delete-entry-390.png` | 390 × 844 | 移动端操作按钮全宽可触达 |
| `03-delete-success-1440.png` | 1440 × 900 | 删除成功、列表刷新与反馈 |
| `04-delete-success-390.png` | 390 × 844 | 移动端删除后列表与反馈 |

移动端实测 `innerWidth = 390`、`documentWidth = 390`、`bodyWidth = 390`，无横向溢出。浏览器控制台错误 0、警告 0。

## 自动化验证

- 后端：290 tests，0 failures，0 errors，1 skipped（Docker/Testcontainers 环境型跳过）。
- 前端：53 个测试文件、511 tests 全部通过。
- 前端类型检查与生产构建通过。
- 后端集成测试额外验证：404/403/409 契约、仅父表逻辑删除、六类子记录主键与关键内容保持、重复删除、列表/详情隐藏，以及编辑/发布并发下不可复活。

## 边界

本次未新增数据库迁移：`t_hwk_homework.is_deleted` 已存在。浏览器验收使用 H2 和 fake sandbox；生产数据库的条件 UPDATE/FOR UPDATE 并发语义由 Repository/Service 自动化测试和 SQL 契约覆盖。
