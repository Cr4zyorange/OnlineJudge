# TST-DOC-06 HWK 作业与自动评测测试文档

| 文档编号 | TST-DOC-06 |
| --- | --- |
| 文档名称 | HWK 作业与自动评测测试文档 |
| 项目名称 | 在线教学与实训平台 |
| 所属阶段 | 系统测试与验收测试 |
| 报告版本 | V1.0 |
| 编写日期 | 2026-06-09 |
| 编写人 | HWK 模块负责人 |
| 对应 issue | #157 TST-DOC-06 HWK 作业与自动评测测试文档编写 |
| 测试范围 | HWK 作业发布、提交、历史、自动评测、教师批阅、结果展示、统计、权限、安全、跨模块通知与成绩来源 |
| 测试结论 | 自动化测试通过；浏览器端完整手工验收、真实沙箱压力与跨模块联调仍需测试负责人整合确认 |

## 1 文档控制

### 1.1 修订记录

| 版本 | 日期 | 修订人 | 修订说明 |
| --- | --- | --- | --- |
| V1.0 | 2026-06-09 | HWK 模块负责人 | 按 #152 统一结构整理 HWK 测试范围、用例、自动化覆盖、执行日志、手工验收点和残余风险 |

### 1.2 审批记录

| 角色 | 姓名/负责人 | 审批意见 | 日期 |
| --- | --- | --- | --- |
| 项目负责人 | 待填写 | 待审批 | 2026-06-09 |
| 测试负责人 | @MontesquieuE | 待整合确认 | 2026-06-09 |
| HWK 模块负责人 | HWK 负责人 | 待确认 | 2026-06-09 |

## 2 测试概述

本文件用于记录 HWK 作业与自动评测模块在当前版本下的测试依据、测试环境、测试数据、测试用例、执行结果、手工验收清单、缺陷风险和验收结论。覆盖范围对齐 `FR-HW-01 ~ FR-HW-06`、`NFR-HW-01 ~ NFR-HW-05`、`UI-HWK-01 ~ UI-HWK-09`、`API-HWK-01 ~ API-HWK-21`、`DB-HWK-01 ~ DB-HWK-07`、`TC-HW-01 ~ TC-HW-18` 与 `TC-HW-N01 ~ TC-HW-N05`。

当前已执行 HWK 后端 Spring Boot 自动化测试和前端 Vue/Vitest 单元测试。自动化覆盖了作业创建与发布、题目与测试用例配置、学生查看与提交、提交历史、客观题评分、代码评测、重评、教师批阅、分数发布、统计、课程权限、隐藏答案/隐藏用例保护、数据库约束、Bearer 登录态与 CRS 成员联动。真实浏览器端到端、真实 Docker 沙箱压力、LRN/GRD 生产联调仍列为手工或集成验收项。

## 3 测试依据

| 序号 | 文档/代码依据 | 用途 |
| --- | --- | --- |
| 1 | `docs/开发/HWK-作业与自动评测模块开发流程.md` | HWK 主流程、开发顺序、P0 闭环、权限与跨模块事件要求 |
| 2 | `docs/最终提交/软件需求规格说明书.md` | FR-HW、NFR-HW 需求和验收来源 |
| 3 | `docs/最终提交/软件概要设计说明书.md` | 模块边界、跨模块依赖和追踪关系来源 |
| 4 | `docs/最终提交/软件详细设计说明书.md` | UI、API、数据库、测试编号和追踪矩阵来源 |
| 5 | `docs/过程/概要/作业与自动评测模块概要设计提交稿（hwk）.md` | HWK 过程设计、非功能要求和页面/API 对照补充 |
| 6 | `docs/过程/详细设计/HWK-作业与自动评测模块-详细设计提交稿.md` | HWK 详细流程、状态、异常和测试编号补充 |
| 7 | `backend/src/test/java/com/onlinejudge/hwk` | HWK 后端自动化测试实现 |
| 8 | `frontend/tests/unit/hwk` | HWK 前端 API 与页面单元测试实现 |
| 9 | `database/migrations/20260530_01_create_hwk_homework.sql`、`20260601_01_create_hwk_submission.sql`、`20260602_01_create_hwk_evaluation.sql`、`20260602_02_create_hwk_review_log.sql` | HWK 数据表和迁移约束依据 |

