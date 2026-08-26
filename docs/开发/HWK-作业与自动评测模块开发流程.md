# HWK 作业与自动评测模块开发流程

## 1. 开发定位

HWK 负责作业发布、题目配置、测试用例、学生提交、FILE 作业附件资产、客观题和代码题自动评测、教师批阅、重评与反馈，并提供单次作业的固定五档分数分布和待处理名单。它依赖 AUTH 当前用户、CRS 课程成员关系，并向 LRN 触发通知，向 GRD 提供作业成绩来源。课程级、跨作业、自定义区间、趋势和统计快照仍由 GRD 负责。

HWK 与 LAB 共享评测抽象。评测状态枚举、`EvaluationResult`、错误反馈、资源限制和重评行为必须提前对齐。

## 2. 详细设计阅读入口

开发前先阅读：

- `docs/最终提交/软件详细设计说明书.md` 的 `3.5 作业与自动评测模块（HWK）`
- `docs/过程/详细设计/HWK-作业与自动评测模块-详细设计提交稿.md`
- LAB 详细设计中的评测抽象和状态枚举
- CRS 成员权限校验接口和 LRN/GRD 事件契约

## 3. 统一开发顺序

```text
1. 读 HWK 详细设计章节，确认 UI-HWK / API-HWK / SVC-HWK / DB-HWK / TC-HWK 编号
2. 建作业、客观题、测试用例、提交附件资产、提交、评测、批阅日志表
3. 写作业创建、编辑、草稿逻辑删除、发布、截止时间和评分方式 API
4. 写作业 Service、提交 Service、评测 Service、批阅 Service、独立统计 Service
5. 写教师端作业列表、创建编辑、题目和测试用例配置、提交队列和统计页面
6. 写学生端作业详情、附件上传/删除/下载、提交、历史、反馈页面
7. 接入 AUTH 当前用户和 CRS 课程成员/教师权限校验
8. 补截止、重复提交、未发布成绩、越权访问、待处理筛选和统计口径等异常处理
9. 准备作业、题目、测试用例、提交、评分测试数据
10. 联调 LRN 通知和 GRD 成绩来源
```

## 4. P0 最短交付

HWK 的 P0 路径是：

```text
教师创建作业
→ 教师发布作业
→ 学生查看作业
→ FILE 作业先上传单个附件并取得不透明 fileId
→ 学生提交文本/附件/代码；FILE 提交原子绑定该 fileId
→ 系统生成提交记录
→ 教师查看提交并给出分数
→ 学生查看反馈
```

客观题自动评分和代码题 IO 比对可以逐步补齐，但提交记录、状态流转和教师批阅必须先形成闭环。

## 5. 数据库与实体

按 DSD 建立以下表：

| 表 | 用途 |
| --- | --- |
| 作业表 | 作业标题、课程、状态、截止时间、评分方式 |
| 客观题题目表 | 题干、选项、答案、分值、排序 |
| 作业测试用例表 | 代码题输入输出、权重、可见性 |
| 作业提交表 | 文本、附件、代码、提交时间、有效提交 |
| 作业附件资产表 | 服务器 UUID、原始文件名、可信类型、大小、存储键、上传者、所属作业、绑定提交、24 小时有效期和生命周期状态 |
| 作业评测记录表 | 客观题或代码题评测结果、状态和错误信息 |
| 批阅/评分日志表 | 人工评分、教师评语、重评和修改留痕 |

作业表需要表达草稿、未开始、已发布、已截止、成绩已发布、归档等状态。提交表要支持是否允许多次提交和当前有效提交，并使用 `idx_hwk_submission_effective(homework_id, is_final, is_deleted, submit_status, student_id)` 覆盖统计有效范围，使用 `idx_hwk_submission_attention(homework_id, is_final, is_deleted, submitted_at, id, submit_status, student_id, submit_type, evaluation_status, review_status)` 支撑待处理稳定分页及组合过滤。既有唯一版本索引已覆盖 `homework_id + student_id` 左前缀。索引变更必须使用增量迁移并由迁移测试验证，不能只修改已执行的历史迁移。

DB-HWK-08 `t_hwk_submission_attachment` 是 HWK 自有的附件业务资产表。公开 `fileId` 必须是服务器生成的 UUID，服务器内部 `storage_key` 不得进入学生或教师 DTO；每条资产记录 `homework_id`、`course_id`、`uploader_id`、可信 `original_filename/content_type/file_size`、`expires_at`、可空 `submission_id`、`active_slot` 和 `UPLOADED/BOUND/DELETED` 状态。`UPLOADED` 固定 `active_slot=1`，`BOUND/DELETED` 为 NULL，唯一约束 `(homework_id,uploader_id,active_slot)` 保证同一学生在同一作业同时只有一份 active 未绑定上传。顺序再上传会原子将旧 active 转为 `DELETED` 并以新资产替换；并发首次上传仅一个返回 201，另一个以 `409/HWK_4092` 失败且回滚物理对象。FILE 提交在同一事务内校验当前学生、当前作业、未过期、未绑定状态并转为 `BOUND`；一份 FILE 提交恰好绑定一个附件，不得以提交表中的 CSV、客户端文件名、路径或裸 URL 作为附件所有权来源。

