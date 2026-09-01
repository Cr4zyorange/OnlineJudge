# HWK 模块专属契约

来源：codex skill 的 `issue-map.md`、`contracts.md` 与 `review-checklist.md`（以 `docs/提炼skills/onlinejudge-hwk-development-workflow/` 最新副本为准）。这是不易从代码直接看出的 HWK 契约索引；大改前先对照在线文档核实。

## 文档来源（按序）

1. `docs/开发/HWK-作业与自动评测模块开发流程.md`
2. `docs/最终提交/软件需求规格说明书.md`
3. `docs/最终提交/软件概要设计说明书.md`
4. `docs/最终提交/软件详细设计说明书.md`，尤其是 3.5 节、第 4 章 UI 行、第 5 章 API/数据库行、第 9 章追溯行
5. `docs/过程/详细设计/HWK-作业与自动评测模块-详细设计提交稿.md`
6. UI 工作：`docs/过程/UI设计参考/index.html`、`style.css`、`img/back.jpg` 及相邻既有 Vue 视图

```powershell
rg -n "FR-HWK|NFR-HWK|UC-HWK|OP-HWK|UI-HWK|API-HWK|SVC-HWK|DB-HWK|TC-HWK|HWK_" docs
rg -n "CoursePermissionClient|HeaderCoursePermissionClient|CurrentUser|NotificationEvent|SourceGradeDTO|EvaluationTask|EvaluationResult|SandboxExecutor" backend/src/main/java
rg -n "request<|LabTeacherView|StudentGradeView|GradeItemConfigView|Homework" frontend/src frontend/tests
```

## Issue 地图

用此表为当前 GitHub issue 选择最小完整纵向切片。始终以在线 issue 正文、Project 字段和相邻阶段 issue 为准；下表是历史分解，不是当前授权。

| Issue | 需求 | 交付焦点 | 追溯 | 首个红测试 |
| --- | --- | --- | --- | --- |
| `#75 HWK-01` | FR-HWK-01 创建与发布 | 教师/助教创建、编辑、配置、发布、关闭；作业中心列表 | UI-HWK-01/02/03; API-HWK-01/02/03/04/16/18; DB-HWK-01/02/03/07; TC-HWK-01/02/03 | 草稿持久化字段；发布合法作业发事件且学生可见；代码作业无测试用例被拒 |
| `#76 HWK-02` | FR-HWK-02 学生查看与提交 | 学生可见列表/详情、答案隐藏、提交规则、截止/重交处理 | UI-HWK-04/05; API-HWK-05/06/07/17; DB-HWK-01/02/04; TC-HWK-04/05/06 | 学生看到已发布详情；学生看不到草稿与答案；合法提交成功；截止/重复提交失败 |
| `#77 HWK-03` | FR-HWK-03 提交历史 | 学生历史、教师列表/详情、最新/有效提交 | UI-HWK-06; API-HWK-08/09/10; DB-HWK-04; TC-HWK-07/08 | 多次提交保留历史且仅最新/有效 `is_final=1`；教师列表分页且权限过滤 |
| `#78 HWK-04` | FR-HWK-04 自动评测 | 客观题判分、代码评测任务、结果查询、重新评测 | UI-HWK-05/07/08; API-HWK-07/11/12/18/19/20; DB-HWK-03/04/05; TC-HWK-09/10/11/12 | 客观题提交生成评测与得分；代码提交生成 PENDING 任务；失败状态保留提交；重新评测追加记录 |
| `#79 HWK-05` | FR-HWK-05 教师评审与复评 | 手动评分/评语、最终成绩、校验、评审日志 | UI-HWK-08/09; API-HWK-09/10/12/13/21; DB-HWK-04/05/06; TC-HWK-13/14/15 | 评审更新手动/最终成绩与评语；越界分数失败；评审/复评/发布写日志 |
| `#80 HWK-06` | FR-HWK-06 反馈与结果展示 | 学生结果可见性、成绩发布、统计 | UI-HWK-01/04/07/09; API-HWK-05/06/11/14/15; DB-HWK-04/05/06; TC-HWK-16/17/18 | 未发布最终成绩不可见；成绩发布暴露反馈并发送成绩/通知事件；统计正确 |
| `#81 HWK-07` | NFR-HWK-01..05 | 可靠性、性能、可追溯、安全、模块测试 | 全部 HWK 页面/API/表; TC-HWK-N01..N05 | 非成员/他人提交/隐藏用例访问被拒；列表查询分页有索引；审计链完整 |
| `#214` | FILE 附件生命周期 | API-HWK-23/24、DB-HWK-08、TC-HWK-20..27、MAN-HWK-012；安全上传/绑定/下载/清理 |
| `#224` | 草稿逻辑删除 | API-HWK-22、DB-HWK-01、TC-HWK-19；仅 DRAFT 原子删除父记录并保留历史 |
| `#225` | 统计与关注队列 | 固定五档分布、活跃学生分母、未交/待评测/待评审分页与稳定 URL 状态 |
| `#264 D2-HWK` | 场景/文档/测试闭环 | 仅 UC-HWK-01/02；三层时序图（用仓库图表工具链）、追溯、模块测试证据、复用归其 issue 所有的共享 E2E |

