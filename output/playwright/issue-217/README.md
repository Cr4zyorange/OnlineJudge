# Issue #217 LAB 学生实验附件验收证据

## 验收环境

- 基线：`origin/dev@af8c0334c09b432f0c5b805adb5ddba3dc43b170`
- 分支：`feature/217-lab-attachment-download`
- 浏览器：Playwright Chromium，1440 x 900 与 390 x 844
- 后端：本地 Spring Boot + 内存 H2 + fake sandbox
- 账号：仓库公开演示学生 `student001`
- 验收数据：仅存在于本次 H2 会话，未写入种子数据或生产数据库

## 前后对照

Issue #216 的实验详情页只显示 `attachmentIds` 的数量，作为本次改造前基线：

- [改造前 1440](../issue-216/02-detail-after-1440.png)
- [改造前 390](../issue-216/08-detail-after-390.png)

改造后页面展示有效资源的名称、原文件名、中文类型、可读大小与受控下载操作：

| 页面 / 状态 | 证据 |
| --- | --- |
| 有效附件 + 部分失效提示，1440 x 900 | [01-detail-attachment-after-1440.png](01-detail-attachment-after-1440.png) |
| 有效附件 + 部分失效提示，390 x 844 | [02-detail-attachment-after-390.png](02-detail-attachment-after-390.png) |
| 元数据失败局部错误，390 x 844 | [03-metadata-error-local-after-390.png](03-metadata-error-local-after-390.png) |

## 真实权限与下载链路

内存 H2 中的实验配置了 6 个附件引用：当前课程学生可见资源、未来发布资源、教师专属资源、已删除资源、跨课程资源和未知 ID。学生页面只渲染 1 个合法资源，并提示另 5 个附件不可访问；页面和可访问树均未出现内部资源 ID、存储路径或原始 `downloadUrl`。

后端实测状态：

- 当前课程、学生可见、已发布资源：下载 `200`
- 未来发布资源：下载 `403`
- 已删除资源：下载 `404`
- 跨课程资源 ID：下载 `404`

浏览器网络只出现 `GET /api/v1/courses/9501/resources` 与带 Bearer 会话的 `GET /api/v1/courses/9501/resources/{resourceId}/download`。键盘从页面起点经过 18 个 Tab 停靠点到达 `下载附件：实验附件：顺序表验收说明`，按 Enter 成功下载，浏览器建议文件名为 `README.md`，下载无失败。

## 失败与恢复

- 将资源列表请求注入为 `503` 后，实验标题、说明、公开用例和提交入口继续可用；附件区单独显示“附件服务暂时不可用”和原生重试按钮。
- 恢复网络并点击重试后，有效资源重新出现，局部错误消失。
- 将单个下载请求注入为 `503` 后，资源行保留并显示“附件下载暂时失败”；恢复后再次下载成功，错误消失。
- 最终无故障浏览器会话为 0 console errors、0 console warnings；390 视口实测 `documentElement.scrollWidth === window.innerWidth === 390`。

## 自动验证

- 前端 LAB scoped：3 个文件，65 项测试通过。
- 前端全量：43 个文件，366 项测试通过。
- `npm run typecheck`：通过。
- `npm run build`：160 个模块构建通过。
- 后端 `CourseControllerTest`：25 项测试通过。
- `git diff --check`：通过。

## 契约边界

本 Issue 没有修改 LAB API、DTO 或数据库结构。页面继续消费 `LabExperimentDetail.attachmentIds`，再与 CRS 学生资源列表取交集；最终下载权限由 CRS 服务端二次校验。教师端附件上传与选择器仍属于 UI-LAB-04，不混入本次交付。
