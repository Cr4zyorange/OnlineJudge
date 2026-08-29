# D4 跨服务共享契约（四服务）

> **唯一正本。** 本文件是 #310（跨服务契约冻结）的契约正本，与 #279 四服务总图共用同一契约口径。AUTH、CRS、LRN、LAB、HWK、GRD 的任何跨服务同步 API、异步事件/消息、内部调用身份和失败处理，只允许消费本文件定义的契约；任何服务需要其他服务数据时不得直接读取对方表、Repository、Mapper 或实现类。
>
> **事实基线：** `origin/dev@1f7c890`（2026-08-29）。本任务冻结接口并补齐失败处理，不改变既有 REST API 路径、错误码和状态枚举语义；所有"变化"均显式标注版本。

## 1. 使用规则

| 规则 | 规范 |
| --- | --- |
| 契约边界 | 本文件只冻结服务之间的数据契约（同步 API、事件/消息、内部调用身份）；各服务内部实现不属于本文件范围，但消费方不得把内部实现当作降级路径。 |
| 版本 | 每条契约标注 `v1`；契约类型（`SourceGradeDTO`、`NotificationEvent`、`EvaluationTask`、`EvaluationResult`、`CoursePermissionClient`、`CurrentUser`）在代码中以 `VERSION = "v1"` 常量冻结。 |
| 消费方式 | 消费方只依赖 `com.onlinejudge.integration.*` 与 `com.onlinejudge.common.*` 契约类型；禁止 import 生产者的 `mapper`、`repository`、`domain` 或 Service 实现。 |
| 失败处理 | 每条契约必须同时定义成功、超时、拒绝、重复、下游不可用五种行为；"稍后重试"不得作为唯一失败策略，必须落到有界重试、告警丢弃、失败关闭或原子中止之一。 |
| 变更流程 | 修改公共 API、错误码、状态枚举、`SourceGradeDTO`、通知事件或 `CoursePermissionClient` 时必须先升版本、改测试、改本文件，再改实现。 |

## 2. 四服务总图与服务归属

本文件采用与 #279 四服务总图一致的服务划分（按《软件详细设计说明书》2.4 的层归属将六个模块归并为四个服务；网关与评测执行器为基础设施，不参与"四服务"计数）：

| 服务 | 模块 | 拥有的数据域 | 对外契约 |
| --- | --- | --- | --- |
| 基础服务 | AUTH、CRS | 用户/会话/角色/权限；课程/章节/成员/公告 | C-01、C-02、C-03、C-04 |
| 评测服务 | LAB、HWK | 实验/作业、提交、评测、文件资产、评分 | C-06（来源成绩）、C-07（评测/事件） |
| 成绩服务 | GRD | 成绩项、成绩记录、发布记录、复核 | C-06（消费来源成绩） |
| 通知学习服务 | LRN | 学习任务、进度、通知、提醒规则 | C-04、C-05（事件接收与通知落库） |

> 说明：若 #279 最终对服务分组有不同界定，只影响上表"服务"列与流水线职责列；各条契约的生产者/消费者按模块给出，仍然成立。

依赖方向固定为：业务服务 → 基础服务（身份/课程）；评测服务 → 通知学习服务（事件）；成绩服务 → 评测服务（来源成绩）→ 通知学习服务（成绩事件）；反向不得建立数据依赖。

## 3. 契约清单汇总

| 契约 | 名称 | 版本 | 载体 | 生产者 | 消费者 |
| --- | --- | --- | --- | --- | --- |
| C-01 | AUTH 认证上下文 | v1 | 网关请求头 + 进程内 `CurrentUserProvider` | AUTH/网关 | 全部业务服务 |
| C-02 | 网关鉴权信息传递与内部调用身份 | v1 | HTTP 头白名单 + `X-Internal-Token` | 网关、LRN | 全部业务服务 |
| C-03 | `CoursePermissionClient` 课程权限 | v1 | 进程内 SPI（Client/Provider） | CRS | LAB/HWK/GRD/LRN |
| C-04 | CRS 公告 → LRN 学习任务/通知 | v1 | `NotificationEvent`（`TEACHER_ANNOUNCEMENT`） | CRS | LRN |
| C-05 | `NotificationEventPublisher` 通知事件 | v1 | `NotificationEvent` + 事件接收 API | CRS/LAB/HWK/GRD | LRN |
| C-06 | `SourceGradeClient/Provider/DTO` 来源成绩 | v1 | 进程内 SPI + `SourceGradeDTO` | LAB/HWK | GRD |
| C-07 | 评测任务、状态、文件资产与完成事件 | v1 | `EvaluationTask/Result/Status` + `FileStorageService` + 完成事件 | LAB/HWK、EVAL | LAB/HWK、LRN、GRD |