新 issue 出现时，先读其在线正文再更新本表；不得推断有权重开或扩大已闭环的 issue。

## P0 流程

```text
teacher creates homework -> teacher publishes homework
-> student sees visible homework -> student submits
-> evaluation or teacher review records result
-> student sees allowed feedback
-> HWK exposes or sends grade source to GRD
-> LRN receives relevant notification event
```

早期 issue 可以只实现部分流程，但每个 issue 必须让链路更连贯。

## API 路由

| ID | Method | Path | 角色边界 |
| --- | --- | --- | --- |
| API-HWK-01 | POST | `/api/v1/homeworks` | 有 CRS 课程管理权的教师/助教 |
| API-HWK-02 | PUT | `/api/v1/homeworks/{homeworkId}` | 课程管理者 |
| API-HWK-03 | PUT | `/api/v1/homeworks/{homeworkId}/publish` | 课程管理者 |
| API-HWK-04 | PUT | `/api/v1/homeworks/{homeworkId}/close` | 课程管理者 |
| API-HWK-05 | GET | `/api/v1/homeworks` | 已登录，按角色过滤 |
| API-HWK-06 | GET | `/api/v1/homeworks/{homeworkId}` | 学生课程成员或课程管理者 |
| API-HWK-07 | POST | `/api/v1/homeworks/{homeworkId}/submissions` | 当前学生课程成员 |
| API-HWK-08 | GET | `/api/v1/homeworks/{homeworkId}/my-submissions` | 当前学生 |
| API-HWK-09 | GET | `/api/v1/homeworks/{homeworkId}/submissions` | 课程管理者 |
| API-HWK-10 | GET | `/api/v1/submissions/{submissionId}` | 提交属主学生或课程管理者 |
| API-HWK-11 | GET | `/api/v1/submissions/{submissionId}/evaluation` | 可见性受控 |
| API-HWK-12 | POST | `/api/v1/submissions/{submissionId}/reevaluate` | 课程管理者 |
| API-HWK-13 | PUT | `/api/v1/submissions/{submissionId}/review` | 课程管理者 |
| API-HWK-14 | PUT | `/api/v1/homeworks/{homeworkId}/scores/publish` | 课程管理者 |
| API-HWK-15 | GET | `/api/v1/homeworks/{homeworkId}/statistics` | 课程管理者 |
| API-HWK-16 | PUT | `/api/v1/homeworks/{homeworkId}/questions` | 课程管理者 |
| API-HWK-17 | GET | `/api/v1/homeworks/{homeworkId}/questions` | 学生视图隐藏答案 |
| API-HWK-18 | PUT | `/api/v1/homeworks/{homeworkId}/test-cases` | 课程管理者 |
| API-HWK-19 | GET | `/api/v1/homeworks/{homeworkId}/test-cases` | 课程管理者 |
| API-HWK-20 | GET | `/api/v1/evaluations/{evaluationId}/logs` | 课程管理者 |
| API-HWK-21 | GET | `/api/v1/submissions/{submissionId}/review-logs` | 课程管理者 |
| API-HWK-22 | DELETE | `/api/v1/homeworks/{homeworkId}` | 课程管理者；仅 DRAFT |
| API-HWK-23 | POST/GET/DELETE | `/api/v1/homeworks/{homeworkId}/attachments[/{fileId}]` | 当前学生成员；仅自己的未绑定上传 |
| API-HWK-24 | GET | `/api/v1/homeworks/{homeworkId}/submissions/{submissionId}/attachment/download` | 提交学生或课程管理者；每次下载重新鉴权 |

