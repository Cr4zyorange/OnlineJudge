# Issue #214 HWK FILE 附件链路验收记录

验收日期：2026-08-22（Asia/Shanghai）

## 环境与固定数据

- 前端：`http://127.0.0.1:5173`
- 后端：`http://127.0.0.1:8080`
- 本地运行数据库：H2 file mode
- 课程：`9501`
- FILE 作业：`950312`
- 已上传文件：`9931efa8-57f9-4b18-9636-d14d96c43ad0`
- 已绑定提交：`950304`
- 原始文件：`student-homework-林晓.md`，222 B，`text/markdown`
- 原始与两次下载的 SHA-256：`d1847d02cb36254509d0ec2df0eaf20805ce3f6aed4e25a809aea88f8d8568fa`

两份下载副本在完成逐字节 `cmp` 与 SHA-256 核对后未纳入版本控制，避免重复保存相同内容。

## 真实浏览器链路

使用 Playwright CLI 连接真实前后端完成以下流程：

1. 教师在 UI 创建并发布 FILE 作业。
2. 学生选择扩展名为 `.pdf`、内容为纯文本的伪装文件，真实上传返回 HTTP 400 / `HWK_4005`，页面给出确定性错误并清除无效选择。
3. 学生选择合法 Markdown 文件；拦截一次上传并返回 HTTP 500 / `HWK_5002`，页面保留文件选择以便重试。
4. 解除拦截后重试，真实上传返回 HTTP 201；响应只包含公开 UUID 与文件元数据，不包含存储路径、`storageKey` 或裸下载 URL。
5. 刷新页面先触发未绑定附件的 `beforeunload` 保护；确认离开后，页面从 `sessionStorage` 恢复记录，并通过真实 GET 元数据请求重新校验附件。
6. 学生提交返回 HTTP 201，附件由 `UPLOADED` 绑定到提交 `950304`。
7. 学生在提交历史中查看元数据并下载；教师在独立批阅页查看相同元数据并下载。两份下载与原始文件逐字节一致，SHA-256 相同。
8. 另上传一份未绑定附件，在刷新恢复 GET 上注入 HTTP 500 / `HWK_5002`；页面保留 `fileId` 和 session 恢复信息。解除故障后再次刷新，真实 GET 200 并恢复附件，随后通过 DELETE 200 清理该验收附件。

## 鉴权与越权验证

| 场景 | 结果 |
| --- | --- |
| 未登录下载提交附件 | HTTP 401 / `ERR-AUTH-04` |
| 第二学生未加入课程时上传 | HTTP 403 / `HWK_4031` |
| 第二学生加入课程后读取他人的 `fileId` | HTTP 404 / `HWK_4042` |
| 第二学生用他人的 `fileId` 创建提交 | HTTP 404 / `HWK_4042` |
| 第二学生下载他人的提交附件 | HTTP 403 / `HWK_4031` |

错误隐藏策略避免泄露附件 UUID 是否存在；课程成员资格和附件所有权均在每次请求时重新校验。

## 视口与控制台

- `390 x 844`：`scrollWidth = clientWidth = 390`
- `1440 x 1000`：`scrollWidth = clientWidth = 1440`
- 无 JavaScript 控制台错误或警告；浏览器记录的失败网络请求仅来自预期的 `HWK_4005` 与人工注入的 `HWK_5002`。

## 自动化与数据库验证

- 后端 Maven：340 项，339 通过、1 跳过，0 failures / 0 errors；#214 定向 9 类 94/94 通过。
- 前端 Vitest：53 个测试文件、545 项全部通过。
- 前端 `vue-tsc --noEmit` 与生产构建均通过。
- 真实 MySQL 9.6：完整 `database/mysql/compose-schema.sql` 成功导入，`20260822_03_create_hwk_submission_attachment.sql` 连续执行两次成功；两份 9 MiB 并发上传得到 201 与 `409/HWK_4092`，DB active 行与物理对象均恰好 1 份，顺序替换后仍为 1 份。
- Nginx 已显式设置 `client_max_body_size 55m;`，与 Spring 50MB/55MB 共享传输契约一致；HWK 业务上限仍为 10 MiB。
- Docker daemon 当时不可用，因此没有重复运行容器启动路径；已用本机真实 MySQL 完成等价迁移验证。

## 截图索引

- `01-teacher-published.png`：教师发布成功。
- `02-signature-rejected.png`：伪装文件被拒绝。
- `03-transient-upload-retry.png`：暂时性失败后选择仍保留。
- `04-restored-upload.png`：刷新后附件恢复并重新验证。
- `05-student-submitted.png`：学生提交回执。
- `06-student-history-download.png`：学生历史页元数据与下载入口。
- `07-teacher-review-download.png`：教师批阅页元数据与下载入口。
- `08-teacher-review-mobile.png`：390 px 移动视口。
- `09-restore-storage-failure-preserved.png`：刷新重验遇到 `HWK_5002` 时保留恢复信息。
- `10-restore-storage-recovered.png`：故障解除后通过真实 GET 恢复附件。

## 残余风险

当前版本执行扩展名、严格 MIME、文件签名和 ZIP/OOXML 结构校验，但不包含恶意软件扫描。生产环境仍需接入病毒扫描/隔离、网关限流与容量监控；允许重交时的 BOUND 历史需纳入存储规划。如果整个存储卷同时无法删除对象与写入 journal marker，补偿意图无法持久化，但该异常不会被静默吞掉。
