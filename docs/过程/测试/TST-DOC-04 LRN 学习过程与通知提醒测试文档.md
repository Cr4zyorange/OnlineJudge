# TST-DOC-04 LRN 学习过程与通知提醒测试文档

| 文档编号 | TST-DOC-04 |
| --- | --- |
| 文档名称 | LRN 学习过程与通知提醒测试文档 |
| 项目名称 | 在线教学与实训平台 |
| 所属阶段 | 系统测试与验收测试 |
| 报告版本 | V1.0 |
| 编写日期 | 2026-06-10 |
| 编写人 | LRN 模块负责人 |
| 对应 issue | #155 TST-DOC-04 LRN 学习过程与通知提醒测试文档编写 |
| 测试范围 | 学习任务中心、学习进度、学习行为仪表盘、通知分类推送、通知已读/删除、提醒规则与通知偏好 |
| 测试结论 | LRN 后端目标测试和前端目标测试已通过；真实浏览器端到端、跨模块生产事件投递和通知推送时延需测试负责人统一联调确认 |

## 1 文档控制

### 1.1 修订记录

| 版本 | 日期 | 修订人 | 修订说明 |
| --- | --- | --- | --- |
| V1.0 | 2026-06-10 | LRN 模块负责人 | 按 #155 和 TST-DOC-01 统一规范整理 LRN 测试范围、用例、自动化覆盖、执行日志、手工验收点和残余风险 |

### 1.2 审批记录

| 角色 | 姓名/负责人 | 审批意见 | 日期 |
| --- | --- | --- | --- |
| 项目负责人 | 待填写 | 待审批 | 2026-06-10 |
| 测试负责人 | @MontesquieuE | 待整合确认 | 2026-06-10 |
| LRN 模块负责人 | LRN 负责人 | 待确认 | 2026-06-10 |

## 2 测试概述

本文件用于记录 LRN 学习过程与通知提醒模块在当前版本下的测试依据、测试环境、测试数据、测试用例、执行结果、手工验收清单、缺陷风险和验收结论。覆盖范围对齐 `FR-LN-01 ~ FR-LN-06`、`NFR-LN-01 ~ NFR-LN-05`、`UI-LRN-01 ~ UI-LRN-05`、`API-LRN-01 ~ API-LRN-11`、`DB-LRN-01 ~ DB-LRN-07`、`TC-LN-01 ~ TC-LN-06` 与 `TC-LN-N01 ~ TC-LN-N05`。

当前已执行 LRN 后端 Spring Boot 目标测试、LRN 前端 Vue/Vitest 目标测试和部分 CRS/LAB/HWK/GRD 跨模块触发测试。自动化覆盖了任务聚合与分页、学习进度保存和断点续传、教师课程进度聚合、学习行为统计和上报限流、通知分类生成和当前用户隔离、已读/删除状态日志、提醒规则保存、截止提醒扫描、失败记录与关键迁移约束。真实浏览器完整端到端、WebSocket 或轮询触达时延、生产跨模块事件链路仍列为手工或联调验收项。

## 3 测试依据

| 序号 | 文档/代码依据 | 用途 |
| --- | --- | --- |
| 1 | `docs/开发/LRN-学习过程与通知提醒模块开发流程.md` | LRN 主流程、开发顺序、P0 闭环、权限和跨模块事件要求 |
| 2 | `docs/最终提交/软件需求规格说明书.md` | FR-LN、NFR-LN 需求和验收来源 |
| 3 | `docs/最终提交/软件概要设计说明书.md` | LRN 页面、接口、数据模型、跨模块依赖和追踪关系来源 |
| 4 | `docs/最终提交/软件详细设计说明书.md` | UI、API、数据库、服务流程、测试编号和追踪矩阵来源 |
| 5 | `docs/过程/需求/学习过程与通知提醒模块（前端总设计师负责）.md` | LRN 过程需求、非功能要求和异常边界补充 |
| 6 | `docs/过程/概要/学习过程与通知提醒 - 概要设计.md` | LRN 概要流程、通知链路和提醒规则补充 |
| 7 | `docs/过程/详细设计/LRN-学习过程与通知提醒-详细设计提交稿.md` | LRN 详细接口、数据库表和追踪矩阵补充 |
| 8 | `backend/src/test/java/com/onlinejudge/lrn` | LRN 后端控制器、服务、迁移和异常自动化测试 |
| 9 | `backend/src/test/java/com/onlinejudge/integration/GrdLrnIntegrationTest.java`、`IntDemoDataInitializerTest.java` | GRD/LRN 通知事件和 INT 演示闭环联调样本 |
| 10 | `frontend/tests/unit/lrn` | LRN 前端页面和 API wrapper 单元测试 |
| 11 | `frontend/tests/unit/CourseManagementView.spec.ts`、`frontend/tests/unit/lab/LabStudentView.spec.ts`、`frontend/tests/unit/hwk/HomeworkStudentView.spec.ts` | CRS/LAB/HWK 触发 LRN 进度和行为记录的前端联动测试 |
| 12 | `database/migrations/20260530_01_create_lrn_learning_task.sql`、`20260531_01_create_lrn_learning_progress.sql`、`20260602_01_create_lrn_learning_record.sql`、`20260603_01_create_lrn_notification.sql`、`20260605_01_create_lrn_reminder_rule.sql` | LRN 数据表和迁移约束依据 |

## 4 测试范围

### 4.1 功能与非功能范围