补充语义：

- API-HWK-22 复用 `HomeworkResponse`，返回 `deleted=true` 与删除时间 `updatedAt`。无课程管理权限 `403 / HWK_4031`；不存在/已删除 `404 / HWK_4001`；一切非 DRAFT 状态 `409 / HWK_4095`。
- API-HWK-23 只接受一个 multipart `file`，限 10 MiB，按 `pdf, zip, docx, xlsx, pptx, txt, md, csv, png, jpg, jpeg` 校验扩展名、声明 MIME 与内容签名。DTO 只返回服务器 UUID、净化文件名、可信 MIME、大小、状态、上传时间、过期时间；绝不暴露 `storage_key`、服务器路径或原始 URL。API-HWK-24 每次请求重查身份、课程权限、作业/提交归属与精确附件绑定，发送含 `nosniff` 的安全内容头。

## UI 页面

| ID | 页面 | 必须处理 |
| --- | --- | --- |
| UI-HWK-01 | 作业中心 | 加载、空、失败、按角色过滤列表；学生见待交/已交/已关闭历史，教师见草稿/已发布/已关闭；仅 DRAFT 显示可确认删除，取消不发请求，pending 互斥，失败保留行，成功刷新并从空的末页回退；验证 1440/390 分辨率 |
| UI-HWK-02 | 教师创建/编辑 | 校验、草稿保存、更新失败 |
| UI-HWK-03 | 发布管理 | 发布/关闭、配置完整性错误、题目/用例链接 |
| UI-HWK-04 | 学生详情 | 不可见草稿、截止时间、提交规则、当前状态 |
| UI-HWK-05 | 学生提交 | 客观/文本/附件/代码输入、成功、待评测 |
| UI-HWK-06 | 提交历史 | 学生与教师的最新/有效标记 |
| UI-HWK-07 | 评测结果 | pending/running/success/failure 与可见性策略 |
| UI-HWK-08 | 教师评审 | 分数校验、评语、重新评测动作 |
| UI-HWK-09 | 统计 | 空统计、提交率、未交名单、成绩摘要 |

## 数据表

| ID | 表 | 关键契约 |
| --- | --- | --- |
| DB-HWK-01 | `t_hwk_homework` | 元数据、课程/章节、类型、状态、截止、提交规则、展示策略、`judge_config_id`；草稿删除仅原子更新父行 `is_deleted/updated_at`，条件 `id + DRAFT + is_deleted=FALSE` |
| DB-HWK-02 | `t_hwk_question` | 客观题题干/选项/答案/分值/顺序；答案对学生隐藏 |
| DB-HWK-03 | `t_hwk_test_case` | 代码 IO 用例、隐藏/公开标志、限制与分值权重 |
| DB-HWK-04 | `t_hwk_submission` | 学生答案/文件/代码、提交/评测/评审状态、各分值、`is_final` |
| DB-HWK-05 | `t_hwk_evaluation` | 每次客观/代码评测与复评记录 |
| DB-HWK-06 | `t_hwk_review_log` | 教师评审、改分、复评、发布审计 |
| DB-HWK-07 | `t_hwk_judge_config` | 代码评测配置；最终 DSD 通过 `t_hwk_homework.judge_config_id` 关联；用约束防止孤儿引用与一作业多配置歧义 |
| DB-HWK-08 | `t_hwk_submission_attachment` | 服务器 UUID、可空唯一 submission、作业/课程/上传者归属、私有 storage key、可信元数据、过期时间与 `UPLOADED/BOUND/DELETED`；`(homework_id,uploader_id,active_slot)` 保证仅一个活跃上传 |