## 4 测试范围

### 4.1 功能与非功能范围

| 编号 | 测试对象 | 主要验证点 | 当前覆盖状态 |
| --- | --- | --- | --- |
| FR-HW-01 | 作业创建与发布 | 教师/助教创建草稿、编辑、保存题目、保存测试用例、发布、关闭、发布通知 | 后端和前端自动化已覆盖 |
| FR-HW-02 | 学生作业查看与提交 | 学生查看已发布作业，标准答案和隐藏用例不可见，提交文本/客观题/代码，截止和重复提交规则 | 后端和前端自动化已覆盖 |
| FR-HW-03 | 提交历史管理 | 学生个人历史、教师全班分页列表、筛选、提交详情、最新有效提交标识 | 后端和前端自动化已覆盖 |
| FR-HW-04 | 自动评测 | 客观题自动评分、代码题 IO 评测、失败状态保留、评测结果查询、重评 | 后端和前端自动化已覆盖；真实沙箱压力待手工/集成确认 |
| FR-HW-05 | 教师批阅与重评 | 人工分数、评语、分数范围校验、重评理由、批阅/重评日志 | 后端和前端自动化已覆盖 |
| FR-HW-06 | 作业反馈与结果展示 | 成绩发布前隐藏最终分，发布后展示反馈，统计提交率和未提交名单，向 GRD 提供成绩来源 | 后端和前端自动化已覆盖；GRD 全链路需联调确认 |
| NFR-HW-01 | 可靠性 | 提交、评测、批阅、通知失败和分数记录不丢失 | 自动化覆盖核心分支 |
| NFR-HW-02 | 性能 | 作业列表、提交列表、统计接口分页和基础规模响应 | 自动化覆盖分页样本；压力测试待补充 |
| NFR-HW-03 | 可追踪性 | 提交、评测、批阅、重评、成绩发布均有记录或日志 | 自动化覆盖核心日志 |
| NFR-HW-04 | 安全性 | 当前用户来源、课程成员校验、学生本人过滤、隐藏答案/隐藏用例/私有日志保护 | 自动化覆盖 |
| NFR-HW-05 | 可测试性 | 关键流程和异常场景可通过稳定测试数据复现 | 自动化覆盖 |

### 4.2 页面、接口、数据表覆盖

| 类别 | 编号范围 | 覆盖说明 |
| --- | --- | --- |
| 页面 | UI-HWK-01 ~ UI-HWK-09 | 前端测试覆盖学生列表、学生详情/提交、提交历史、教师作业管理、统计和批阅入口；真实浏览器视觉和端到端流程待手工验收 |
| 接口 | API-HWK-01 ~ API-HWK-21 | 后端 MockMvc 和前端 API wrapper 覆盖主要路由、请求体、分页、权限、错误码和响应数据 |
| 数据表 | DB-HWK-01 ~ DB-HWK-07 | 迁移测试覆盖 MySQL 兼容语法、外键、唯一约束、提交版本、评测记录和批阅日志 |
| 跨模块 | AUTH、CRS、LRN、GRD、LAB | AUTH/CRS 已有 Bearer 与成员联动测试；LRN 通知事件和 GRD HWK 来源成绩有自动化样本，完整环境联调待确认；代码评测复用公共评测抽象 |

### 4.3 不在本次自动化确认范围