| 编号 | 测试对象 | 主要验证点 | 当前覆盖状态 |
| --- | --- | --- | --- |
| FR-LN-01 | 学习任务中心展示 | 聚合课程资源、实验、作业任务；按登录用户、课程成员、类型、状态、截止时间和分页返回 | 后端和前端自动化已覆盖；真实浏览器入口待手工确认 |
| FR-LN-02 | 学习进度记录与展示 | 课程级、章节级进度；继续学习；断点续传；教师查看所教课程聚合进度 | 后端和前端自动化已覆盖；跨页面真实恢复待手工确认 |
| FR-LN-03 | 学习行为跟踪 | 学习时长、访问次数、任务完成统计；近 7 天趋势；离线队列回放；上报限流 | 后端和前端自动化已覆盖 |
| FR-LN-04 | 通知分类推送与展示 | 任务、成绩、公告、系统通知分类；内部事件鉴权；幂等生成；列表筛选分页 | 后端和前端自动化已覆盖；真实跨模块投递待联调确认 |
| FR-LN-05 | 通知触达与状态管理 | 未读、已读、删除、批量已读、通知跳转、状态日志和用户隔离 | 后端和前端自动化已覆盖；真实推送时延待联调确认 |
| FR-LN-06 | 定时提醒与规则配置 | 提醒规则、通知偏好、任务截止提醒、重复提醒防护、失败记录 | 后端和前端自动化已覆盖 |
| NFR-LN-01 | 实时性 | 任务状态、学习进度、通知未读数快速刷新，通知推送延迟不超过设计阈值 | 接口链路自动化覆盖；真实推送时延待手工计时 |
| NFR-LN-02 | 性能 | 任务列表、通知列表分页，批量操作和进度保存响应时间 | 自动化覆盖分页、size 上限和基础规模；压力测试待补充 |
| NFR-LN-03 | 可靠性 | 断点续传、离线队列回放、通知幂等、失败扫描日志 | 自动化覆盖核心分支 |
| NFR-LN-04 | 易用性 | 固定入口、主要路径不超过 2 步、空态/失败态/重试提示 | 前端单元测试覆盖关键状态；浏览器视觉待手工确认 |
| NFR-LN-05 | 可追踪性 | 学习记录、通知读取和删除、提醒扫描失败均可追溯用户与时间 | 自动化覆盖状态日志和扫描日志 |

### 4.2 页面、接口、数据表覆盖

| 类别 | 编号范围 | 覆盖说明 |
| --- | --- | --- |
| 页面 | `UI-LRN-01 ~ UI-LRN-05` | 前端测试覆盖学习任务中心、学习进度、学习行为仪表盘、消息通知中心、提醒规则设置页的加载、成功、空态、失败、筛选、分页和主要操作 |
| 接口 | `API-LRN-01 ~ API-LRN-11` | 后端 MockMvc 和前端 API wrapper 覆盖请求字段、Bearer 登录态、分页、权限、错误、状态流转和响应结构 |
| 数据表 | `DB-LRN-01 ~ DB-LRN-07` | 迁移测试覆盖任务、进度、行为、通知、通知状态日志、提醒规则、通知设置和提醒扫描日志的关键结构 |
| 跨模块 | AUTH、CRS、LAB、HWK、GRD | AUTH 提供当前用户；CRS 提供课程成员和教师权限；LAB/HWK/CRS 触发学习进度和行为记录；GRD 事件生成成绩通知 |

### 4.3 不在本次自动化确认范围

| 范围项 | 说明 | 处理方式 |
| --- | --- | --- |
| 真实浏览器端完整链路 | 需在本地或测试环境中完成登录、进入课程、打开资源/实验/作业、查看任务/进度/仪表盘/通知/提醒设置 | 列入 9.3 手工测试 |
| 生产跨模块事件投递 | LAB/HWK/GRD/CRS 在真实业务动作后投递 LRN 通知事件需要多模块联合环境 | 列入联调待确认 |
| 通知触达时延 | 设计要求通知推送和角标刷新有时延指标，单元测试不能证明真实网络和浏览器事件时延 | 列入手工计时和联调 |
| 高并发压力 | 当前自动化覆盖基础分页和限流，不覆盖大量用户同时上报学习行为或批量通知推送 | 后续压力测试补充 |

## 5 测试环境

| 项目 | 环境/版本 | 说明 |
| --- | --- | --- |
| 操作系统 | Windows 本地开发环境 | 文档编写和自动化验证环境 |
| 后端 | Java 21、Spring Boot、Maven、H2 测试库 | 后端目标测试使用 Maven Surefire 执行 |
| 前端 | Node.js、Vue、Vitest、jsdom | 前端目标测试使用 `npm run test:unit -- ...` 执行 |
| 数据库 | H2 测试数据库、MySQL 兼容迁移脚本 | 迁移测试验证 LRN 表结构和约束 |
| 鉴权 | Bearer token 测试上下文 | 自动化重点验证当前登录用户和课程成员隔离 |

## 6 测试数据

### 6.3 LRN 测试数据

| 数据类别 | 数据说明 | 使用场景 |
| --- | --- | --- |
| 学生用户 | `201`、`1002` 等课程成员学生，另设非成员学生用于越权访问 | 任务列表、进度、行为、通知、提醒 |
| 教师用户 | `101`、`1001` 等课程教师或管理者 | 教师聚合进度、课程事件和跨模块联调 |
| 课程数据 | 成员课程、非成员课程、教师管理课程、公开课程和 INT 演示课程 | 课程成员隔离、教师权限、学习任务聚合 |
| 章节数据 | 课程章节、资源所属章节、断点恢复章节 | 课程/章节进度、继续学习 |
| 来源任务 | CRS 课程资源、LAB 实验、HWK 作业；包含未开始、进行中、已完成、已逾期、不同截止时间 | 学习任务中心、截止提醒 |
| 学习进度 | 不同 `sourceModule`、`sourceId`、`progressPercent`、`lastPosition`、`status` | 断点续传、继续学习、教师统计 |
| 学习行为 | `VIEW`、`COMPLETE` 等行为；不同开始/结束时间、时长、服务端创建时间 | 仪表盘、趋势、访问次数、限流 |
| 通知事件 | `TASK`、`GRADE`、`ANNOUNCEMENT`、`SYSTEM` 类型；同一事件重复投递 | 分类展示、幂等、内部鉴权 |
| 通知状态 | 未读、已读、删除；批量已读；其他用户通知 | 通知状态流转、状态日志、用户隔离 |
| 提醒规则 | 作业/实验截止前提醒、必选规则、非必要规则、禁用偏好 | 提醒设置、截止扫描、重复提醒防护 |
| 失败数据 | 无效分页、无效进度百分比、无效提前分钟、无内部 token、非成员课程、过期/禁止会话 | 异常、权限和边界输入 |