索引应支撑课程、作业、学生、状态、截止与提交历史查询。

## 状态枚举

优先用代码中已有命名；否则对齐：

```text
HomeworkStatus: DRAFT, NOT_OPEN, PUBLISHED, CLOSED, SCORE_PUBLISHED, ARCHIVED
HomeworkType: OBJECTIVE, FILE, CODE
SubmitStatus: SUBMITTED, LATE, REJECTED
EvaluationStatus: NONE, PENDING, RUNNING, ACCEPTED, WRONG_ANSWER, COMPILE_ERROR,
  RUNTIME_ERROR, TIME_LIMIT_EXCEEDED, SYSTEM_ERROR
ReviewStatus: UNREVIEWED, REVIEWED, NEED_REVIEW
```

前端类型不得引入第二套不兼容状态词表。逻辑删除与 `HomeworkStatus` 正交，不加 `DELETED`；API-HWK-22 只接受 DRAFT，其余状态一律 HWK_4095。

## 错误码

`HWK_4001 HOMEWORK_NOT_FOUND`、`HWK_4002 HOMEWORK_NOT_PUBLISHED`、`HWK_4003 HOMEWORK_CLOSED`、`HWK_4004 DEADLINE_EXCEEDED`、`HWK_4005 SUBMIT_FORMAT_INVALID`、`HWK_4006 RESUBMIT_NOT_ALLOWED`、`HWK_4007 TEST_CASE_REQUIRED`、`HWK_4008 SCORE_OUT_OF_RANGE`、`HWK_4009 EVALUATION_TASK_FAILED`、`HWK_4010 EVALUATION_RESULT_NOT_VISIBLE`、`HWK_4031 COURSE_PERMISSION_DENIED`、`HWK_4042 ATTACHMENT_NOT_FOUND_OR_NOT_VISIBLE`、`HWK_4091 ATTACHMENT_EXPIRED`、`HWK_4092 ATTACHMENT_STATE_CONFLICT`、`HWK_4095 HOMEWORK_DELETE_STATE_CONFLICT`、`HWK_4131 ATTACHMENT_TOO_LARGE`、`HWK_4151 ATTACHMENT_TYPE_UNSUPPORTED`、`HWK_5001 INTERNAL_ERROR`、`HWK_5002 FILE_STORAGE_ERROR`。

## 草稿删除完整性

- 只逻辑删除 `t_hwk_homework` 父行；保留题目、测试用例、评测配置、提交、评测、评审日志与复评历史。
- 普通更新/发布/关闭/发分 SQL 不得设置 `is_deleted` 且必须带 `is_deleted=FALSE`，防止过期实体复活已删草稿。
- 原子删除影响零行时分类：不存在/已删除 → HWK_4001；存在但非 DRAFT → HWK_4095。
- 追溯：FR-HWK-01、UI-HWK-01、API-HWK-22、DB-HWK-01、TC-HWK-19。

## FILE 附件完整性

- FILE 提交恰含一个 API-HWK-23 UUID。在创建提交的同一事务内绑定 `UPLOADED -> BOUND`；绝不以客户端文件名、CSV 字段、路径或 URL 作为归属凭证。
- 未绑定上传 24 小时过期。顺序替换原子地把旧活跃上传标 `DELETED`；并发活跃槽冲突收敛为 `409/HWK_4092`，只留一个活跃记录/对象。
- 未绑定上传仅上传者可查看/删除。API-HWK-24 仅提交学生或 CRS 授权的课程管理者可用于精确的已绑定版本。
- 元数据持久化或流式传输失败时，尝试立即物理删除；删除失败则持久化延迟删除标记并重试。损坏标记不得饿死有效清理。
- 追溯：FR-HWK-02/03/05、API-HWK-23/24、DB-HWK-08、TC-HWK-20..27、MAN-HWK-012。

## 跨模块契约