## C-01 AUTH 认证上下文契约（v1）

| 属性 | 定义 |
| --- | --- |
| 路径/事件名 | 网关注入请求头：`X-User-Id`、`X-Username`、`X-User-Role`、`X-Permissions`；进程内由 `com.onlinejudge.common.security.HeaderCurrentUserProvider` 解析为 `CurrentUser`。 |
| 生产者 | AUTH 服务（Bearer 会话校验、角色/权限装载）与网关（注入可信头）。 |
| 消费者 | CRS、LRN、LAB、HWK、GRD 全部业务服务（Controller 声明 `CurrentUser` 参数）。 |
| 请求/载荷 | 请求头 → `CurrentUser(id, username, role, permissions)`；`X-Permissions` 为逗号分隔权限码，如 `course:manage,grade:manage`；角色大小写不敏感，统一大写。 |
| 响应 | 成功：`CurrentUser` 记录；缺头/非法 ID/空角色：无身份；`requireCurrentUser()` 抛出 `AuthenticationRequiredException`。 |
| 版本 | `v1`（`CurrentUser` 四个字段冻结）。 |
| 鉴权 | 业务服务只信任网关注入的头；`onlinejudge.auth.allow-header-auth=false` 时外部直接伪造 `X-User-*` 一律按未认证拒绝（fail-closed）。 |
| 错误码 | `ERR-AUTH-01` 登录失败；`ERR-AUTH-03` 禁用/冻结/锁定；`ERR-AUTH-04` 未认证（HTTP 401）；`ERR-AUTH-05` 权限不足（HTTP 403）。 |
| 超时 | 本地头解析无网络超时；AUTH 会话校验超时按未认证处理（失败关闭）。 |
| 幂等键 | 认证为只读校验，无写幂等键。 |
| 重试 | 401/403 不自动重试；前端统一跳转登录/无权限页。 |
| 补偿/降级 | AUTH 不可用时业务服务一律拒绝请求，不得默认放行未授权访问。 |
| 日志 | 记录 userId、角色、拒绝原因与请求路径；不记录令牌、密码或完整权限审计明细以外的敏感字段。 |
| 兼容策略 | 字段新增必须先发 `CurrentUser` v2 或新增头 `X-User-V2-*`；删除字段需双版本过渡。 |

## C-02 网关鉴权信息传递与内部调用身份契约（v1）

| 属性 | 定义 |
| --- | --- |
| 路径/事件名 | 外部：Nginx/网关 `/api/v1` → `backend:8080`；内部事件投递：`POST /api/v1/notifications/events`（`WebMvcConfig` 白名单排除登录/注册/探活/事件接收路径）。 |
| 生产者 | 网关（身份头注入与外部同名头剥离）；LRN（内部令牌校验）。 |
| 消费者 | 全部业务服务（身份消费）；CRS/LAB/HWK/GRD（内部事件调用方，需持有 `X-Internal-Token`）。 |
| 请求/载荷 | 头白名单：`X-User-Id`、`X-Username`、`X-User-Role`、`X-Permissions`、`X-Course-Ids`、`X-Manageable-Course-Ids`、`X-Course-Student-Ids`、`X-Course-Teacher-Ids`、`X-Internal-Token`。 |
| 响应 | 身份上下文（成功）或 401/403；事件投递返回 `NotificationEventResult`（`createdIds`、`createdCount`）。 |
| 版本 | `v1`（头名与令牌键名冻结）。 |
| 鉴权 | 网关剥离客户端同名头后注入可信身份头；内部调用以 `X-Internal-Token` 匹配 `ONLINEJUDGE_NOTIFICATIONS_INTERNAL_TOKEN`，不匹配返回 403。 |
| 错误码 | `ERR-AUTH-04`（401）、`ERR-AUTH-05`（403）、`LRN-403-04`（事件令牌拒绝）、`LRN-400-04`（事件载荷非法）。 |
| 超时 | 网关代理超时由 Nginx/网关配置（上传链路 55 MiB 上限内）；内部事件 HTTP 调用默认有界 3s。 |
| 幂等键 | 事件幂等键由业务方生成（见 C-05 目录），重放同一键不得重复建通知。 |
| 重试 | 内部事件调用按 C-05 有界重试/告警丢弃；401/403 不重试。 |
| 补偿/降级 | 网关不可用时前端展示 502/504，不旁路直连；内部令牌缺失时事件接口按拒绝处理。 |
| 日志 | 记录来源 IP、路径、状态码与遮蔽后的令牌存在性；绝不记录令牌值。 |
| 兼容策略 | 新增身份头必须以 `X-User-` 前缀扩展并在本表登记；令牌键名变更需升级 v2。 |

