# Issue #222 LAB 提交源文件受控下载验收证据

## 环境

- 日期：2026-08-22（Asia/Shanghai）
- 基线：`origin/dev@e363a06e0724`
- 分支：`feature/222-lab-source-download`
- 服务：本地 Spring Boot + H2 演示数据，Vite `http://127.0.0.1:5173`
- 浏览器：Playwright Chromium，1440 x 1000 与 390 x 844
- 账号：仓库公开演示账号 `student001` / `teacher001`
- 目标：课程 `9501`，实验 `950211`，本轮提交 `950204`

## 截图

| 证据 | 视口 | 验收点 |
| --- | --- | --- |
| [01-student-source-selected-1440.png](01-student-source-selected-1440.png) | 1440px | 学生选择 Unicode 文件名源码 |
| [02-student-submit-success-1440.png](02-student-submit-success-1440.png) | 1440px | 真实 API 提交成功，生成版本 1 |
| [03-teacher-source-metadata-1440.png](03-teacher-source-metadata-1440.png) | 1440px | UI-LAB-06 展示安全元数据与独立下载入口 |
| [04-teacher-source-metadata-390.png](04-teacher-source-metadata-390.png) | 390px | 窄屏单列布局，无横向溢出 |
| [05-teacher-download-success-1440.png](05-teacher-download-success-1440.png) | 1440px | 受控下载完成后的页面反馈 |
| [06-anonymous-download-401.png](06-anonymous-download-401.png) | 1440px | 匿名直达下载端点返回 401 / `ERR-AUTH-04` |

390px 视口实测 `innerWidth=documentElement.scrollWidth=body.scrollWidth=390`。

## 真实业务链路

1. `student001` 在 `/courses/9501/labs/950211/submit` 上传 `student-source-林晓.py`，`POST /api/v1/labs/950211/submissions` 返回 201，提交 ID 为 `950204`。
2. `teacher001` 进入 `/courses/9501/labs/950211/manage/submissions/950204`，页面展示 `student-source-林晓.py`、`text/x-python-script`、`84 B`和“下载源文件”按钮。
3. 教师详情响应仅包含顶层 `hasFile=true` 与四字段 `sourceFile`，没有 `fileId`、storage key、路径或下载 URL。
4. `GET /api/v1/labs/950211/submissions/950204/source/download` 返回 200，`Content-Type: text/x-python-script`、`Content-Length: 84`，`Content-Disposition` 同时含 UTF-8 `filename*`。
5. Playwright 保存文件为 `.playwright-cli/student-source-林晓.py`。上传文件与下载文件均为 84 字节，`cmp` 通过，SHA-256 均为 `1aa9b0c2b985e0062b13b77eb0676eeae53ccb3064deae0ad88eff49bd6f7e17`。
6. 学生携带有效 bearer token 直调同一下载接口返回 403 / `ERR-AUTH-05`；学生详情只返回 `downloadAvailable=false`，且没有 `fileId`。

教师名义链路 console 为 0 errors / 0 warnings。学生越权验收仅出现一条预期的 403 resource error，没有应用脚本错误。

## 自动验证

- 后端迁移 + 接口定向：41/41 通过，包含权限重验、归属绑定、历史数据、删除状态、物理缺失、MIME/路径/响应头防护与事务补偿。
- 前端 LAB + API 定向：191/191 通过。
- 前端全量：53 个文件，521/521 通过；`typecheck` 和生产构建通过。
- 完整后端回归、文档校验和最终编译结果以 PR 收口记录为准。

## 边界

- 学生本人源文件下载不在 #222 范围内，学生只能查看安全元数据。
- 实验报告下载继续走独立报告链路，不与源文件共用 ID 或下载权限。
- 当事务回滚且物理删除本身也失败时，当前实现没有业务级孤儿文件重试队列；这是后续存储运维能力，不放宽本期下载授权边界。