| 范围项 | 说明 | 处理方式 |
| --- | --- | --- |
| 真实浏览器端到端验收 | 当前未执行从登录、进入课程、发布作业、学生提交、教师批阅、学生查看反馈的完整浏览器流程 | 作为手工验收用例 MAN-HWK-001 ~ MAN-HWK-006 |
| 真实 Docker 沙箱压力 | 自动化使用可控评测样本，未执行多语言真实容器并发和资源限制压力 | 作为手工/专项测试 MAN-HWK-007 |
| 大规模性能压测 | 自动化验证分页和 105 条左右基础样本，未执行生产规模压测 | 作为专项性能测试 MAN-HWK-008 |
| LRN/GRD 生产联调 | HWK 发布事件和成绩来源有代码级覆盖，仍需统一测试环境验证通知中心、学习任务和成绩同步页面 | 作为跨模块联调用例 MAN-HWK-009 |

## 5 测试环境

| 环境项 | 内容 |
| --- | --- |
| 操作系统 | Windows |
| 后端运行环境 | Java 25，Spring Boot 3.4.5，Maven 3.9.16，JUnit 5，MockMvc，H2 MySQL mode |
| 前端运行环境 | Node.js，Vue 3.5，Vite 6.3，Vitest 3.2，jsdom |
| 数据库 | 自动化测试使用 H2 内存库；迁移脚本按 MySQL 8.0 兼容约束编写 |
| 鉴权方式 | 后端测试使用 `X-User-Id`、`X-User-Role` 或 Bearer Session；前端测试 mock API wrapper |
| 执行日期 | 2026-06-09 |

## 6 测试数据

| 数据类别 | 数据说明 | 使用模块 |
| --- | --- | --- |
| 教师/助教用户 | `X-User-Id=501` 等课程管理者；Bearer 集成测试动态创建教师账号 | HWK、AUTH、CRS |
| 学生用户 | `X-User-Id=101`、`601` 等课程成员；非成员学生用于越权验证 | HWK、AUTH、CRS |
| 课程数据 | `courseId=101` 等测试课程，包含教师、助教、学生、非成员分支 | HWK、CRS |
| 作业数据 | 客观题、文本题、代码题，状态包含 DRAFT、PUBLISHED、CLOSED、SCORE_PUBLISHED、ARCHIVED | HWK |
| 题目数据 | 客观题题干、选项、标准答案、分值和排序 | HWK |
| 测试用例数据 | 公开/隐藏 IO 用例、分值权重、语言白名单、时间/内存限制 | HWK、LAB 公共评测抽象 |
| 提交数据 | 文本答案、客观题 JSON、代码文本、语言、提交版本、is_final 标识 | HWK |
| 评测和批阅数据 | ACCEPTED、WRONG_ANSWER、PENDING 等评测状态，人工分数、评语、重评理由、日志 | HWK |
| 跨模块数据 | HOMEWORK_PUBLISHED 通知事件、HWK 来源成绩、作业截止提醒 | HWK、LRN、GRD |

## 7 测试用例汇总

### 7.1 自动化执行结果

| 测试类别 | 命令 | 执行结果 |
| --- | --- | --- |
| 后端 HWK 相关测试 | `mvn test "-Dtest=HomeworkControllerTest,HomeworkBearerAuthControllerTest,HomeworkMigrationTest,HomeworkSubmissionServiceTest"` | 44 条通过，0 失败，0 错误，0 跳过 |
| 前端 HWK 单元测试 | `node node_modules/vitest/vitest.mjs run tests/unit/hwk/homeworksApi.spec.ts tests/unit/hwk/HomeworkStudentListView.spec.ts tests/unit/hwk/HomeworkStudentView.spec.ts tests/unit/hwk/HomeworkSubmissionHistoryView.spec.ts tests/unit/hwk/HomeworkTeacherView.spec.ts --pool=threads` | 5 个测试文件通过，28 条测试通过 |

说明：后端测试首次在普通沙箱下因 `backend/target/classes/schema.sql` 写入受限失败，提权后同一命令通过；前端测试首次因 esbuild 子进程 `spawn EPERM` 失败，提权后同一命令通过。