## C-03 CoursePermissionClient 课程权限契约（v1）

| 属性 | 定义 |
| --- | --- |
| 路径/事件名 | 进程内 SPI：消费端 `com.onlinejudge.integration.course.CoursePermissionClient`；生产端 `CoursePermissionProvider`；默认实现 `DefaultCoursePermissionClient`（`VERSION = "v1"`）。 |
| 生产者 | CRS（`CrsCoursePermissionProvider`，在 CRS 自己的 `CourseRepository` 之上实现）。 |
| 消费者 | LAB、HWK、GRD、LRN 及 CRS 自身（课程存在性、成员关系、管理权限、名单）。 |
| 请求/载荷 | `courseExists(courseId)`；`canViewCourse/isCourseMember(courseId, userId)`；`canManageCourse/canManageCourseGrade(courseId, userId)`；`listCourseStudentIds/listCourseTeacherIds(courseId)`。 |
| 响应 | 布尔授权结果；名单返回去重 `List<Long>`；无效 ID（≤0）直接拒绝。 |
| 版本 | `v1`（接口 + 版本常量；新方法必须以 default 追加）。 |
| 鉴权 | 调用方身份由当前请求 `CurrentUser` 提供；`ADMIN` 通配与 `X-Course-*` 头仅在 `onlinejudge.auth.allow-header-auth=true` 的 DEV 模式生效。 |
| 错误码 | 业务层：`NO_COURSE_MANAGE_PERMISSION`（403）、`NO_COURSE_MEMBERSHIP`（403）、`COURSE_NOT_FOUND`（404）。 |
| 超时 | `onlinejudge.integration.course.timeout-ms`（默认 1000ms）有界预算；超时按拒绝/空名单处理（fail-closed）。 |
| 幂等键 | 只读校验天然幂等；重复调用结果稳定。 |
| 重试 | 不自动重试；超时/下游失败即拒绝，由调用方决定是否向用户提示重试。 |
| 补偿/降级 | 下游不可用 → 拒绝/空名单；**禁止**回退直连 CRS 表、`CourseRepository` 或 CRS Service 实现。 |
| 日志 | 超时与下游失败记录 operation、courseId、userId 与失败根因；正常校验不落日志。 |
| 兼容策略 | 新增校验方法用 default 保持兼容；破坏性变更（删除方法/改语义）升级 v2 并同步所有消费者。 |

## C-04 CRS 公告 → LRN 学习任务/通知协作契约（v1）

| 属性 | 定义 |
| --- | --- |
| 路径/事件名 | 事件类型 `TEACHER_ANNOUNCEMENT`；发布入口 `AnnouncementService.create`；投递经 `NotificationEventPublisher` → LRN。 |
| 生产者 | CRS（公告创建成功后发布）。 |
| 消费者 | LRN（`PersistentNotificationEventPublisher` → `NotificationService.createNotifications`）。 |
| 请求/载荷 | `NotificationEvent` v1：`courseId`、`recipientUserIds`=课程活跃学生、`title/content`=公告内容（已净化）、`targetType=CRS_ANNOUNCEMENT`、`targetId=announcementId`、`linkUrl=/courses/{courseId}`。 |
| 响应 | 尽力而为：公告事务不因通知失败回滚；LRN 落库成功即可在通知中心查询。 |
| 版本 | `v1`（事件字段随 `NotificationEvent` 冻结）。 |
| 鉴权 | 事件经内部通知通道，携带 `X-Internal-Token`；对外不可直接调用公告事件。 |
| 错误码 | 无来源业务错误码；LRN 载荷校验失败 `LRN-400-04`。 |
| 超时 | 有界通知投递执行器；执行器饱和即拒绝（拒绝=告警丢弃，见重试）。 |
| 幂等键 | `CRS_ANNOUNCEMENT_{announcementId}`；重复投递只生成一条通知/用户。 |
| 重试 | 当前内存有界队列不做持久重试：调度拒绝或落库失败 → 记录告警并丢弃（有界丢失语义，明确不等于"稍后重试"）；恢复途径为公告列表页兜底展示。 |
| 补偿/降级 | 公告发布成功即完成来源业务；通知缺失可接受（best-effort），LRN 通过公告列表页保证用户可见性。 |
| 日志 | 记录 targetType/targetId、投递结果、失败原因与告警级别。 |
| 兼容策略 | 新公告字段需同步 `AnnouncementResponse` 与事件载荷；事件结构变化升级 `NotificationEvent` v2。 |