## 7 测试用例汇总

### 7.3 LRN 测试用例汇总

#### 7.3.1 LRN 总体自动化结果

| 测试类别 | 命令 | 执行结果 |
| --- | --- | --- |
| 后端 LRN 目标测试 | `mvn -q "-Dtest=LearningTaskControllerTest,LearningTaskMigrationTest,LearningTaskDefaultConfigurationTest,LearningProgressControllerTest,LearningProgressMigrationTest,LearningRecordControllerTest,LearningRecordMigrationTest,NotificationControllerTest,NotificationMigrationTest,ReminderRuleControllerTest,ReminderRuleFailureLoggingTest,ReminderRuleServiceTest,ReminderRuleMigrationTest,GrdLrnIntegrationTest,IntDemoDataInitializerTest" test` | 15 个测试类、41 条测试通过，0 失败，0 错误，0 跳过 |
| 前端 LRN 目标测试 | `npm run test:unit -- tests/unit/lrn/LearningTaskCenterView.spec.ts tests/unit/lrn/LearningProgressView.spec.ts tests/unit/lrn/LearningStatisticsView.spec.ts tests/unit/lrn/NotificationCenterView.spec.ts tests/unit/lrn/ReminderRuleSettingsView.spec.ts tests/unit/lrn/learningTasksApi.spec.ts tests/unit/lrn/learningProgressApi.spec.ts tests/unit/lrn/learningRecordsApi.spec.ts tests/unit/lrn/notificationsApi.spec.ts tests/unit/lrn/reminderRulesApi.spec.ts tests/unit/CourseManagementView.spec.ts tests/unit/lab/LabStudentView.spec.ts tests/unit/hwk/HomeworkStudentView.spec.ts` | 13 个测试文件、67 条测试通过 |

#### 7.3.2 LRN 追踪矩阵

| 用例编号 | 对应需求 | 页面 | 接口 | 数据表 | 覆盖重点 | 自动化状态 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LN-01 | FR-LN-01 | UI-LRN-01 | API-LRN-01 | DB-LRN-01 | 学习任务聚合、筛选、排序、分页、用户隔离 | 已覆盖 |
| TC-LN-02 | FR-LN-02 | UI-LRN-02 | API-LRN-02、API-LRN-03 | DB-LRN-02 | 课程/章节进度、断点续传、继续学习、教师聚合 | 已覆盖 |
| TC-LN-03 | FR-LN-03 | UI-LRN-03 | API-LRN-04、API-LRN-05 | DB-LRN-03 | 行为上报、7 天统计、访问次数、完成数、离线队列、限流 | 已覆盖 |
| TC-LN-04 | FR-LN-04 | UI-LRN-04 | API-LRN-06、API-LRN-09 | DB-LRN-04 | 通知分类、内部事件、幂等、筛选分页、当前用户隔离 | 已覆盖 |
| TC-LN-05 | FR-LN-05 | UI-LRN-04 | API-LRN-07、API-LRN-08 | DB-LRN-05 | 已读、批量已读、删除、跳转、状态日志、越权保护 | 已覆盖 |
| TC-LN-06 | FR-LN-06 | UI-LRN-05 | API-LRN-10、API-LRN-11 | DB-LRN-06、DB-LRN-07 | 提醒规则、通知偏好、截止提醒、失败记录 | 已覆盖 |
| TC-LN-N01 | NFR-LN-01 | 全部 LRN 页面 | 全部 LRN 接口 | DB-LRN-01 ~ DB-LRN-07 | 登录态、课程权限、事件接口鉴权、用户数据隔离 | 已覆盖，真实推送时延待手工 |
| TC-LN-N02 | NFR-LN-02 | UI-LRN-04 | API-LRN-09 | DB-LRN-04、DB-LRN-05 | 通知幂等、不重复生成、失败可记录 | 已覆盖 |
| TC-LN-N03 | NFR-LN-03 | UI-LRN-01 ~ UI-LRN-05 | API-LRN-01 ~ API-LRN-11 | 全部 LRN 表 | 空态、失败态、重试、主流程入口可用 | 部分已覆盖，浏览器待验收 |
| TC-LN-N04 | NFR-LN-04 | UI-LRN-01、UI-LRN-04 | API-LRN-01、API-LRN-06 | DB-LRN-01、DB-LRN-04 | 分页上限、基础规模、列表响应 | 已覆盖，压力待补充 |
| TC-LN-N05 | NFR-LN-05 | 全部 LRN 页面 | 全部 LRN 接口 | 全部 LRN 表 | Mock/测试接口、日志和状态可追踪 | 已覆盖 |

#### 7.3.3 LRN 可执行用例表