### 7.2 HWK 核心用例表

| 用例编号 | 对应需求 | 覆盖对象 | 前置条件/测试数据 | 操作步骤 | 预期结果 | 实际结果 | 通过状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TC-HW-01 | FR-HW-01 | UI-HWK-02；API-HWK-01、02、16；DB-HWK-01、02 | 教师/助教具备课程管理权限；准备客观题作业和题目数据 | 创建草稿，保存题目，读取详情 | 作业为 DRAFT，字段和题目正确落库 | `teacherCreatesObjectiveHomeworkDraftAndSavesQuestions`、前端教师创建/编辑用例通过 | 通过 |
| TC-HW-02 | FR-HW-01 | UI-HWK-03；API-HWK-03、05；LRN 事件 | 已有配置完整作业 | 教师发布作业，学生查询列表/详情 | 状态变为 PUBLISHED，学生可见，发送 HOMEWORK_PUBLISHED | `teacherPublishesConfiguredHomeworkAndNotificationIsEmitted`、前端发布用例通过 | 通过 |
| TC-HW-03 | FR-HW-01 | API-HWK-18；DB-HWK-03、07 | 代码题未配置测试用例 | 执行发布 | 返回 `HWK_4007`，状态不变 | `codeHomeworkWithoutTestCasesIsRejectedWhenPublishing`、前端代码题校验用例通过 | 通过 |
| TC-HW-04 | FR-HW-02 | UI-HWK-04；API-HWK-06、17；DB-HWK-02、03 | 已发布客观题/代码题，包含标准答案和隐藏用例 | 学生打开作业详情 | 显示说明和提交要求，不泄露答案、隐藏用例输出 | `studentPublishedHomeworkListAndDetailDoNotExposeAnswersOrHiddenTestCaseOutput` 通过 | 通过 |
| TC-HW-05 | FR-HW-02 | UI-HWK-05；API-HWK-07；DB-HWK-04 | 学生为课程成员；作业已发布且未截止 | 提交文本、客观题或代码答案 | 生成提交记录，返回提交编号、时间和初始评测/批阅状态 | `studentSubmitsPublishedTextHomeworkAndReceivesSubmissionReceipt`、前端学生提交用例通过 | 通过 |
| TC-HW-06 | FR-HW-02 | API-HWK-07；错误码 `HWK_4004` | 作业已超过截止时间且不允许迟交 | 学生提交作业 | 返回截止错误，不生成有效提交 | `studentCannotSubmitAfterDeadlineWhenLateSubmitIsDisabled` 通过 | 通过 |
| TC-HW-07 | FR-HW-03 | UI-HWK-06；API-HWK-08；DB-HWK-04 | 作业允许重复提交，学生提交多次 | 查询我的提交历史 | 历史完整，仅最新提交 `is_final=1` | `studentSubmissionHistoryKeepsPreviousVersionsAndMarksOnlyLatestFinal`、前端历史用例通过 | 通过 |
| TC-HW-08 | FR-HW-03 | UI-HWK-06；API-HWK-09、10；DB-HWK-04 | 教师/助教有课程管理权限；存在多名学生提交 | 查询提交列表，按学生和状态筛选，读取详情 | 支持分页、筛选、详情读取 | `courseManagerListsSubmissionsWithPaginationAndReadsSubmissionDetail`、`courseManagerFiltersSubmissionsByStudentAndStatuses` 通过 | 通过 |
| TC-HW-09 | FR-HW-04 | API-HWK-07、11；DB-HWK-05 | 客观题作业配置标准答案和分值 | 学生提交客观题答案，查询评测结果 | 自动计算分数，生成评测记录 | `objectiveHomeworkSubmissionCreatesEvaluationRecordAndResultView` 通过 | 通过 |
| TC-HW-10 | FR-HW-04 | UI-HWK-05、07；API-HWK-07、11；DB-HWK-03、05 | 代码题配置 IO 用例和语言白名单 | 学生提交代码，查询评测结果 | 返回评测状态、通过用例数和分数 | `codeHomeworkSubmissionEvaluatesIoCasesAndTeacherCanReevaluate`、前端代码评测展示用例通过 | 通过 |
| TC-HW-11 | FR-HW-04；NFR-HW-01 | API-HWK-11；DB-HWK-04、05 | 代码提交触发错误结果 | 查询评测结果和提交详情 | 评测状态记录失败，提交记录不丢失 | `codeHomeworkEvaluationFailurePreservesSubmissionAndRecordsFailedStatus` 通过 | 通过 |
| TC-HW-12 | FR-HW-04、05 | UI-HWK-08；API-HWK-12；DB-HWK-05、06 | 已有提交和评测记录；教师提供重评理由 | 教师触发重评 | 新增评测记录，保留旧记录，写入重评日志 | `codeHomeworkSubmissionEvaluatesIoCasesAndTeacherCanReevaluate`、`objectiveReevaluationUpdatesSubmissionSummary` 通过 | 通过 |
| TC-HW-13 | FR-HW-05 | UI-HWK-08；API-HWK-13；DB-HWK-04、06 | 教师/助教有课程管理权限；存在待批阅提交 | 填写人工分数和评语 | 更新 manualScore、finalScore、comment，写入日志 | `courseManagerReviewsSubmissionAndReadsReviewAuditLogs`、前端教师批阅用例通过 | 通过 |
| TC-HW-14 | FR-HW-05 | API-HWK-13；错误码 `HWK_4008` | 作业总分 100，教师填写超出总分的分数 | 提交批阅 | 返回分数范围错误，不更新成绩 | `teacherReviewRejectsScoreOutsideHomeworkTotalScore` 通过 | 通过 |
| TC-HW-15 | FR-HW-05；NFR-HW-03 | API-HWK-12、13、21；DB-HWK-06 | 存在批阅、重评和发布成绩操作 | 查询批阅日志 | 日志记录操作人、时间、原因和分数变化 | `courseManagerReviewsSubmissionAndReadsReviewAuditLogs`、`studentCannotReadPrivateReviewLogs` 通过 | 通过 |
| TC-HW-16 | FR-HW-06 | UI-HWK-07；API-HWK-10、11、14 | 学生成绩已发布 | 学生查询详情和反馈 | 展示允许公开的评测摘要、成绩和教师评语 | `scorePublishExposesStudentFeedbackAndHomeworkSourceGrades` 通过 | 通过 |
| TC-HW-17 | FR-HW-06；NFR-HW-04 | API-HWK-08、10、11 | 学生成绩未发布 | 学生查询历史、详情和评测结果 | 不显示未公开最终分和教师评语 | `studentHistoryAndDetailHideUnpublishedScoresAndTeacherComment`、`objectiveHomeworkSubmissionShowsEvaluationButHidesUnpublishedFinalScore` 通过 | 通过 |
| TC-HW-18 | FR-HW-06；NFR-HW-02 | UI-HWK-09；API-HWK-15；DB-HWK-04、05 | 多名学生提交和未提交 | 教师查询统计和未提交名单分页 | 展示提交数、未提交数、平均分等统计 | `teacherQueriesHomeworkStatisticsWithUnsubmittedStudentsAndScoreSummary`、`teacherQueriesHomeworkStatisticsWithPaginatedUnsubmittedStudentsForNfrPerformance` 通过 | 通过 |
| TC-HW-N01 | NFR-HW-01 | API-HWK-03、07、11、13 | 模拟通知投递失败、评测失败、重复提交冲突 | 执行发布、提交、查询和批阅 | 主数据保持一致，错误以受控响应返回 | `publishKeepsHomeworkPublishedWhenNotificationDeliveryFails`、`submitReturnsControlledConflictWhenSubmissionVersionIsAlreadyUsed` 通过 | 通过 |
| TC-HW-N02 | NFR-HW-02 | API-HWK-05、09、15；索引 | 作业列表、提交列表、统计使用分页参数和基础规模样本 | 查询列表和统计 | 返回分页结构，响应受控 | 后端分页统计用例和前端 API route 用例通过；大规模压测待补充 | 有条件通过 |
| TC-HW-N03 | NFR-HW-03 | API-HWK-10、20、21；DB-HWK-04、05、06 | 存在多次提交、评测、重评、批阅 | 查询详情、评测日志、批阅日志 | 提交和日志可追溯 | 迁移测试和控制器日志用例通过 | 通过 |
| TC-HW-N04 | NFR-HW-04 | 全部 HWK 接口；DB-HWK-02、03、04、05 | 非成员、他人提交、隐藏用例、私有日志 | 越权访问或查询敏感数据 | 返回 `HWK_4031` 或隐藏敏感字段 | `studentCannotReadAnotherStudentsSubmission`、`nonMemberStudentCannotSubmitHomework`、隐藏用例/日志用例通过 | 通过 |
| TC-HW-N05 | NFR-HW-05 | 全部 HWK 流程 | 稳定测试数据、MockMvc、Vitest、H2 迁移 | 重复执行自动化测试 | 核心流程可重复验证 | 本文第 8 章命令已通过 | 通过 |