## C-05 NotificationEventPublisher 通知事件契约（v1）

| 属性 | 定义 |
| --- | --- |
| 路径/事件名 | 进程内 `com.onlinejudge.common.event.NotificationEventPublisher`（`publish` / `publishRequired`）；对外接收 `POST /api/v1/notifications/events`；事件目录见下。 |
| 生产者 | CRS、LAB、HWK、GRD（各业务完成点）。 |
| 消费者 | LRN（`NotificationService`：校验、幂等、落库、课程成员过滤）。 |
| 请求/载荷 | `NotificationEvent` v1：`idempotencyKey`、`type`、`courseId`、`recipientUserIds`、`title`、`content`、`targetType`、`targetId`、`linkUrl`、`occurredAt`。 |
| 响应 | `publish`：尽力而为，不抛错污染来源事务；`publishRequired`：同来源事务同步落库，失败向上抛出并回滚来源。 |
| 版本 | `v1`（`NotificationEvent.VERSION`）。 |
| 鉴权 | 内部令牌 `X-Internal-Token`；`publishRequired` 调用方必须在来源事务内完成身份校验。 |
| 错误码 | `LRN-403-04`（令牌拒绝）、`LRN-400-04`（载荷非法）；`HOMEWORK_PUBLISHED` 必达失败 → `503/HWK_5003`。 |
| 超时 | 投递走专用有界执行器；执行器饱和按拒绝处理，不无限排队。 |
| 幂等键 | 目录见下；同一键+接收人只生成一条通知。 |
| 重试 | `publishRequired` 同事务失败即回滚（无异步重试）；`publish` 调度拒绝或落库失败 → 告警丢弃（有界内存队列，进程退出会丢，属已定义降级）；不得以未定时的重试承诺代替实现。 |
| 补偿/降级 | 来源事务提交前不落通知（after-commit）；回滚不产生孤儿通知；通知缺失由业务页面兜底，不反向改写来源数据。 |
| 日志 | 记录事件类型、targetType/targetId、接收人数、投递结果；失败记录异常栈。 |
| 兼容策略 | 新增事件类型必须先进本目录；删除/改事件字段升级 `NotificationEvent` v2 并同步 LRN 校验。 |

**事件目录（v1）：**

| 事件类型 | 生产者 | 语义 | 幂等键 | 必达 |
| --- | --- | --- | --- | --- |
| `TEACHER_ANNOUNCEMENT` | CRS | 公告发布 | `CRS_ANNOUNCEMENT_{id}` | 否 |
| `LAB_EXPERIMENT_PUBLISHED` | LAB | 实验发布 | `lab-published-{id}-{updatedAt}` | 否 |
| `EXPERIMENT_SCORE_PUBLISHED` | LAB | 实验成绩发布 | `lab-score-published-{id}-{publishedAt}` | 否 |
| `LAB_SUBMISSION_SCORED` | LAB | 实验评分完成 | `lab-score-{submissionId}-{occurredAt}` | 否 |
| `LAB_EVALUATION_COMPLETED` | LAB | 评测完成（含 SYSTEM_ERROR） | `LAB_EVALUATION_{submissionId}` | 否 |
| `HOMEWORK_PUBLISHED` | HWK | 作业发布 | `homework-published-{id}-{updatedAt}` | 是 |
| `HOMEWORK_SCORE_PUBLISHED` | HWK | 作业成绩发布 | `homework-score-published-{id}-{occurredAt}` | 否 |
| `HWK_EVALUATION_COMPLETED` | HWK | 评测完成（含 SYSTEM_ERROR） | `HWK_EVALUATION_{submissionId}` | 否 |
| `GRADE_PUBLISHED` | GRD | 成绩发布 | `GRD:GRADE_PUBLISHED:PUBLISH:{publishRecordId}` | 否 |
| `GRADE_CHANGED` | GRD | 成绩变更 | `GRD:GRADE_CHANGED:LOG:{changeLogId}` | 否 |
| `GRADE_REVIEW_REQUESTED` | GRD | 复核申请 | `GRD:GRADE_REVIEW_REQUESTED:REQUEST:{requestId}` | 否 |
| `GRADE_REVIEW_PROCESSED` | GRD | 复核处理完成 | `GRD:GRADE_REVIEW_PROCESSED:REQUEST:{requestId}` | 否 |