| 用例编号 | 对应需求 | 用例名称 | 前置条件 | 测试数据 | 操作步骤 | 预期结果 | 实际结果 | 通过状态 | 自动化覆盖 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TC-LN-01-01 | FR-LN-01 | 学生查看课程资源、实验、作业聚合任务 | 学生已登录且是课程成员 | 课程资源、实验、作业各 1 条 | 调用 `GET /api/v1/learning/tasks` 或进入学习任务中心 | 返回三类任务，包含课程名、类型、状态、截止时间、进度和跳转地址 | 后端和前端测试通过 | 通过 | `LearningTaskControllerTest.studentGetsAggregatedResourceLabHomeworkAndOwnSnapshotsForMemberCourses`、`LearningTaskCenterView.spec.ts` |
| TC-LN-01-02 | FR-LN-01 | 任务列表分页和 size 上限 | 学生已登录且有超过一页任务 | 105 条任务，`page`、`size` 参数 | 请求第 1 页、第 2 页和超大 `size` | 按请求页返回切片，超大 `size` 被限制，响应含总数和分页信息 | 后端和前端测试通过 | 通过 | `LearningTaskControllerTest.pageAndSizeReturnTheRequestedSliceOfAggregatedTasks`、`largeTaskListIsPagedAndSizeIsCappedForNfrPerformance`、`LearningTaskCenterView.spec.ts` |
| TC-LN-01-03 | FR-LN-01 | 按类型、状态、课程和截止时间排序筛选 | 学生已登录 | 不同类型、状态、课程、截止时间任务 | 带 `type`、`status`、`courseId`、排序参数请求任务列表 | 仅返回匹配任务，截止时间排序正确 | 后端和前端测试通过 | 通过 | `LearningTaskControllerTest.studentCanFilterByTypeStatusCourseAndSortByDeadlineDescending` |
| TC-LN-01-04 | FR-LN-01、NFR-LN-01 | 非成员不能看到课程任务 | 学生已登录但不是课程成员 | 非成员课程中的任务 | 请求学习任务列表 | 不返回非成员课程任务 | 后端测试通过 | 通过 | `LearningTaskControllerTest.bearerTokenStudentCannotSeeTasksFromCoursesWhereTheyAreNotMembers` |
| TC-LN-01-05 | FR-LN-01 | 未登录访问任务中心失败态 | 无有效 token | 无 | 请求 `GET /api/v1/learning/tasks` 或打开页面 | 后端返回未认证；前端显示失败/登录态提示并可重试 | 后端测试通过；页面失败态测试通过 | 通过 | `LearningTaskControllerTest.unauthenticatedTaskListRequestIsRejected`、`LearningTaskCenterView.spec.ts` |
| TC-LN-02-01 | FR-LN-02 | 学生保存并恢复自己的学习进度 | 学生已登录且是课程成员 | `courseId`、`chapterId`、`sourceModule=CRS`、`lastPosition` | 调用 `POST /api/v1/learning/progress` 后再 `GET /api/v1/learning/progress` | 返回课程/章节进度和继续学习地址，断点位置不丢失 | 后端和前端测试通过 | 通过 | `LearningProgressControllerTest.bearerTokenStudentCanSaveAndResumeOwnCourseProgress`、`LearningProgressView.spec.ts` |
| TC-LN-02-02 | FR-LN-02 | 同一来源进度更新不重复插入 | 学生已登录且是课程成员 | 同一 `userId/courseId/sourceModule/sourceId` 两次上报 | 连续保存不同进度和 `lastPosition` | 更新原记录，最新断点生效，不产生重复来源行 | 后端测试通过 | 通过 | `LearningProgressControllerTest.savingSameSourceProgressUpdatesBreakpointInsteadOfDuplicatingRows` |
| TC-LN-02-03 | FR-LN-02 | 课程进度按章节平均聚合 | 学生已登录且存在多个章节进度 | 章节进度 20%、80% | 查询学习进度 | 课程级进度为章节进度平均值，章节明细完整 | 后端测试通过 | 通过 | `LearningProgressControllerTest.courseProgressAggregatesChapterProgressByAveragePercent` |
| TC-LN-02-04 | FR-LN-02 | 非成员不能保存或查询课程进度 | 学生已登录但不是课程成员 | 非成员课程进度请求 | 调用保存和查询接口 | 返回权限错误，不写入进度 | 后端测试通过 | 通过 | `LearningProgressControllerTest.nonMemberCannotSaveOrQueryCourseProgress` |
| TC-LN-02-05 | FR-LN-02 | 进度输入边界校验 | 学生已登录 | 无效百分比、缺失来源、非法状态 | 调用 `POST /api/v1/learning/progress` | 返回 400，不写入异常数据 | 后端测试通过 | 通过 | `LearningProgressControllerTest.invalidProgressPayloadIsRejected` |
| TC-LN-02-06 | FR-LN-02 | 教师只能查看所教课程聚合进度 | 教师已登录且管理课程 | 所教课程和非所教课程 | 调用教师聚合进度接口 | 所教课程返回班级聚合，非教师或非所教课程被拒绝 | 后端和前端测试通过 | 通过 | `LearningProgressControllerTest.teacherCanViewAggregateProgressOnlyForManagedCourse`、`studentCannotViewTeacherAggregateProgress`、`LearningProgressView.spec.ts` |
| TC-LN-02-07 | FR-LN-02 | CRS/LAB/HWK 页面继续学习恢复断点 | 已存在 `lastPosition` | CRS `chapterId`、LAB/HWK `resume` 查询参数 | 从学习进度页点击继续学习 | 目标页面按断点恢复章节、代码或作答上下文 | 前端单元测试通过；真实浏览器待手工确认 | 部分通过 | `CourseManagementView.spec.ts`、`LabStudentView.spec.ts`、`HomeworkStudentView.spec.ts` |
| TC-LN-03-01 | FR-LN-03 | 学生上报学习行为并查看 7 天仪表盘 | 学生已登录且是课程成员 | 行为记录、时长、开始/结束时间 | 调用 `POST /api/v1/learning/records` 后查询 `GET /api/v1/learning/statistics` | 统计返回总时长、访问次数、完成任务数、7 天趋势和最近记录 | 后端和前端测试通过 | 通过 | `LearningRecordControllerTest.bearerTokenStudentCanReportBehaviorAndViewSevenDayDashboard`、`LearningStatisticsView.spec.ts` |
| TC-LN-03-02 | FR-LN-03、NFR-LN-01 | 非成员不能上报或查询课程行为 | 学生已登录但不是课程成员 | 非成员课程行为记录 | 上报和查询统计 | 返回权限错误，不泄露统计 | 后端测试通过 | 通过 | `LearningRecordControllerTest.nonMemberCannotReportOrQueryCourseBehavior` |
| TC-LN-03-03 | FR-LN-03 | 学习行为输入边界校验 | 学生已登录 | 非法时长、非法时间、缺失来源 | 调用 `POST /api/v1/learning/records` | 返回 400，不写入行为记录 | 后端测试通过 | 通过 | `LearningRecordControllerTest.invalidLearningRecordPayloadIsRejected` |
| TC-LN-03-04 | FR-LN-03、NFR-LN-02 | 行为上报按用户和资源限流 | 学生已登录 | 同一用户同一资源每分钟超过 10 次 | 连续上报学习行为 | 超过阈值返回 429，限流基于服务端接收时间 | 后端测试通过 | 通过 | `LearningRecordControllerTest.learningRecordReportsAreRateLimitedPerUserAndSource`、`learningRecordRateLimitUsesServerReceiveTimeInsteadOfClientStartedAt` |
| TC-LN-03-05 | FR-LN-03、NFR-LN-03 | 离线行为记录重连后回放 | 前端请求失败或浏览器离线 | 本地队列中的行为记录 | 触发请求失败、恢复在线或调用回放函数 | 队列按当前用户和课程隔离，恢复后重新 POST，失败项保留 | 前端测试通过 | 通过 | `learningRecordsApi.spec.ts` |
| TC-LN-03-06 | FR-LN-03、NFR-LN-01 | 仪表盘缓存不跨用户泄露 | 用户 A、B 切换或会话过期 | 用户 A 缓存统计、用户 B 请求失败 | 查询统计接口失败或 401/403 | 普通失败只使用同用户同课程缓存；401/403 不用旧缓存 | 前端测试通过 | 通过 | `learningRecordsApi.spec.ts` |
| TC-LN-04-01 | FR-LN-04 | 内部业务事件生成分类通知 | 内部服务带合法 token | TASK、GRADE、ANNOUNCEMENT、SYSTEM 事件 | 调用 `POST /api/v1/notifications/events` | 仅为课程成员生成对应分类通知，标题、内容、来源和跳转地址正确 | 后端测试通过 | 通过 | `NotificationControllerTest.internalBusinessEventCreatesCategorizedNotificationsForCourseMembersOnlyAndIsIdempotent` |
| TC-LN-04-02 | FR-LN-04、NFR-LN-03 | 重复事件幂等 | 内部服务带合法 token | 相同 `eventId/sourceModule/sourceId/userId` | 重复投递同一事件 | 不重复生成通知 | 后端测试通过 | 通过 | `NotificationControllerTest.internalBusinessEventCreatesCategorizedNotificationsForCourseMembersOnlyAndIsIdempotent` |
| TC-LN-04-03 | FR-LN-04 | 通知列表分类、已读、时间、分页筛选 | 用户已登录且有多类通知 | 多类型、已读/未读、不同时间通知 | 调用 `GET /api/v1/notifications` 并切换筛选 | 仅返回当前用户通知，筛选和分页正确，未读数按用户计算 | 后端和前端测试通过 | 通过 | `NotificationControllerTest.notificationListSupportsTypeReadTimeAndPaginationFiltersForCurrentUserOnly`、`NotificationCenterView.spec.ts` |
| TC-LN-04-04 | FR-LN-04、NFR-LN-01 | 内部事件接口鉴权和载荷校验 | 无 token 或非法 token | 缺失字段、非法类型 | 调用事件接口 | 返回未授权或 400，不生成通知 | 后端测试通过 | 通过 | `NotificationControllerTest.notificationEventRequiresInternalTokenAndValidPayload` |
| TC-LN-04-05 | FR-LN-04 | GRD 成绩事件生成 LRN 通知 | GRD 发布、变更或复核成绩 | LAB/HWK 成绩事件 | 执行 GRD/LRN 集成测试 | LRN 为相关学生生成成绩通知 | 后端集成测试通过 | 通过 | `GrdLrnIntegrationTest.grdGradeEventsCreateLrnNotificationsForPublishChangeAndReviewFlow` |
| TC-LN-05-01 | FR-LN-05 | 单条和批量标记已读 | 用户已登录且有未读通知 | 当前用户通知 ID 列表 | 调用 `PUT /api/v1/notifications/read` 或页面批量已读 | 通知变为已读，写入状态日志，未读数更新 | 后端和前端测试通过 | 通过 | `NotificationControllerTest.readAndDeleteActionsAreScopedToCurrentUserAndLogged`、`NotificationCenterView.spec.ts`、`notificationsApi.spec.ts` |
| TC-LN-05-02 | FR-LN-05 | 删除通知仅影响当前用户 | 用户已登录 | 当前用户通知和其他用户通知 | 调用 `DELETE /api/v1/notifications/{notificationId}` | 当前用户通知软删除且列表隐藏；其他用户通知不受影响；写状态日志 | 后端和前端测试通过 | 通过 | `NotificationControllerTest.readAndDeleteActionsAreScopedToCurrentUserAndLogged`、`readAndDeleteActionsRejectInvalidInputAndHideDeletedNotifications` |
| TC-LN-05-03 | FR-LN-05 | 通知业务跳转 | 用户已登录且通知含 `actionUrl` | 指向任务、成绩、公告或系统页面的通知 | 在通知中心点击查看/跳转 | 跳转到对应业务页面，通知内容不丢失 | 前端组件测试覆盖链接渲染；真实浏览器待手工确认 | 部分通过 | `NotificationCenterView.spec.ts` |
| TC-LN-05-04 | FR-LN-05、NFR-LN-05 | 通知状态日志留痕 | 用户执行已读和删除 | `lrn_notification_status_log` | 标记已读、删除通知后查询日志 | 日志记录旧状态、新状态、操作类型、用户和时间 | 后端测试通过 | 通过 | `NotificationControllerTest.readAndDeleteActionsAreScopedToCurrentUserAndLogged`、`NotificationMigrationTest.migrationCreatesNotificationStatusLogTable` |
| TC-LN-06-01 | FR-LN-06 | 当前用户读取和保存提醒规则及通知偏好 | 用户已登录 | 多条提醒规则和偏好开关 | 调用 `GET/PUT /api/v1/reminder-rules` 或在设置页保存 | 返回并保存当前用户规则和偏好，必选规则不可被非法关闭 | 后端和前端测试通过 | 通过 | `ReminderRuleControllerTest.currentUserCanReadAndSaveReminderRulesAndNotificationSettings`、`ReminderRuleSettingsView.spec.ts` |
| TC-LN-06-02 | FR-LN-06 | 提醒规则输入边界校验 | 用户已登录 | 无效 `aheadMinutes`、非法来源模块 | 调用 `PUT /api/v1/reminder-rules` | 返回 400，不写入非法规则 | 后端测试通过 | 通过 | `ReminderRuleControllerTest.savingReminderRulesRejectsInvalidAheadMinutesAndSourceContract` |
| TC-LN-06-03 | FR-LN-06 | 截止提醒扫描只提醒未提交学生并遵守偏好 | 存在临近截止 LAB/HWK | 已提交学生、未提交学生、关闭非必要提醒用户 | 执行截止提醒扫描 | 只为符合规则且未提交用户生成提醒，关闭偏好的用户不接收非必要提醒 | 后端测试通过 | 通过 | `ReminderRuleControllerTest.deadlineScanCreatesRemindersForUnsubmittedStudentsAndHonorsPreferences` |
| TC-LN-06-04 | FR-LN-06、NFR-LN-05 | 提醒失败记录 | 通知写入异常或状态日志表异常 | 模拟提醒投递失败 | 执行截止提醒扫描 | 失败原因写入扫描日志，不静默丢失 | 后端测试通过 | 通过 | `ReminderRuleFailureLoggingTest.failedReminderDeliveryStillPersistsFailedScanLog`、`ReminderRuleServiceTest.failedDeadlineScanWritesFailureBatchLogBeforeRethrowing` |
| TC-LN-N01-01 | NFR-LN-01 | LRN 接口登录态和课程权限隔离 | 未登录、非成员、非教师用户 | 任务、进度、行为、通知、提醒请求 | 分别调用受保护接口 | 未登录拒绝；非成员不能看课程数据；学生不能看教师聚合 | 后端自动化通过 | 通过 | `LearningTaskControllerTest`、`LearningProgressControllerTest`、`LearningRecordControllerTest`、`NotificationControllerTest` |
| TC-LN-N02-01 | NFR-LN-02 | 任务和通知列表分页性能基础样本 | 已生成多条任务和通知 | `page/size` 和超大 `size` | 查询任务和通知列表 | 返回分页数据，size 上限生效，不一次性返回全量 | 后端测试通过 | 通过 | `LearningTaskControllerTest.largeTaskListIsPagedAndSizeIsCappedForNfrPerformance`、`NotificationControllerTest.notificationListCapsPageSizeAndUnreadCountIsUserScoped` |
| TC-LN-N03-01 | NFR-LN-03 | 断点、离线队列和通知幂等可靠性 | 存在进度、离线记录和重复事件 | `lastPosition`、本地队列、重复 `eventId` | 保存进度、恢复在线、重复投递事件 | 断点不丢失，离线记录回放，通知不重复 | 自动化通过 | 通过 | `LearningProgressControllerTest`、`learningRecordsApi.spec.ts`、`NotificationControllerTest` |
| TC-LN-N04-01 | NFR-LN-04 | LRN 页面空态、失败态和重试 | API 返回空列表或错误 | 空任务、空通知、加载失败 | 打开页面、切换筛选、点击重试 | 页面展示清晰空态/失败态，重试重新发起请求 | 前端测试通过 | 通过 | `LearningTaskCenterView.spec.ts`、`LearningProgressView.spec.ts`、`LearningStatisticsView.spec.ts`、`NotificationCenterView.spec.ts`、`ReminderRuleSettingsView.spec.ts` |
| TC-LN-N05-01 | NFR-LN-05 | 数据库表结构和状态可追踪性 | 迁移脚本可执行 | LRN 五个迁移脚本 | 执行迁移测试 | 关键表、唯一约束、状态日志和扫描日志结构可创建 | 后端迁移测试通过 | 通过 | `LearningTaskMigrationTest`、`LearningProgressMigrationTest`、`LearningRecordMigrationTest`、`NotificationMigrationTest`、`ReminderRuleMigrationTest` |