### 7.3 前端 HWK 用例摘要

| 测试文件 | 覆盖内容 | 结果 |
| --- | --- | --- |
| `frontend/tests/unit/hwk/homeworksApi.spec.ts` | API-HWK-01 ~ 21 路由构造、请求方法、参数、ApiResponse 解包 | 6 条通过 |
| `frontend/tests/unit/hwk/HomeworkStudentListView.spec.ts` | 学生作业列表、详情链接、空状态 | 2 条通过 |
| `frontend/tests/unit/hwk/HomeworkStudentView.spec.ts` | 学生详情、文本提交、空提交校验、代码语言选择、评测结果、学习进度记录、断点恢复 | 7 条通过 |
| `frontend/tests/unit/hwk/HomeworkSubmissionHistoryView.spec.ts` | 学生历史、教师分页列表、教师批阅、重评、筛选、空状态 | 6 条通过 |
| `frontend/tests/unit/hwk/HomeworkTeacherView.spec.ts` | 教师创建/编辑、代码题测试用例校验、发布/关闭、批阅入口、统计、成绩发布 | 7 条通过 |

## 8 测试执行日志

### 8.1 后端 HWK 执行日志

| 日志编号 | 时间 | 命令/测试类 | 执行内容 | 结果 |
| --- | --- | --- | --- | --- |
| HWK-LOG-001 | 2026-06-09 16:30 | Maven 目标测试 | 普通沙箱执行 HWK 后端目标测试 | 因 `backend/target/classes/schema.sql` 写入受限失败，未进入断言阶段 |
| HWK-LOG-002 | 2026-06-09 16:31 | `HomeworkBearerAuthControllerTest` | Bearer 登录态、AUTH/CRS 成员联动、作业可见性、提交和批阅权限 | 2 条通过 |
| HWK-LOG-003 | 2026-06-09 16:31 | `HomeworkControllerTest` | HWK 控制器主流程、异常、权限、评测、批阅、统计、通知和成绩来源 | 35 条通过 |
| HWK-LOG-004 | 2026-06-09 16:31 | `HomeworkMigrationTest` | HWK 迁移语法、外键、唯一约束、提交/评测/批阅日志表契约 | 6 条通过 |
| HWK-LOG-005 | 2026-06-09 16:31 | `HomeworkSubmissionServiceTest` | 重复提交版本冲突返回受控业务错误 | 1 条通过 |
| HWK-LOG-006 | 2026-06-09 16:31 | Maven 汇总 | `Tests run: 44, Failures: 0, Errors: 0, Skipped: 0` | 构建成功 |