## C-06 SourceGradeClient/Provider/DTO 来源成绩契约（v1）

| 属性 | 定义 |
| --- | --- |
| 路径/事件名 | 进程内 SPI：`integration.grade.SourceGradeClient`（消费端）、`SourceGradeProvider`（生产端）、`DefaultSourceGradeClient`（路由+超时）。 |
| 生产者 | LAB（`LabSourceGradeService`）、HWK（`HomeworkSourceGradeClient`），只读各自模块的成绩数据。 |
| 消费者 | GRD（成绩同步/汇总，只允许消费 `SourceGradeDTO`）。 |
| 请求/载荷 | `findSourceGrades(courseId, sourceType, sourceId)`；`SourceGradeType ∈ {LAB, HWK}`。 |
| 响应 | `List<SourceGradeDTO>`；`SourceGradeDTO` v1 字段：`courseId`、`sourceType`、`sourceId`、`studentId`、`score`、`fullScore`、`status`、`updatedAt`；来源不存在/跨课程 → `Optional.empty`（MISSING）；未发布/无成绩 → 空列表（EMPTY）；`status ∈ {SCORED, UNGRADED}`。 |
| 版本 | `v1`（`SourceGradeDTO.VERSION`；字段集合由契约测试冻结）。 |
| 鉴权 | 来源查询由 GRD 内部调用身份发起；对外成绩只经 GRD 发布接口暴露。 |
| 错误码 | 无 HTTP 错误码；下游不可用/超时抛 `SourceGradeUnavailableException`，GRD 原子中止本次同步，不生成部分结果。 |
| 超时 | `onlinejudge.integration.grade.timeout-ms`（默认 1000ms）；超时按下游失败处理（中止，不静默 MISSING）。 |
| 幂等键 | 只读查询天然幂等；重复查询结果稳定。 |
| 重试 | 不自动重试；GRD 侧由同步任务重跑（有界、人工可触发）。 |
| 补偿/降级 | 禁止 GRD 直连 LAB/HWK 表或 Repository；来源不可用时保留既有成绩快照，不写不可解释总评。 |
| 日志 | 记录 courseId/sourceType/sourceId、返回条数、耗时；异常记录根因（不记录成绩明细于 WARN 级别以上）。 |
| 兼容策略 | 字段新增 → `SourceGradeDTO` v2（v1 保留）；`status` 枚举新增值需显式版本化并同步 GRD 校验。 |

## C-07 评测任务、状态、文件资产与完成事件契约（v1）

