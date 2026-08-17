# Issue #213 浏览器验收记录

## 验收环境

- 基线：`origin/dev@7e327870989cdfa0848bbb4b95c1a39a9bd7e884`
- 前端：Vite `http://127.0.0.1:5173`
- 后端：Spring Boot `http://127.0.0.1:8080`，评测沙箱使用仓库内 `fake` 模式
- 账号：演示学生 `student001`
- 课程：`9501`
- 桌面视口：`1440 × 900`
- 移动视口：`390 × 844`

重构前对照图位于：

- `docs/开发/assets/frontend-refactor-baseline/2026-08-14/03-hwk-student-1440.png`
- `docs/开发/assets/frontend-refactor-baseline/2026-08-14/04-hwk-student-390.png`

## 真实链路

1. 列表：真实查询 `courseId/keyword/status/page/size`，验证查询、重置、分页、详情入口和空/失败状态测试覆盖。
2. TEXT：作业 `950311` 的详情、提交草稿、提交成功、历史版本和结果入口均通过真实 API 验收。
3. OBJECTIVE：临时作业 `950312` 通过单选、多选、判断控件提交，提交 `950305` 自动计分 `100`，通过 `3/3`。
4. CODE：临时作业 `950313` 连续产生三个真实版本；最新提交 `950308` 由假沙箱评测为 `ACCEPTED`，得分 `100`，通过 `1/1`。历史结果路由按版本选择最新提交。
5. FILE：临时作业 `950314` 显示真实 `input[type=file]`；由于仓库没有学生作业附件上传端点，主提交按钮保持禁用，并显示阻塞说明。后续契约由 GitHub #214 跟踪，没有用文件名或附件编号伪造成功链路。
6. 已发布成绩：作业 `950301` 的提交 `950303` 显示最终分 `88` 和教师评语；自动评测不存在时展示明确空态。

临时作业仅写入本次本地 H2 验收会话，不属于数据库种子或产品代码。

## 交互与可访问性

- sessionStorage 草稿使用用户、课程、模块、任务四级隔离键；刷新后恢复，真实提交成功后清除。
- 带草稿离开提交页会出现确认框；取消后 URL 和页面标题都保持在提交页，确认后进入提交历史。
- 浏览器刷新触发 `beforeunload` 保护。
- 多选题按题目选项的稳定顺序序列化，点击顺序不影响后端判分；`JUDGE` 与设计枚举 `TRUE_FALSE` 都使用真假单选控件。
- 详情、提交、历史和结果组件在路由复用时会失效旧请求；迟到的提交、评测、详情或日志响应不会覆盖新作业，也不会清除新作业草稿。
- 键盘从页面标题继续 Tab，可依次到达关键词、状态、每页数量、查询、重置以及每条作业的“查看”链接。
- 六个移动端页面均实测 `document.documentElement.scrollWidth <= window.innerWidth`，无横向溢出。
- 新浏览器会话遍历列表、提交、历史、结果和 FILE 阻塞页后，控制台为 `Errors: 0, Warnings: 0`。

## 自动化验证

- `npm run test:unit`：39 个测试文件、245 项测试全部通过。
- `npm run test:unit -- tests/unit/hwk tests/unit/app/router.spec.ts`：HWK 与正式路由 85/85 通过。
- `npm run typecheck`：通过。
- `npm run build`：153 个模块构建通过。
- `mvn -Dtest=HomeworkControllerTest,HomeworkBearerAuthControllerTest,HomeworkSubmissionServiceTest,HomeworkMigrationTest test`：后端 HWK 契约 44/44 通过。
- `git diff --check`：通过。

## 截图索引

| 文件 | 验收内容 |
| --- | --- |
| `01-homework-list-1440.png` | 桌面端列表、摘要、筛选和分页 |
| `02-homework-detail-1440.png` | 桌面端只读详情 |
| `03-homework-submit-draft-1440.png` | TEXT 提交与自动草稿 |
| `04-homework-history-1440.png` | 提交历史、最新/当前有效标记 |
| `05-objective-submit-1440.png` | OBJECTIVE 结构化作答 |
| `06-code-submit-1440.png` | CODE 编辑器与语言选择 |
| `07-file-blocked-1440.png` | FILE 真实选择控件和安全阻塞态 |
| `08-published-result-1440.png` | 已发布最终分和教师评语 |
| `09-homework-list-390.png` | 移动端列表卡片 |
| `10-homework-detail-390.png` | 移动端详情 |
| `11-objective-submit-390.png` | 移动端结构化作答和固定操作区 |
| `12-homework-history-390.png` | 移动端提交历史 |
| `13-published-result-390.png` | 移动端已发布结果 |
| `14-file-blocked-390.png` | 移动端 FILE 阻塞态 |
| `15-code-result-1440.png` | CODE 最新版本自动评测通过 |