### 8.2 前端 HWK 执行日志

| 日志编号 | 时间 | 命令/测试文件 | 执行内容 | 结果 |
| --- | --- | --- | --- | --- |
| HWK-LOG-007 | 2026-06-09 16:31 | Vitest 目标测试 | 普通沙箱执行 HWK 前端单测 | 因 esbuild 子进程 `spawn EPERM` 启动失败，未进入断言阶段 |
| HWK-LOG-008 | 2026-06-09 16:31 | `homeworksApi.spec.ts` | HWK API wrapper 路由、方法、参数和响应处理 | 6 条通过 |
| HWK-LOG-009 | 2026-06-09 16:31 | `HomeworkStudentListView.spec.ts` | 学生作业列表和空状态 | 2 条通过 |
| HWK-LOG-010 | 2026-06-09 16:31 | `HomeworkStudentView.spec.ts` | 学生作业详情、提交、校验、代码评测、学习记录 | 7 条通过 |
| HWK-LOG-011 | 2026-06-09 16:31 | `HomeworkSubmissionHistoryView.spec.ts` | 提交历史、教师筛选、批阅、重评、日志刷新 | 6 条通过 |
| HWK-LOG-012 | 2026-06-09 16:31 | `HomeworkTeacherView.spec.ts` | 教师创建/编辑、发布/关闭、统计、成绩发布 | 7 条通过 |
| HWK-LOG-013 | 2026-06-09 16:31 | Vitest 汇总 | `Test Files 5 passed (5)`、`Tests 28 passed (28)` | 构建成功 |