- AUTH 提供当前用户、角色、登录态；HWK 仍自行校验作业/课程归属。
- CRS 是课程成员与教师/助教管理权限的权威。
- LAB 与 HWK 共享 `EvaluationTask`、`EvaluationResult`、`EvaluationStatus`、`Evaluator`、`SandboxExecutor`；不把评测器复制进 HWK。
- LRN 接收 `HOMEWORK_PUBLISHED`、`HOMEWORK_UPDATED`、`HOMEWORK_DEADLINE_APPROACHING`、`HOMEWORK_EVALUATION_FINISHED`、`HOMEWORK_SCORE_PUBLISHED`。
- GRD 需要来源成绩数据：课程 id、来源类型 `HWK`、作业 id、学生 id、得分、满分、状态、更新时间。

## 仓库结构

```text
backend/src/main/java/com/onlinejudge/hwk/{controller,domain,repository,service}
backend/src/test/java/com/onlinejudge/hwk
database/migrations
frontend/src/types/hwk.ts
frontend/src/api/hwk/homeworks.ts
frontend/src/views/hwk/*.vue
frontend/tests/unit/hwk/*.spec.ts
```

公共契约：`common.web.ApiResponse<T>`（success code `"0"`）、`common.web.PageResponse`、`common.security.CurrentUser`、`integration.course.CoursePermissionClient`、`common.event.NotificationEvent`/`NotificationEventPublisher`、`integration.grade.SourceGradeDTO`/`SourceGradeType`、`frontend/src/api/http.ts` 的 `request<T>`。

## 测试命名模式

用业务行为命名：`assistantCourseManagerCanCreateConfigureAndPublishHomework`、`studentListKeepsClosedHomeworkVisibleForHistory`、`studentCannotReadAnotherStudentsSubmission`、`codeHomeworkPublishRequiresAtLeastOneTestCase`、`scorePublishEmitsGradeSourceEvent`。避免 `testCreate`、`shouldWork`、`happyPath`。

## 已知评审失败（每次必查）

- 无效分支名，如 `hwk01` 而非 `feature/75-hwk-homework-create-publish`。
- 重命名/删除已开 PR 的远端 head 分支可能导致 PR 被关闭；需修分支名时，先准备合规分支与替换 PR 方案，等用户送审指示后再动 GitHub。
- 前端单测部分 mock `localStorage` 导致 `window.localStorage.setItem is not a function`。
- `App.vue` 路由改动破坏 GRD/LAB 入口，尤其 `/courses/{id}/grades?role=student`。
- Controller 层角色检查在 CRS 课程权限评估前拒绝助教。
- 学生作业列表只显示 `PUBLISHED`，隐藏 `CLOSED`/`SCORE_PUBLISHED`/`ARCHIVED` 历史与反馈。
- CODE 作业接受 `languageLimitJson` 之外的语言：API-HWK-07 必须在保存 `t_hwk_submission` 前以 `HWK_4005` 拒绝；学生详情可暴露语言白名单但隐藏私有评测限制；配置存在时学生 UI 应从同一白名单渲染/选择而非自由文本。
- CODE 作业语言 `<select>` 只渲染一个选项而 Vue model 为空：`languageLimitJson` 非空时 UI-HWK-05 必须把提交语言初始化为显示默认值；前端测试覆盖单选项白名单。
- DB-HWK-07 无约束实现，允许孤儿评测配置引用或一作业多配置。
- 迁移 SQL 过 H2 但在 MySQL 8.0 失败，尤其 `ALTER TABLE ... ADD CONSTRAINT IF NOT EXISTS`（MySQL 8.0 不支持）；用有序 `CREATE TABLE IF NOT EXISTS` 内联 `CONSTRAINT ... FOREIGN KEY ...` 或 MySQL 兼容的幂等策略。
- 前端 API 包装与联合类型偏离后端 DTO/枚举。
- 事件发布失败回滚核心 HWK 数据，而设计期望通知/成绩集成容错。
- FILE 附件工作遗漏 API-HWK-23/24、DB-HWK-08、归属/过期/绑定校验或 `storage_key` 保密。
- 把自动评审当批准，或在无 CI workflow 时声称"CI 通过"。
