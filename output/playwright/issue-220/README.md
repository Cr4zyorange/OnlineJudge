# Issue #220 LAB 教师端流程验收证据

## 验收环境

- 基线：`origin/dev@5e686bb474b0fd303b0815127f4a91a961512939`
- 分支：`feature/220-lab-teacher-flow`
- 浏览器：Playwright Chrome，1440 x 900 与 390 x 844
- 服务：本地 Spring Boot + 内存 H2 + fake sandbox；页面调用真实 API
- 账号：仓库公开演示教师 `teacher001`、学生 `student001`
- 课程：`9501`；验收实验、提交、报告和评分只存在于本次内存数据库会话

## 前后对照

改造前，教师创建、列表、统计和批阅纵向堆叠在同一页面，移动端操作列存在裁切：

- [改造前 1440](../../../docs/开发/assets/frontend-refactor-baseline/2026-08-14/05-lab-teacher-1440.png)
- [改造前 390](../../../docs/开发/assets/frontend-refactor-baseline/2026-08-14/06-lab-teacher-390.png)

改造后按职责拆为管理总览、结构化编辑、实验详情、提交队列、独立批阅和统计六个页面：

| 页面 | 1440 x 900 | 390 x 844 |
| --- | --- | --- |
| 管理总览 | [01-manage-index-1440.png](01-manage-index-1440.png) | [02-manage-index-390.png](02-manage-index-390.png) |
| 结构化编辑 | [03-editor-1440.png](03-editor-1440.png) | [04-editor-390.png](04-editor-390.png) |
| 实验管理详情 | [05-manage-detail-1440.png](05-manage-detail-1440.png) | [06-manage-detail-390.png](06-manage-detail-390.png) |
| 提交队列 | [07-submission-queue-1440.png](07-submission-queue-1440.png) | [08-submission-queue-390.png](08-submission-queue-390.png) |
| 独立批阅 | [09-submission-review-1440.png](09-submission-review-1440.png) | [10-submission-review-390.png](10-submission-review-390.png) |
| 实验统计 | [11-statistics-1440.png](11-statistics-1440.png) | [12-statistics-390.png](12-statistics-390.png) |

## 真实业务链路

- 教师从 `/courses/9501/labs/new` 创建草稿后，页面切换到该草稿的 `/edit` 深链；再次保存调用更新而不是重复创建。
- 管理总览真实执行了草稿发布，以及提交完成后的成绩发布，均经过确认对话框并返回中文成功状态。
- 学生真实提交两个版本；教师队列按学生姓名展示版本、有效性、评测依据和业务状态，不展示学生 ID 或传输枚举。
- 独立批阅页真实完成报告下载、报告评分、提交评分和确认式重新评测；评测用例与最终分刷新成功。
- 包含学生源文件的版本只展示受控下载缺口说明，不渲染 `fileId`，也不伪造下载入口；实验报告继续通过正式端点下载。
- 统计页读取真实 LAB 汇总，并复用 LRN 教师学习进度名单解析学生业务名称；名单读取失败时只局部降级，不阻断队列、批阅或评分。

## 路由、权限与视觉结果

- 六个页面均直接地址访问成功，刷新后仍保持当前页面和课程上下文。
- 学生账号直接访问教师管理深链会进入 `/403`，教师管理守卫生效。
- 队列筛选只保留 `keyword/status/evaluation/overdue`，进入批阅和返回队列时继续携带安全筛选；`role/studentId` 等参数不会透传。
- 六个页面的 390 视口均实测 `documentElement.scrollWidth === window.innerWidth === 390`，没有横向溢出。
- 最终无故障会话依次访问六个页面，浏览器 console 为 0 errors、0 warnings。

## 自动验证

- 前端定向：8 个文件，74 项测试通过。
- 前端全量：47 个文件，399 项测试通过。
- `npm run typecheck`：通过。
- `npm run build`：172 个模块构建通过。
- `git diff --check`：通过。

## 契约边界与残余限制

本 Issue 没有修改 REST API、DTO、数据库结构或 LAB 状态枚举。学生姓名通过既有 LRN 教师学习进度只读接口解析，避免 LAB 页面泄露原始 ID；该依赖失败时显示“学生姓名暂不可用”。当前后端没有教师受控下载学生源文件的端点，因此本次只明确展示阻塞状态；没有复用其他模块权限，也没有伪造下载能力。