## 9 手工测试与联调确认

| 手测编号 | 模块 | 场景 | 操作要点 | 预期结果 | 当前结果 |
| --- | --- | --- | --- | --- | --- |
| MAN-HWK-001 | HWK/AUTH/CRS | 教师创建并发布作业 | 浏览器登录教师账号，进入课程，创建文本/客观题/代码题作业并发布 | 作业保存成功，学生可见，教师端状态刷新 | 待手工验收 |
| MAN-HWK-002 | HWK | 学生提交作业 | 学生登录后进入作业详情，提交文本答案、客观题答案和代码答案 | 提交成功，显示提交时间和初始评测状态 | 待手工验收 |
| MAN-HWK-003 | HWK | 教师批阅与重评 | 教师查看提交列表，筛选学生，录入人工分数/评语，触发重评 | 分数、评语、评测状态和日志刷新 | 待手工验收 |
| MAN-HWK-004 | HWK | 学生查看反馈 | 成绩发布前后分别查看详情、历史和评测结果 | 发布前隐藏最终分，发布后展示允许公开的反馈 | 待手工验收 |
| MAN-HWK-005 | HWK | 权限边界 | 非成员、其他学生、无课程管理权限教师访问 HWK 页面和接口 | 页面提示权限不足，接口返回受控错误，不泄露敏感字段 | 待手工验收 |
| MAN-HWK-006 | HWK | 页面状态 | 人为制造加载中、空列表、接口失败、会话过期、发布配置不完整 | 页面有清晰提示，操作入口禁用或引导正确 | 待手工验收 |
| MAN-HWK-007 | HWK/LAB | 真实代码评测沙箱 | 使用真实 Docker 沙箱提交 Python/Java 代码，包含 AC、WA、编译错误、超时 | 状态、日志、资源限制和隐藏用例显示策略正确 | 待专项测试 |
| MAN-HWK-008 | HWK | 基础性能 | 准备大批量作业、提交和未提交学生，查询列表/统计 | 分页正常，响应时间满足测试负责人设定阈值 | 待专项测试 |
| MAN-HWK-009 | HWK/LRN/GRD | 跨模块联调 | 发布作业、完成评测/批阅、发布成绩，查看通知中心、学习任务、成绩同步 | LRN 通知/提醒生成，GRD 可同步 HWK 来源成绩 | 待联调确认 |