作业逻辑删除只允许课程管理者删除 `DRAFT` 父记录。Repository 必须以 `id + status='DRAFT' + is_deleted=FALSE` 原子更新 `is_deleted` 和 `updated_at`；普通编辑、发布等更新不得写入 `is_deleted`，且必须带 `is_deleted=FALSE` 条件，防止删除前发出的旧请求复活作业。删除不得级联清理题目、测试用例、判题配置、提交、评测、批阅或重评历史。

## 6. 后端 API 与 Service

教师端先实现：

- 创建作业
- 编辑作业
- 保存草稿
- 删除草稿（API-HWK-22：`DELETE /api/v1/homeworks/{homeworkId}`）
- 发布作业
- 设置截止时间、提交规则和评分方式
- 配置客观题和测试用例

学生端实现：

- 查询作业中心和作业详情
- API-HWK-23：`POST /api/v1/homeworks/{homeworkId}/attachments` 以 multipart 单 `file` 上传，`GET /api/v1/homeworks/{homeworkId}/attachments/{fileId}` 查询安全元数据，`DELETE` 删除本人未绑定上传
- API-HWK-24：`GET /api/v1/homeworks/{homeworkId}/submissions/{submissionId}/attachment/download` 受控下载已绑定附件
- 文本提交、附件提交、代码提交；FILE 的 API-HWK-07 请求 `fileIds` 必须恰好包含 API-HWK-23 返回的一个 UUID
- 提交校验
- 查询提交历史
- 查询反馈和评测结果

评测和批阅实现：

- 客观题自动评分
- 代码题 IO 比对
- 创建评测记录和状态
- 教师查看提交
- 人工评分和教师评语
- 触发重评
- 评分修改留痕

统计和待处理实现：

- API-HWK-15 保留原响应字段和 `page/size` 的未提交名单分页语义，兼容新增 `autoEvaluableCount`、`pendingEvaluationCount`、`pendingReviewCount`、`scoredCount`、`scoreDistribution`、`generatedAt`
- `scoreDistribution` 固定返回 `0-59`、`60-69`、`70-79`、`80-89`、`90-100` 五档；按 `effectiveScore = finalScore ?? autoScore`，再以 `effectiveScore / totalScore × 100` 归一化后分桶；无分数不入桶，`scoredCount` 必须等于五档之和
- API-HWK-09 增加可选 `attention=EVALUATION_PENDING|REVIEW_PENDING`；未传时保持原列表行为，传入时仍复用 `PageResponse`、1 基页码、`size` 1～100 和稳定排序
- 统计和两类待处理名单只纳入 CRS 当前活跃学生、`is_final=true`、未删除且 `submitStatus` 为 `SUBMITTED/LATE` 的有效提交；历史版本、`REJECTED` 和非当前课程学生必须排除
- `EVALUATION_PENDING` 仅包括 OBJECTIVE/CODE 的 NONE/PENDING/RUNNING；TEXT/FILE 的 NONE 不属于待评测。`REVIEW_PENDING` 包括 UNREVIEWED/NEED_REVIEW，其中 TEXT/FILE 可直接进入，OBJECTIVE/CODE 仅在评测终态后进入

Service 层拆分为 `HomeworkService`、`HomeworkQuestionService`、`HomeworkSubmissionService`、`HomeworkEvaluationService`、`HomeworkReviewService` 和独立的 `HomeworkStatisticsService`。统计必须由 Repository 使用 SQL 聚合完成，不把全部最终提交加载到内存；自动评测通过共享评测抽象调用，不在 HWK 内部单独复制一套执行器。运行时状态沿用现有枚举：提交状态为 `SUBMITTED/LATE/REJECTED`，批阅状态为 `UNREVIEWED/REVIEWED/NEED_REVIEW`，本项开发不得新增或替换状态枚举。

API-HWK-22 成功时复用 `HomeworkResponse`，返回 `deleted=true` 且 `updatedAt` 为删除时间；无课程管理权限返回 `403 / HWK_4031`，不存在或已删除返回 `404 / HWK_4001`，任何非 `DRAFT` 状态返回 `409 / HWK_4095`。原子删除零行时必须读取当前记录区分 404 与 409，不能把并发冲突统一吞成同一错误。