## 8 测试执行日志

### 8.3 LRN 测试执行日志

#### 8.3.1 后端 LRN 执行日志

| 日志编号 | 时间 | 命令/测试类 | 执行内容 | 结果 |
| --- | --- | --- | --- | --- |
| LRN-LOG-001 | 2026-06-10 15:54 | `LearningTaskControllerTest` | 学习任务聚合、分页、筛选、排序、成员隔离、未登录拒绝 | 6 条通过 |
| LRN-LOG-002 | 2026-06-10 15:54 | `LearningProgressControllerTest` | 学习进度保存、断点续传、章节聚合、非成员拒绝、教师聚合 | 8 条通过 |
| LRN-LOG-003 | 2026-06-10 15:54 | `LearningRecordControllerTest` | 行为上报、统计仪表盘、非成员拒绝、非法载荷、服务端时间限流 | 6 条通过 |
| LRN-LOG-004 | 2026-06-10 15:54 | `NotificationControllerTest` | 通知分类生成、幂等、筛选分页、已读/删除、状态日志、内部 token | 6 条通过 |
| LRN-LOG-005 | 2026-06-10 15:54 | `ReminderRuleControllerTest`、`ReminderRuleFailureLoggingTest`、`ReminderRuleServiceTest` | 提醒规则和偏好保存、截止提醒扫描、失败日志 | 5 条通过 |
| LRN-LOG-006 | 2026-06-10 15:54 | LRN 迁移与配置测试 | `lrn_learning_task`、`lrn_learning_progress`、`lrn_learning_record`、`lrn_notification`、`lrn_notification_status_log`、`lrn_reminder_rule`、`lrn_notification_setting`、扫描日志和默认迁移配置 | 7 条通过 |
| LRN-LOG-007 | 2026-06-10 15:54 | `GrdLrnIntegrationTest`、`IntDemoDataInitializerTest` | GRD 成绩事件生成通知、INT 演示数据覆盖登录-课程-学习-LAB/HWK-GRD-通知闭环 | 3 条通过 |
| LRN-LOG-008 | 2026-06-10 15:54 | Maven 汇总 | 目标命令共 15 个测试类 | 41 条通过，0 失败，0 错误，0 跳过 |