| 属性 | 定义 |
| --- | --- |
| 路径/事件名 | 进程内：`EvaluationTask`/`EvaluationResult`/`Evaluator`/`SandboxExecutor`；文件资产 `FileStorageService`/`StoredFile`；完成事件 `LAB_EVALUATION_COMPLETED`、`HWK_EVALUATION_COMPLETED`。 |
| 生产者 | LAB/HWK（评测编排与资产归属）、EVAL 评测执行器（Fake/Docker 沙箱）。 |
| 消费者 | LAB/HWK（状态回写）、LRN（完成事件）、GRD（仅消费已发布来源成绩，不读评测明细）。 |
| 请求/载荷 | `EvaluationTask` v1：`taskId`、`module`、`courseId`、`sourceId`、`submissionId`、`studentId`、`language`、`sourceCode`、`options`、`submittedAt`。 |
| 响应 | `EvaluationResult` v1：`taskId`、`status`、`score`、`message`、`caseResults`、`finishedAt`；`EvaluationStatus` v1 九值：`NONE/PENDING/RUNNING/ACCEPTED/WRONG_ANSWER/COMPILE_ERROR/RUNTIME_ERROR/TIME_LIMIT_EXCEEDED/SYSTEM_ERROR`。 |
| 版本 | `v1`（`EvaluationTask.VERSION`、`EvaluationResult.VERSION`；状态枚举由契约测试冻结）。 |
| 鉴权 | 评测任务只由 LAB/HWK 内部创建；学生只能查看允许公开的评测结果。 |
| 错误码 | 评测失败统一收敛为 `SYSTEM_ERROR`（提交保留）；超时用例 → `TIME_LIMIT_EXCEEDED`；资产下载错误沿用 `LAB-404-*`/`LAB-409-03`/`LAB-500-05`。 |
| 超时 | 基础规模用例 60s 内返回终态或失败；沙箱超时映射为 `TIME_LIMIT_EXCEEDED`/`SYSTEM_ERROR`，不阻塞提交接口。 |
| 幂等键 | 评测任务 `taskId={submissionId}-{testcaseId}`；`claimPending` 保证同一评测只被一个 worker 领取；完成事件幂等键 `LAB_EVALUATION_{submissionId}`/`HWK_EVALUATION_{submissionId}`。 |
| 重试 | 评测 worker 失败不无限重试：标记 `SYSTEM_ERROR` 并由教师重评触发新评测记录（保留旧记录）。 |
| 补偿/降级 | 文件资产：物理存储成功而业务事务回滚 → 补偿删除（删除失败入持久 journal，定时收敛）；评测服务异常时提交与资产保留，不产生孤儿结果。 |
| 日志 | 记录 taskId、submissionId、状态、耗时与失败原因；不记录源码、storageKey、本地路径或未脱敏日志。 |
| 兼容策略 | 新增状态枚举值、评测字段或资产语义 → 升级 v2 并同步 LRN/GRD 消费与测试。 |

## 失败处理矩阵

每条跨服务调用在五种场景下的行为定义如下（即"不能只写稍后重试"的落地口径）：

| 契约 | 成功 | 超时 | 拒绝 | 重复 | 下游不可用 |
| --- | --- | --- | --- | --- | --- |
| C-01 认证上下文 | 返回 `CurrentUser` | N/A（本地解析）；AUTH 校验超时按 401 | 缺头/非法 → 401 `ERR-AUTH-04`；无权限 → 403 `ERR-AUTH-05` | 重复解析结果稳定 | AUTH 不可用 → 一律 401，失败关闭 |
| C-02 网关/内部身份 | 身份上下文或事件落库成功 | 网关代理超时 → 502/504；内部调用默认 3s | 令牌不匹配 → 403 `LRN-403-04` | 事件幂等键重放不重复建通知 | 网关不可用 → 502/504，不旁路 |
| C-03 课程权限 | 布尔/名单返回 | 1000ms 预算 → 拒绝/空名单 | 非成员/非管理者 → 业务 403 | 只读幂等，结果稳定 | 拒绝/空名单，禁止直连 CRS 表 |
| C-04 公告→LRN | 公告落库 + 通知创建 | 执行器饱和 → 告警丢弃（有界） | 无效载荷 → `LRN-400-04` | `CRS_ANNOUNCEMENT_{id}` 去重 | 通知缺失由公告页兜底，不回滚公告 |
| C-05 通知事件 | 通知落库（after-commit） | 有界队列饱和 → 拒绝告警 | 令牌/载荷拒绝 | 幂等键+接收人去重 | `publish` 告警丢弃；`publishRequired` 回滚来源 |
| C-06 来源成绩 | 返回 DTO 列表 | 超时 → `SourceGradeUnavailableException`，GRD 中止 | MISSING/EMPTY 按语义返回 | 只读幂等 | 抛异常，GRD 原子中止，不写部分结果 |
| C-07 评测/资产/事件 | 终态回写 + 完成事件 | 60s 用例上限 → 超时/系统错误 | 越权/非法提交 → 业务错误码 | `claimPending` + 事件幂等键 | `SYSTEM_ERROR` 保留提交，补偿删除/journal 收敛 |

## 禁止项清单

- 禁止任何消费方 import 生产者模块的 `mapper`、`repository`、`domain` 或 Service 实现作为降级路径（由 `CrossServiceContractRegistryTest` 强制）。
- 禁止 `integration.course` 出现 `com.onlinejudge.crs.mapper` / `com.onlinejudge.crs.domain` 引用。
- 禁止 GRD 直连 LAB/HWK 表或 Repository 读取来源成绩。
- 禁止业务服务信任前端/外部传入的 `userId`、`courseId` 权限头绕过 C-01/C-03。
- 禁止以孤立"稍后重试"作为失败处理；必须落到本文件定义的超时/拒绝/重试/补偿行为。
- 禁止提交密码、Token、真实 Secret、私有镜像凭据或未脱敏日志；`X-Internal-Token` 只经 Secret 注入。