API-HWK-23 单请求只接收一个文件，单个 FILE 提交也只允许一个附件，大小上限为 10 MiB。扩展名、声明 MIME 和内容签名必须同时落在白名单 `pdf, zip, docx, xlsx, pptx, txt, md, csv, png, jpg, jpeg`；文件名只作显示元数据并必须净化。响应只返回 UUID、净化文件名、可信 MIME、字节数、状态、上传时间和过期时间，不返回 `storageKey`、服务器路径或裸 URL。API-HWK-24 每次下载都重新校验登录态、课程关系、作业与提交归属；仅提交学生本人或课程管理者可下载，并通过安全的 `Content-Type`、`Content-Disposition` 和 `X-Content-Type-Options: nosniff` 返回准确版本内容。

附件错误码冻结为：`HWK_4005`（FILE 提交格式/fileIds 数量不合法、空文件、内容签名非法或损坏 ZIP/OOXML 结构）、`HWK_4031`（非成员、非本人且非课程管理者）、`HWK_4042`（格式合法但未知/跨归属 UUID，或附件/提交/绑定关系不存在或不可见）、`HWK_4091`（上传已过期）、`HWK_4092`（附件已绑定/删除/重用，或并发 active-slot 冲突）、`HWK_4131`（超过 10 MiB）、`HWK_4151`（扩展名或声明 MIME 不支持/不匹配）、`HWK_5002`（存储读写或补偿失败）。Repository 将 `DuplicateKeyException/ConcurrencyFailureException` 稳定收敛为 `HWK_4092`。元数据回滚或部分流写入失败后先立即删除物理对象，失败则调用 `FileStorageService.deferDelete`。`LocalDiskFileStorageService` 在存储根目录 `.pending-deletes` 中以 storageKey SHA-256 marker 和原子 move 持久化，`HomeworkAttachmentCleanupService` 以 `pendingDeletes` 分批重试并在成功后 `completeDeferredDelete`；损坏 marker 必须跳过，不得饿死后续合法项。残余边界是整卷删除与 journal 同时失败、病毒扫描缺失、网关/容量限流，以及允许重交时 BOUND 历史的容量规划。

## 7. 前端页面与交互

HWK 前端必须包含：

| 页面 | 完成标准 |
| --- | --- |
| 作业中心页 | 学生查看待完成/已完成作业；教师查看课程作业列表，仅对 DRAFT 展示“删除草稿”，确认后调用 API-HWK-22 |
| 作业详情页 | 展示说明、题目、截止时间、提交规则 |
| 作业创建/编辑页 | 教师配置题目、测试用例、截止时间和评分方式 |
| 作业提交页 | FILE 作业支持单文件选择、真实上传、24 小时倒计时、失败保留选择、重试/删除，以及上传成功后以一个 fileId 提交；不生成假 fileId |
| 提交历史页 | 展示每次提交、最新提交、当前有效提交和安全附件元数据；仅在有权限时调用 API-HWK-24 下载 |
| 教师批阅页 | 教师查看提交、评测结果、安全附件元数据并受控下载，完成人工评分和评语 |
| 教师提交队列 | 支持普通筛选和 `attention` 待评测/待批阅深链；分页、刷新、前进和后退可恢复 URL 状态 |
| 作业统计页 | 展示固定五档分布，以及未提交、待评测、待批阅三个服务端分页名单；支持键盘和窄屏访问 |
| 作业反馈页 | 学生查看提交状态、评测结果、最终反馈 |

页面必须展示成功、失败、加载、空状态，以及截止后不可提交、未发布成绩不可见、重复提交被拒绝等状态。统计页和提交队列从姓名服务补齐展示名；姓名服务失败时使用不含学号/用户编号的安全占位文案，不得裸露 `studentId`。

附件上传失败时必须保留用户已选文件并允许重试；删除未绑定上传后清空附件状态；上传过期、跨作业/跨用户复用、重复绑定、文件超限和类型不支持均须显示可操作提示。页面只能展示安全元数据，不能拼接存储地址或在 DOM、日志、下载链接中暴露 `storageKey`/裸 URL。

教师总览的删除动作必须先确认，取消时不得发送请求；请求期间与编辑、发布等生命周期动作互斥。成功后刷新列表，删除当前页最后一项时回退到有效页；失败时保留原行和筛选/页码并允许重试。该入口需完成 1440px 与 390px 浏览器验收。

## 8. 权限、异常与跨模块事件

必须覆盖：