#### 8.3.2 前端 LRN 执行日志

| 日志编号 | 时间 | 命令/测试文件 | 执行内容 | 结果 |
| --- | --- | --- | --- | --- |
| LRN-LOG-009 | 2026-06-10 15:55 | `LearningTaskCenterView.spec.ts`、`learningTasksApi.spec.ts` | 学习任务中心页面、筛选、分页、失败重试、API 参数和 Bearer 鉴权 | 5 条通过 |
| LRN-LOG-010 | 2026-06-10 15:55 | `LearningProgressView.spec.ts`、`learningProgressApi.spec.ts` | 课程/章节进度展示、继续学习、教师聚合、API 调用 | 4 条通过 |
| LRN-LOG-011 | 2026-06-10 15:55 | `LearningStatisticsView.spec.ts`、`learningRecordsApi.spec.ts` | 个人仪表盘、缓存失败态、行为上报、离线队列、用户/课程缓存隔离 | 9 条通过 |
| LRN-LOG-012 | 2026-06-10 15:55 | `NotificationCenterView.spec.ts`、`notificationsApi.spec.ts` | 分类通知、未读高亮、筛选分页、已读、批量已读、删除、API 调用 | 8 条通过 |
| LRN-LOG-013 | 2026-06-10 15:55 | `ReminderRuleSettingsView.spec.ts`、`reminderRulesApi.spec.ts` | 提醒规则展示、偏好保存、失败重试、API 调用 | 4 条通过 |
| LRN-LOG-014 | 2026-06-10 15:55 | `CourseManagementView.spec.ts`、`LabStudentView.spec.ts`、`HomeworkStudentView.spec.ts` | CRS/LAB/HWK 页面触发 LRN 进度、行为记录和断点恢复 | 37 条通过 |
| LRN-LOG-015 | 2026-06-10 15:55 | Vitest 汇总 | 目标命令共 13 个测试文件 | 67 条通过 |