## 测试矩阵与流水线

契约测试位于 `backend/src/test/java/com/onlinejudge/contracts/`，按消费者/生产者两侧独立可运行：

| 套件 | 覆盖 | 运行方式（独立） |
| --- | --- | --- |
| 结构冻结 | `CrossServiceContractRegistryTest`：契约类型、版本常量、状态枚举、禁止 import | `mvn test -Dtest=CrossServiceContractRegistryTest` |
| 文档完整性 | `ContractDocumentationCompletenessTest`：14 项属性、失败矩阵、consumer/producer 运行口径 | `mvn test -Dtest=ContractDocumentationCompletenessTest` |
| 消费端 | `CommonInfrastructureContractTest` + 结构/文档冻结 + `CoursePermissionConsumerContractTest` + `SourceGradeConsumerContractTest` | `bash scripts/ci/contract-verify.sh <checkout> consumer` |
| 生产端 | `CommonInfrastructureContractTest` + 结构/文档冻结 + `CoursePermissionProducerContractTest` + `SourceGradeProducerContractTest` + `EvaluationCompletionEventContractTest` + `AuthContextContractTest` | `bash scripts/ci/contract-verify.sh <checkout> producer` |
| 全量 | 上述全部 | `bash scripts/ci/contract-verify.sh <checkout> all` 或不传 side |

`contract-verify.sh` 同时保留既有 shell 契约门禁；`ci.yml` 的 `contracts-gate` 以两个独立步骤顺序执行 consumer/producer 两侧并分别归档证据（`ci-artifacts/contracts-gate/consumer`、`/producer` 与 surefire `contract-consumer`/`contract-producer`），任一侧失败即阻断 delivery。

## 本文件核对记录

| 项目 | 环境 | 基线 SHA | 命令 | 原始结果 |
| --- | --- | --- | --- | --- |
| 文档空白与格式 | Windows 11 PowerShell | `1f7c890` | `git diff --check` | `exit 0`；stdout 仅含本机 LF/CRLF 提示，无空白错误 |
| RED：契约测试编译失败 | 同上 | `1f7c890` | `mvn -B -ntp test -Dtest="com.onlinejudge.contracts.*"` | 41 个编译错误（缺失 `CoursePermissionProvider`/`DefaultCoursePermissionClient`/`CrsCoursePermissionProvider`/`SourceGradeClient(Duration)`），日志 `output/issue-310/red-contract-suite-01-compile.log` |
| RED：断言级失败 | 同上 | `1f7c890` | `mvn -B -ntp test -Dtest="com.onlinejudge.contracts.**"` | 35 测试中 2 失败 + 2 错误（契约正本缺失），日志 `output/issue-310/red-contract-suite-02-assertion.log` |
| GREEN：消费端契约套件独立运行 | Windows 11 + Java 21 | 被测 `9758511` | `mvn -B -ntp test -Dtest="CommonInfrastructureContractTest,CrossServiceContractRegistryTest,ContractDocumentationCompletenessTest,CoursePermissionConsumerContractTest,SourceGradeConsumerContractTest"` | `exit 0`；20 total / 0 failures / 0 errors / 0 skipped；日志 `output/issue-310/green-contract-consumer.log` |
| GREEN：生产端契约套件独立运行 | 同上 | 被测 `9758511` | `mvn -B -ntp test -Dtest="CommonInfrastructureContractTest,CrossServiceContractRegistryTest,ContractDocumentationCompletenessTest,CoursePermissionProducerContractTest,SourceGradeProducerContractTest,EvaluationCompletionEventContractTest,AuthContextContractTest"` | `exit 0`；27 total / 0 failures / 0 errors / 0 skipped；日志 `output/issue-310/green-contract-producer.log` |
| GREEN：后端全量回归 | 同上 | 被测 `9758511` | `mvn -B -ntp test` | `exit 0`；443 total / 0 failures / 0 errors / 7 skipped；日志 `output/issue-310/backend-full-regression-final.log` |
| CI 流水线 | GitHub Actions（待 PR 触发） | 被测 `9758511` | `contracts-gate` job 顺序执行 consumer/producer 两侧 | 本机已按同一条命令与套件验证；Actions 链接待 PR 推送后回填 |