- 非课程成员不可查看或提交作业
- 学生不能查看他人提交
- 非课程成员不能上传附件；学生不能查询、删除或绑定他人、其他课程或其他作业的附件
- FILE 提交只能绑定当前学生为当前作业上传、状态为 UPLOADED 且未过 24 小时的唯一附件，已绑定/已删除附件不能复用
- 已绑定附件只能由提交学生本人或课程管理者通过 API-HWK-24 下载，且每次请求重新鉴权
- 学生不能查看未发布最终分
- 不允许多次提交时二次提交被拒绝
- 截止后提交按规则拒绝或标记迟交
- 教师只能管理自己课程内作业
- 课程管理者只能删除 DRAFT 作业；非 DRAFT 返回 409，不存在或已删除返回 404
- 学生和无课程管理权限的教师访问统计或待处理名单时返回 403，且响应不得泄露统计值、名单或学生标识
- 重评和分数修改必须留痕

跨模块事件：

- 作业发布 → LRN 通知
- 作业评测完成 → LRN 通知
- 作业成绩发布 → LRN 通知，并向 GRD 提供作业成绩来源

向 GRD 提供成绩时必须包含来源类型、来源编号、课程编号、学生编号、分数、满分、状态和更新时间。

## 9. 测试与自测清单

| 测试点 | 验收标准 |
| --- | --- |
| 作业发布 | 教师可创建、编辑、发布作业 |
| 作业发布事务 | `HOMEWORK_PUBLISHED` 必达通知成功后才提交发布；通知失败返回 `503/HWK_5003`，作业保持 DRAFT 且学生不可见 |
| 草稿逻辑删除 | 仅课程管理者可删除 DRAFT；确认取消不发请求；重复删除为 404、非 DRAFT 为 409；并发编辑/发布不能复活，父表删除后全部子表和历史仍保留 |
| 学生提交 | 文本、附件或代码提交至少一种主流程可演示 |
| 附件上传生命周期 | API-HWK-23 覆盖单文件上传、安全元数据查询、本人未绑定删除、24 小时过期和 `UPLOADED -> BOUND/DELETED`；`active_slot` 保证同学生/作业仅一份 active 上传，顺序上传原子替换旧件；不返回存储键或裸 URL |
| 附件边界 | 单请求/单提交恰好 1 个、10 MiB、扩展名/MIME/内容签名三重白名单；空文件、伪装类型、跨作业/跨用户/重复 fileId 均拒绝且错误码稳定 |
| 附件原子绑定与补偿 | API-HWK-07 创建 FILE 提交与 DB-HWK-08 绑定同事务成功或失败；并发首次上传为 201 + `409/HWK_4092` 且只留 1 条 active/物理对象；元数据或部分流失败立即删除，删除失败入 journal，损坏 marker 不饿死合法项 |
| 附件受控下载 | API-HWK-24 仅学生本人或课程管理者访问准确提交版本；每次重鉴权，响应头安全，上传/下载哈希一致且不泄露内部路径 |
| 附件浏览器验收 | MAN-HWK-012 覆盖真实选择/上传/删除/重试/提交/历史与批阅下载、无假 fileId、1440px/390px、控制台无错误 |
| 提交规则 | 不允许多次提交时二次提交被拒绝 |
| 自动评测 | 客观题或代码题可生成评测记录 |
| 教师批阅 | 教师可评分、写评语、触发重评 |
| 作业统计 | 五档边界、非 100 满分归一化、空分布、生成时间和 `scoredCount` 总数一致 |
| 待处理名单 | 未提交、待评测、待批阅均为服务端分页并稳定排序；TEXT/FILE、代码评测中和评测终态语义正确 |
| 统计数据范围 | 仅当前 CRS 活跃学生的最终有效提交；历史、删除、REJECTED 和非当前学生均排除 |
| 统计查询性能 | Repository 使用 SQL 聚合，组合索引由增量迁移和迁移测试证明 |
| 统计页面验收 | URL 可恢复，姓名失败不泄露裸 ID，并完成 1440px 与 390px 浏览器检查 |
| 教师删除入口验收 | 仅 DRAFT 显示入口；确认、互斥 pending、失败保留、成功刷新和末页回退正确，并完成 1440px 与 390px 浏览器检查 |
| 学生反馈 | 学生只看到允许公开的评测和成绩 |
| 权限边界 | 非成员、越权教师、截止后提交均处理正确 |
| 成绩来源 | GRD 可读取或接收作业成绩 |

## 10. 联调顺序

1. AUTH → HWK：当前用户、角色和登录态
2. CRS → HWK：课程成员和教师权限
3. HWK ↔ LAB：评测状态和 `EvaluationResult` 统一；附件仅复用低层 `FileStorageService`，HWK 不复用 LAB/CRS 的业务 API、元数据表或授权边界
4. HWK → LRN：发布、评测完成、成绩发布通知
5. HWK → GRD：作业成绩来源同步或查询

完成标准是教师发布作业、学生提交、自动评测或教师批阅、学生查看反馈、成绩进入 GRD 的路径可演示。