#### 8.3.3 本次文档校验日志

| 日志编号 | 时间 | 命令 | 执行内容 | 结果 |
| --- | --- | --- | --- | --- |
| LRN-LOG-016 | 2026-06-10 15:56 | `git diff --check` | 检查文档变更空白和补丁格式 | 通过 |

## 9 手动测试

### 9.3 LRN 手动测试

| 手工编号 | 关联用例 | 场景 | 前置条件 | 操作步骤 | 预期结果 | 当前状态 | 残余风险 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| LRN-MAN-001 | TC-LN-01 | 首页进入学习任务中心并查看分页 | 本地前后端启动，学生账号已登录并加入课程 | 从首页点击学习任务入口，切换状态/类型筛选，点击上一页/下一页 | 页面毛玻璃风格一致；任务为当前学生可见课程；分页可切换；空态/失败态清晰 | 待手工验收 | 入口样式和真实课程数据需浏览器确认 |
| LRN-MAN-002 | TC-LN-02 | 课程资源断点续传 | 学生已打开课程资源并产生进度 | 从学习进度页点击继续学习 | 回到课程页对应章节/资源位置，不进入错误课程 | 待手工验收 | jsdom 只能覆盖参数恢复，真实滚动/播放位置需浏览器确认 |
| LRN-MAN-003 | TC-LN-02 | LAB/HWK 断点续传 | 学生在实验/作业中保存草稿或产生进度 | 从学习进度页点击继续学习到 LAB/HWK | 代码或作答上下文恢复，打开页面不会立即覆盖原断点 | 待手工验收 | 真实编辑器状态和复杂题型恢复需联调确认 |
| LRN-MAN-004 | TC-LN-03 | 仪表盘数据随真实学习动作变化 | 学生进入 CRS/LAB/HWK 并完成访问或提交 | 打开学习仪表盘，观察总时长、访问次数、趋势和最近记录 | 统计与真实操作一致，失败时展示重试或缓存提示 | 待手工验收 | 学习时长依赖真实停留时间，需人工计时核对 |
| LRN-MAN-005 | TC-LN-04、TC-LN-05 | 通知中心分类、已读、删除、跳转 | 账号存在任务、成绩、公告、系统通知 | 切换类型/未读筛选，批量已读，删除单条，点击业务跳转 | 未读数变化正确；删除后列表隐藏；跳转到对应业务页；其他用户通知不受影响 | 待手工验收 | 真实业务页面跳转和通知角标需浏览器确认 |
| LRN-MAN-006 | TC-LN-04 | 跨模块真实事件生成通知 | CRS/LAB/HWK/GRD 模块在同一环境可用 | 发布公告、发布实验/作业、发布成绩或复核成绩 | LRN 收到事件并生成分类通知，重复操作不生成重复通知 | 联调待确认 | 生产事件触发时机和内部 token 配置需多模块确认 |
| LRN-MAN-007 | TC-LN-06 | 提醒规则设置和截止提醒 | 学生有临近截止且未提交的 LAB/HWK | 修改提醒偏好，等待或触发提醒扫描 | 符合规则的未提交任务产生提醒；关闭非必要提醒后不再收到非必要提醒 | 待手工验收 | 定时任务执行周期和测试时间窗口需环境配合 |
| LRN-MAN-008 | TC-LN-N01 | 权限边界手工核对 | 准备学生 A、学生 B、教师、非成员账号 | 分别访问学习任务、进度、仪表盘、通知和提醒页面 | A/B 互不泄露数据；非成员被拒绝；学生不能查看教师聚合 | 待手工验收 | 自动化已覆盖主要分支，真实账号配置仍需核对 |
| LRN-MAN-009 | TC-LN-N01、TC-LN-N04 | 会话过期和网络异常 | 删除 token 或关闭后端服务 | 打开 LRN 页面并执行刷新/保存/已读/删除操作 | 显示登录失效或网络失败提示，不展示其他用户缓存 | 待手工验收 | 真实浏览器 localStorage 和网络错误提示需确认 |
| LRN-MAN-010 | TC-LN-N02、TC-LN-N03 | 通知触达时延和可靠性 | 测试环境支持通知轮询或推送 | 触发任务/成绩/公告通知并计时 | 通知列表和未读数在设计阈值内刷新，断线恢复后不丢通知 | 联调待确认 | 单元测试不能证明真实推送时延 |