## 10 缺陷、风险与处理建议

| 风险编号 | 风险说明 | 影响范围 | 建议处理 |
| --- | --- | --- | --- |
| R-HWK-001 | 当前未执行真实浏览器端到端验收 | UI-HWK-01 ~ UI-HWK-09 | 测试负责人整合后按 MAN-HWK-001 ~ MAN-HWK-006 补跑 |
| R-HWK-002 | 当前未执行真实 Docker 沙箱并发和资源限制专项测试 | FR-HW-04、NFR-HW-01、NFR-HW-02、NFR-HW-04 | 使用真实沙箱环境补充多语言、错误、超时和并发样本 |
| R-HWK-003 | LRN/GRD 跨模块生产环境联调尚未记录完整结果 | FR-HW-06、NFR-HW-03 | 在统一测试环境执行作业发布、成绩发布、通知中心和成绩同步闭环 |
| R-HWK-004 | Maven 和 Vitest 在普通沙箱下存在写入/子进程权限限制 | 本地验证流程 | 本地开发机可直接运行；受限环境下需使用已批准的提权命令 |

## 11 验收结论

| 验收项 | 结论 | 说明 |
| --- | --- | --- |
| 功能覆盖 | 有条件通过 | FR-HW-01 ~ FR-HW-06 均有自动化覆盖，真实浏览器验收待补充 |
| 接口覆盖 | 通过 | API-HWK-01 ~ API-HWK-21 的主路由、权限和错误分支由后端/前端自动化覆盖 |
| 页面覆盖 | 有条件通过 | Vue 单测覆盖主要页面状态和交互，视觉与端到端流程待手工确认 |
| 数据一致性 | 通过 | DB-HWK-01 ~ DB-HWK-07 关键约束、提交版本、评测和日志记录已由迁移测试覆盖 |
| 权限与安全 | 通过 | 非成员、他人提交、隐藏用例、私有日志、未发布成绩均有自动化覆盖 |
| 非功能 | 有条件通过 | 可靠性、可追踪性、安全性、可测试性已覆盖；真实压测和沙箱专项测试待补充 |
| 最终结论 | 有条件通过 | 当前文档可交给测试负责人整合；需补充 MAN-HWK-001 ~ MAN-HWK-009 的统一验收记录 |

## 12 附录

### 12.1 执行命令

```powershell
cd D:\repos\OnlineJudge\backend
& 'D:\Program Files\apache-maven-3.9.16\bin\mvn.cmd' test '-Dtest=HomeworkControllerTest,HomeworkBearerAuthControllerTest,HomeworkMigrationTest,HomeworkSubmissionServiceTest'

cd D:\repos\OnlineJudge\frontend
& 'D:\Program Files\nodejs\node.exe' '.\node_modules\vitest\vitest.mjs' run tests/unit/hwk/homeworksApi.spec.ts tests/unit/hwk/HomeworkStudentListView.spec.ts tests/unit/hwk/HomeworkStudentView.spec.ts tests/unit/hwk/HomeworkSubmissionHistoryView.spec.ts tests/unit/hwk/HomeworkTeacherView.spec.ts --pool=threads
```

### 12.2 本次执行摘要

| 项目 | 摘要 |
| --- | --- |
| 后端 HWK 自动化测试 | 4 个测试类，44 passed / 0 failed / 0 errors / 0 skipped |
| 前端 HWK 自动化测试 | 5 files passed / 28 tests passed |
| 自动化覆盖 | 作业创建/发布、提交、历史、自动评测、重评、批阅、统计、权限、隐藏数据、迁移约束、AUTH/CRS 联动 |
| 手工/联调状态 | 待测试负责人整合后补充真实浏览器、真实沙箱、LRN/GRD 联调记录 |
