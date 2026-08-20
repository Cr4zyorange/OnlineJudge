# Issue #223 HWK 教师端流程验收证据

## 验收环境

- 基线：`origin/dev@7d7ff58a5fdc0ed0598f150c0483bcce2283f40d`
- 分支：`feature/223-hwk-teacher-flow`
- 浏览器：Playwright Chrome，1440 x 900 与 390 x 844
- 服务：本地 Spring Boot + 内存 H2 + fake sandbox；页面调用真实 API
- 账号：仓库公开演示教师 `teacher001`、学生 `student001`
- 课程：`9501`；本次新增作业、提交、批阅和成绩只存在于一次性内存数据库会话

## 前后对照

改造前，教师创建、发布、列表、批阅与统计入口纵向堆叠在同一工作台，移动端表格存在裁切：

- [改造前 1440](../../../docs/开发/assets/frontend-refactor-baseline/2026-08-14/07-hwk-teacher-1440.png)
- [改造前 390](../../../docs/开发/assets/frontend-refactor-baseline/2026-08-14/08-hwk-teacher-390.png)

改造后按职责拆为管理总览、结构化编辑、单作业详情、提交队列、独立批阅和统计六个页面；后五页为本次新增职责页，因此逐页旧版截图记为 `before=N/A`：

| 页面 | 1440 x 900 | 390 x 844 |
| --- | --- | --- |
| 管理总览 | [01-manage-index-1440.png](01-manage-index-1440.png) | [02-manage-index-390.png](02-manage-index-390.png) |
| 结构化编辑（本机自动保存状态） | [03-editor-1440.png](03-editor-1440.png) | [04-editor-390.png](04-editor-390.png) |
| 单作业管理详情 | [05-manage-detail-1440.png](05-manage-detail-1440.png) | [06-manage-detail-390.png](06-manage-detail-390.png) |
| 提交队列 | [07-submission-queue-1440.png](07-submission-queue-1440.png) | [08-submission-queue-390.png](08-submission-queue-390.png) |
| 独立批阅 | [09-submission-review-1440.png](09-submission-review-1440.png) | [10-submission-review-390.png](10-submission-review-390.png) |
| 作业统计 | [11-statistics-1440.png](11-statistics-1440.png) | [12-statistics-390.png](12-statistics-390.png) |

编辑器刷新后恢复状态另留存一组补充证据：

| 状态 | 1440 x 900 | 390 x 844 |
| --- | --- | --- |
| 恢复 24 小时内未保存内容 | [13-editor-recovered-1440.png](13-editor-recovered-1440.png) | [14-editor-recovered-390.png](14-editor-recovered-390.png) |

## 真实业务链路

- 教师通过结构化编辑器创建文本草稿，保存后进入 `/edit` 深链；草稿详情正确锁住提交与统计入口。
- 教师在管理总览确认发布，学生从正式提交页提交新版本，教师队列立即按姓名显示当前有效版本与批阅状态。
- 教师在独立批阅页核对答案，填写人工分、最终分及批阅理由；保存后分数、状态与审计日志同步刷新。
- 教师在管理总览确认发布成绩；作业进入“成绩已发布”，统计页从真实 API 读取提交率、批阅率与平均/最高/最低分。
- 管理总览使用正式分页 API；队列可按学生姓名及提交、评测、批阅状态筛选，URL 与返回深链不暴露学生 ID。
- 姓名筛选沿用现有模糊候选 API，在前端逐页按学生身份精确收口并使用稳定匿名引用恢复深链；没有改变既有 API 语义。
- 管理总览、详情和队列只展示接口原始批阅计数，不用历史版本或非终态提交推导全量待处理人数；精确待处理口径归 #225。
- 编辑器对未保存修改提供 500ms 本机自动保存、24 小时有效恢复、服务器版本冲突保护、站内离开确认与刷新/关闭保护；服务器保存成功后清理本机草稿。Playwright 实测填写“本机草稿恢复验收”后出现自动保存提示，接受刷新确认后同一路由恢复标题并显示恢复提示。
- 上述创建、提交、批阅与发布闭环在一次性内存会话完成；最终响应式截图使用服务重启后的真实 seed API 基线（最终分 `88`），两者均未使用前端假数据。

## 路由、权限与视觉结果

- 六个页面均直接地址访问成功；统计深链刷新后仍停留在当前页面。
- 学生账号直接访问教师批阅深链会进入 `/403`，教师管理守卫生效。
- 编辑器站内离开确认实测：取消后仍停留在 `/courses/9501/homeworks/new`，确认后进入 `/courses/9501/homeworks/manage`。
- 六个页面的 390 视口均实测 `documentElement.scrollWidth === window.innerWidth === 390`，没有横向溢出。
- 教师与学生无故障会话的浏览器 console 均为 0 errors、0 warnings。

## 自动验证

- 前端全量：52 个测试文件，480 项测试通过。
- `npm run typecheck`：通过。
- `npm run build`：187 个模块构建通过。
- 后端 HWK 定向：44 项测试通过。
- `git diff --check` 与新增文件空白检查：通过。

## 契约边界与残余限制

- 本 Issue 没有修改 REST API、DTO、数据库结构或 HWK 状态枚举。
- 现有契约没有删除作业端点，因此页面不伪造删除成功；草稿逻辑删除的设计、权限、API、并发保护和测试由 [Issue #224](https://github.com/Cr4zyorange/OnlineJudge/issues/224) 独立交付。
- 统计契约只提供基础聚合与未提交学生 ID，没有分数段、未评测名单或待批阅名单；页面只展示真实汇总并明确说明，不生成推测数据，完整契约由 [Issue #225](https://github.com/Cr4zyorange/OnlineJudge/issues/225) 独立交付。
- FILE 作业在安全上传/提交链路 Issue #214 完成前保持可编辑但不可发布，也不渲染存储地址。
- 当前评测运行时只支持 Python；编辑器和发布入口均阻止带 Java、C++ 或 JavaScript 的旧 CODE 草稿绕过限制。输出比较按当前运行时真实行为固定为 trim，不伪装可切换模式。