## 10 验收结论

### 10.3 LRN 验收结论

| 验收维度 | 结论 | 依据 | 残余风险/后续动作 |
| --- | --- | --- | --- |
| 功能完整性 | 基本通过 | `TC-LN-01 ~ TC-LN-06` 均有可执行用例，后端和前端目标测试通过 | 真实浏览器端到端和跨模块生产事件仍需测试负责人整合验收 |
| 接口契约 | 通过 | `API-LRN-01 ~ API-LRN-11` 均有后端或前端测试覆盖 | 内部事件接口真实部署 token/IP 白名单策略需环境确认 |
| 数据表和状态 | 通过 | `DB-LRN-01 ~ DB-LRN-07` 迁移和状态日志测试通过 | 生产库迁移顺序需在统一部署脚本中再次确认 |
| 权限和隔离 | 通过 | 自动化覆盖 Bearer 登录态、课程成员、教师聚合和当前用户通知隔离 | 真实账号矩阵需手工复验 |
| 异常和边界 | 通过 | 自动化覆盖未登录、非成员、非法参数、限流、内部 token 错误、提醒失败日志 | 高并发异常和真实网络波动需压力/联调补充 |
| 非功能 | 部分通过 | 自动化覆盖分页、size 上限、幂等、离线队列、状态日志和失败重试 | 通知时延、列表响应时间和高并发压力需专项测试 |
| 测试文档交付 | 通过 | 本文件按 TST-DOC-01 和各模块负责人任务整理 6.3、7.3、8.3、9.3、10.3 内容 | 交由 @MontesquieuE 统一整合时需补充最终 FAT/UAT 实测结果 |

## 11 附录

### 11.3 LRN 附录

#### 11.3.1 后端目标测试命令

```bash
cd backend
mvn -q "-Dtest=LearningTaskControllerTest,LearningTaskMigrationTest,LearningTaskDefaultConfigurationTest,LearningProgressControllerTest,LearningProgressMigrationTest,LearningRecordControllerTest,LearningRecordMigrationTest,NotificationControllerTest,NotificationMigrationTest,ReminderRuleControllerTest,ReminderRuleFailureLoggingTest,ReminderRuleServiceTest,ReminderRuleMigrationTest,GrdLrnIntegrationTest,IntDemoDataInitializerTest" test
```

#### 11.3.2 前端目标测试命令

```bash
cd frontend
npm run test:unit -- tests/unit/lrn/LearningTaskCenterView.spec.ts tests/unit/lrn/LearningProgressView.spec.ts tests/unit/lrn/LearningStatisticsView.spec.ts tests/unit/lrn/NotificationCenterView.spec.ts tests/unit/lrn/ReminderRuleSettingsView.spec.ts tests/unit/lrn/learningTasksApi.spec.ts tests/unit/lrn/learningProgressApi.spec.ts tests/unit/lrn/learningRecordsApi.spec.ts tests/unit/lrn/notificationsApi.spec.ts tests/unit/lrn/reminderRulesApi.spec.ts tests/unit/CourseManagementView.spec.ts tests/unit/lab/LabStudentView.spec.ts tests/unit/hwk/HomeworkStudentView.spec.ts
```

#### 11.3.3 LRN 页面和接口快速索引

| 页面 | 前端页面文件 | 主要接口 |
| --- | --- | --- |
| UI-LRN-01 学习任务中心页 | `frontend/src/views/lrn/LearningTaskCenterView.vue` | `GET /api/v1/learning/tasks` |
| UI-LRN-02 学习进度页 | `frontend/src/views/lrn/LearningProgressView.vue` | `GET /api/v1/learning/progress`、`POST /api/v1/learning/progress` |
| UI-LRN-03 学习行为仪表盘 | `frontend/src/views/lrn/LearningStatisticsView.vue` | `GET /api/v1/learning/statistics`、`POST /api/v1/learning/records` |
| UI-LRN-04 消息通知中心页 | `frontend/src/views/lrn/NotificationCenterView.vue` | `GET /api/v1/notifications`、`PUT /api/v1/notifications/read`、`DELETE /api/v1/notifications/{notificationId}`、`POST /api/v1/notifications/events` |
| UI-LRN-05 提醒规则设置页 | `frontend/src/views/lrn/ReminderRuleSettingsView.vue` | `GET /api/v1/reminder-rules`、`PUT /api/v1/reminder-rules` |

#### 11.3.4 交付说明

本 issue 仅交付测试文档，不修改业务代码。测试负责人整合时可直接抽取本文件的 `6.3`、`7.3`、`8.3`、`9.3`、`10.3` 和 `11.3` 小节合入总测试报告，并在 FAT/UAT 后补充手工测试实际结果、缺陷编号和最终审批记录。
