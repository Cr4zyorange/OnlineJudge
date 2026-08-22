# TST-DOC-05 LAB 实训实验测试文档

| 文档编号 | TST-DOC-05 |
| --- | --- |
| 文档名称 | LAB 实训实验测试文档 |
| 项目名称 | 在线教学与实训平台 |
| 所属阶段 | 系统测试与验收测试 |
| 报告版本 | V1.0 |
| 编写日期 | 2026-06-11 |
| 编写人 | LAB 模块负责人 |
| 对应 issue | #156 TST-DOC-05 LAB 实训实验测试文档编写 |
| 测试范围 | LAB 实验创建与发布、学生查看与提交、提交历史、自动评测、实验报告、教师评分、结果展示、实验统计、权限、安全、跨模块通知与成绩来源 |
| 测试结论 | LAB 目标自动化测试已通过；真实浏览器端到端、真实 Docker 沙箱压力与 LRN/GRD 统一环境联调仍需测试负责人整合确认 |

## 1 文档控制

### 1.1 修订记录

| 版本 | 日期 | 修订人 | 修订说明 |
| --- | --- | --- | --- |
| V1.0 | 2026-06-11 | LAB 模块负责人 | 按 #152 统一结构整理 LAB 测试范围、测试数据、用例追踪、自动化覆盖、执行日志模板、手工验收点和残余风险 |

### 1.2 审批记录

| 角色 | 姓名/负责人 | 审批意见 | 日期 |
| --- | --- | --- | --- |
| 项目负责人 | 待填写 | 待审批 | 2026-06-11 |
| 测试负责人 | @MontesquieuE | 待整合确认 | 2026-06-11 |
| LAB 模块负责人 | LAB 负责人 | 待确认 | 2026-06-11 |

## 2 测试概述

本文件用于记录 LAB 实训实验模块在当前版本下的测试依据、测试环境、测试数据、测试用例、执行结果、手工验收清单、缺陷风险和验收结论。覆盖范围对齐 `FR-LAB-01 ~ FR-LAB-08`、`NFR-LAB-01 ~ NFR-LAB-05`、`UI-LAB-01 ~ UI-LAB-08`、`API-LAB-01 ~ API-LAB-19`、`DB-LAB-01 ~ DB-LAB-09`、`TC-LAB-01 ~ TC-LAB-41`、`TC-LAB-N01 ~ TC-LAB-N05` 与 `MAN-LAB-001 ~ MAN-LAB-011`。#222 新增 TC-LAB-34 ~ TC-LAB-41 与 MAN-LAB-011，现已取得定向/完整自动化和真实浏览器证据；既有历史执行计数仍作为基线单独保留。

当前仓库已经具备 LAB 后端 Spring Boot 自动化测试和前端 Vue/Vitest 单元测试，既有基线覆盖实验创建、提交评测、报告、评分、结果、统计和跨模块样本。#222 的提交源文件可信元数据、API-LAB-10 安全 DTO、API-LAB-19 教师受控下载、DB-LAB-09、UI-LAB-06 双入口和事务补偿已由新增自动化与 MAN-LAB-011 验证。真实 Docker 沙箱压力和 LRN/GRD 统一环境联调仍按既有手工/集成验收项管理。

## 3 测试依据

| 序号 | 文档/代码依据 | 用途 |
| --- | --- | --- |
| 1 | `docs/开发/LAB-实训实验模块开发流程.md` | LAB 主流程、开发顺序、P0 闭环、权限与跨模块要求 |
| 2 | `docs/最终提交/软件需求规格说明书.md` | `FR-LAB-*`、`NFR-LAB-*` 需求和验收来源 |
| 3 | `docs/最终提交/软件概要设计说明书.md` | 模块边界、跨模块依赖、实验闭环和性能约束来源 |
| 4 | `docs/最终提交/软件详细设计说明书.md` | `UI-LAB-*`、`API-LAB-*`、`DB-LAB-*`、异常码和 `TC-LAB-*` 追踪矩阵来源 |
| 5 | `docs/过程/需求/实训实验模块（概要设计负责人负责）.md` | LAB 过程需求和范围补充 |
| 6 | `docs/过程/概要/概要设计说明书（完整协作底稿）—实训模块.md` | LAB 过程概要设计、页面/API 草案、性能指标和跨模块协作补充 |
| 7 | `docs/过程/详细设计/LAB-实训实验模块-详细设计提交稿.md` | LAB 页面、接口、数据表、异常、安全和测试编号补充 |
| 8 | `backend/src/test/java/com/onlinejudge/lab/controller/LabExperimentControllerTest.java` | 实验创建、发布、关闭、成绩发布、统计、权限和基础异常自动化测试 |
| 9 | `backend/src/test/java/com/onlinejudge/lab/controller/LabSubmissionControllerTest.java` | 提交、历史、评测、报告、评分、结果展示、成绩来源和权限自动化测试 |
| 10 | `backend/src/test/java/com/onlinejudge/lab/service/LabEvaluationServiceTest.java` | 评测器异常时状态持久化与失败留痕自动化测试 |
| 11 | `backend/src/test/java/com/onlinejudge/lab/service/LabExperimentTransactionTest.java` | 实验与测试用例保存/更新的事务回滚自动化测试 |
| 12 | `backend/src/test/java/com/onlinejudge/lab/database/LabExperimentMigrationTest.java` | LAB 迁移脚本、报告表、评分表、评分变更表自动化测试 |
| 13 | `frontend/tests/unit/lab/LabTeacherView.spec.ts` | 教师端实验管理、统计、提交查看、报告下载、评分交互自动化测试 |
| 14 | `frontend/tests/unit/lab/LabStudentView.spec.ts` | 学生端实验详情、提交、断点恢复、评测结果、报告上传/下载、成绩展示自动化测试 |
| 15 | `frontend/tests/unit/lab/LabSubmissionHistoryView.spec.ts` | 提交历史、空状态、失败提示自动化测试 |
| 16 | `frontend/tests/unit/api/labs.spec.ts` | 报告下载、报告评分、实验统计 API wrapper 自动化测试 |
| 17 | `database/migrations/20260525_02_create_lab_experiment.sql`、`20260526_01_create_lab_submission.sql`、`20260604_01_create_lab_report.sql`、`20260605_02_create_lab_score.sql`、`20260606_01_add_lab_published_at.sql`、`20260822_02_create_lab_submission_source_file.sql` | LAB 数据表、索引、约束、迁移增量脚本依据；DB-LAB-09 与补偿由 TC-LAB-41 验证 |

## 4 测试范围

### 4.1 功能与非功能范围

| 编号 | 测试对象 | 主要验证点 | 当前覆盖状态 |
| --- | --- | --- | --- |
| FR-LAB-01 | 实验创建与发布 | 教师创建草稿、读取列表、读取详情、编辑、发布、截止、删除草稿、发布通知 | 后端和前端自动化已覆盖 |
| FR-LAB-02 | 学生实验查看与提交 | 学生查看、代码/文件提交、可信元数据、类型/大小、存储后事务补偿 | TC-LAB-34/38/39/41 通过 |
| FR-LAB-03 | 提交历史与版本管理 | 历史版本、API-LAB-10 安全 DTO、指定提交版本源文件核对 | TC-LAB-34 ~ 38/41 通过 |
| FR-LAB-04 | 实验自动评测 | 自动评测受理、评测通过、错误答案、超时、编译错误、运行错误、教师重评、隐藏测试用例保护 | 后端和前端自动化已覆盖；真实沙箱压力待专项确认 |
| FR-LAB-05 | 实验报告管理 | 学生上传报告、重复上传版本递增、教师查看报告、报告下载、报告评分、格式与大小限制 | 后端和前端自动化已覆盖 |
| FR-LAB-06 | 教师评分与评语 | 查看安全源文件元数据；源文件/报告独立下载；`canManageCourse` 授权；评分与变更留痕 | TC-LAB-35 ~ 40/MAN-LAB-011 通过 |
| FR-LAB-07 | 实验结果展示与学生反馈 | 成绩发布前隐藏教师评分与报告评分，发布后展示最终分、评语、报告反馈和评测摘要 | 后端和前端自动化已覆盖 |
| FR-LAB-08 | 实验统计与查询 | 教师查看提交率、未提交名单、评测完成率、平均分、分数分布、逾期提交数 | 后端和前端自动化已覆盖 |
| NFR-LAB-01 | 可靠性 | 提交与源文件按版本绑定，存储成功但事务失败时成功补偿删除，评测失败不丢提交 | TC-LAB-34/35/38/41 通过 |
| NFR-LAB-02 | 性能 | 提交快速受理、评测异步处理、统计查询和列表查询支持基础规模样本 | 自动化覆盖基础样本；真实压测待补充 |
| NFR-LAB-03 | 可追踪性 | 提交、源文件资产、评测、报告、评分和版本信息可追踪 | TC-LAB-34 ~ 38/41 通过 |
| NFR-LAB-04 | 安全性 | `canManageCourse`、学生本人排除、归属校验、响应头与公共 DTO 无内部信息 | TC-LAB-36 ~ 40/MAN-LAB-011 通过 |
| NFR-LAB-05 | 可测试性 | 评测器/文件服务可替换，元数据、权限、兼容、异常、迁移和 UI 状态可复现 | TC-LAB-34 ~ 41/MAN-LAB-011 通过 |

### 4.2 页面、接口、数据表覆盖

| 类别 | 编号范围 | 覆盖说明 |
| --- | --- | --- |
| 页面 | `UI-LAB-01 ~ UI-LAB-08` | 前端测试覆盖教师实验管理页、学生实验详情页、提交历史页、评分与结果展示相关交互；真实浏览器视觉与完整端到端流程待手工验收 |
| 接口 | `API-LAB-01 ~ API-LAB-19` | API-LAB-10 新 DTO 与 API-LAB-19 已由 TC-LAB-34 ~ 40 验证 |
| 数据表 | `DB-LAB-01 ~ DB-LAB-09` | DB-LAB-09 字段/约束、绑定与事务补偿由 TC-LAB-41 验证 |
| 跨模块 | AUTH、CRS、LRN、GRD、CRS 文件、HWK #214 | API-LAB-19 只依赖 AUTH 与 CRS `canManageCourse`；LAB 报告、CRS 资源、HWK 文件保持业务边界，仅底层 FileStorageService 可共享 |

### 4.3 跨模块集成追踪编号

以下 `IC-LAB-*` 编号用于满足 issue #156 的统一整合要求。它们不是详细设计说明书里的正式编号，而是基于现有设计文档与仓库实现补充的文档级追踪标识，用于把 LAB 与 AUTH、CRS、LRN、GRD、公共评测/存储契约之间的集成测试点标出来。

| 编号 | 集成契约 | 对应设计依据 | 已映射用例 |
| --- | --- | --- | --- |
| IC-LAB-01 | AUTH 当前用户与 CRS 课程教师/课程成员校验共同驱动实验创建、查看、提交与评分 | `docs/开发/LAB-实训实验模块开发流程.md`、`docs/开发/00-基础设施开发约定.md`、`FR-LAB-01/02/06` | `TC-LAB-01`、`TC-LAB-08`、`TC-LAB-10`、`TC-LAB-13`、`TC-LAB-26`、`TC-LAB-32` |
| IC-LAB-02 | LAB 发布实验后向 LRN 发送实验发布通知 | `FR-LAB-01`、概要设计 `S-03 教师发布实验` | `TC-LAB-04` |
| IC-LAB-03 | LAB 发布成绩后向 LRN 发送成绩发布通知，并向 GRD 暴露 LAB 来源成绩 | `FR-LAB-07`、`FR-LAB-08`、概要设计 `S-07 教师评分与发布` | `TC-LAB-07`、`TC-LAB-30` |
| IC-LAB-04 | 学生/教师页面通过共享 API 与权限上下文一致展示实验、提交、统计和结果状态 | `UI-LAB-01 ~ UI-LAB-08`、`API-LAB-03/09/10/14/18` | `TC-LAB-08`、`TC-LAB-11`、`TC-LAB-23`、`TC-LAB-29`、`TC-LAB-33` |
| IC-LAB-05 | LAB 与公共评测抽象、文件存储契约协作，支持代码提交、报告上传、重评和结果留痕 | `docs/开发/00-基础设施开发约定.md` 中 `common.evaluation` / `common.storage` | `TC-LAB-15`、`TC-LAB-18`、`TC-LAB-21`、`TC-LAB-24`、`TC-LAB-25` |
| IC-LAB-N01 | 越权访问跨模块资源默认拒绝，不允许前端身份伪造绕过 AUTH/CRS 校验 | 安全边界、课程成员约束 | `TC-LAB-10`、`TC-LAB-14`、`TC-LAB-N04` |
| IC-LAB-N02 | 通知与来源成绩只在正确状态流转后发生，不提前泄露实验或成绩结果 | 发布/成绩发布状态机 | `TC-LAB-04`、`TC-LAB-07`、`TC-LAB-28`、`TC-LAB-30` |
| IC-LAB-N03 | 提交、报告、评分、评分变更、来源成绩在跨模块链路中保持可追踪 | 可追踪性与来源成绩同步 | `TC-LAB-21`、`TC-LAB-25`、`TC-LAB-27`、`TC-LAB-30`、`TC-LAB-N03` |
| IC-LAB-N04 | 隐藏测试用例、教师评分和报告评分在前后端都不应向学生提前泄露 | 安全性与结果展示策略 | `TC-LAB-15`、`TC-LAB-23`、`TC-LAB-28`、`TC-LAB-29` |
| IC-LAB-N05 | 通知发布器、来源成绩客户端、评测器和文件存储相关集成点均可被替身/样本稳定验证 | 可测试性与替身契约 | `TC-LAB-19`、`TC-LAB-20`、`TC-LAB-N05` |

### 4.4 不在本次自动化确认范围

| 范围项 | 说明 | 处理方式 |
| --- | --- | --- |
| 真实浏览器端到端验收 | 当前未执行从登录、进入课程、教师发布实验、学生提交、教师评分、学生查看结果的完整浏览器流程 | 作为手工验收用例 `MAN-LAB-001 ~ MAN-LAB-007` |
| 真实 Docker 沙箱压力 | 自动化使用 fake sandbox 或可控样本，未执行真实 Docker 并发、资源限制和多语言压力 | 作为专项测试 `MAN-LAB-008` |
| 大规模性能压测 | 自动化覆盖基础规模统计和异步评测轮询，不代表生产规模并发性能 | 作为专项测试 `MAN-LAB-009` |
| LRN/GRD 统一环境联调 | 代码级通知事件和来源成绩样本已覆盖，但统一环境通知中心与成绩同步页仍需确认 | 作为跨模块联调用例 `MAN-LAB-010` |
| LAB 源文件受控下载浏览器链路 | API-LAB-19、UI-LAB-06 双入口、学生本人排除和失败提示 | `MAN-LAB-011` 已完成；证据位于 `output/playwright/issue-222/01~06` |

## 5 测试环境

| 环境项 | 内容 |
| --- | --- |
| 操作系统 | macOS 26.5.1 |
| 后端运行环境 | Java 25.0.1，Spring Boot 3.4.5，Maven 3.9.11，JUnit 5，MockMvc，H2 MySQL mode |
| 前端运行环境 | Node.js 25.8.2，npm 11.11.1，Vue 3.5，Vite 6.3，Vitest 3.2.4，jsdom |
| 数据库 | 自动化测试使用 H2 内存库；迁移脚本按 MySQL 兼容约束编写 |
| 鉴权方式 | 后端测试使用 `X-User-Id`、`X-User-Role`、课程权限 header 或测试上下文；前端测试 mock API wrapper、浏览器存储与路由 |
| 执行日期 | 2026-06-11 |

## 6 测试数据

| 数据类别 | 数据说明 | 使用模块 |
| --- | --- | --- |
| 教师用户 | `X-User-Id=501` 等课程管理者；含有可管理课程和课程学生列表 header | LAB、AUTH、CRS |
| 学生用户 | `X-User-Id=601/602/703` 等课程成员与非成员学生 | LAB、AUTH、CRS |
| 课程数据 | `courseId=101/202/404/513/530` 等测试课程，覆盖教师、成员、非成员、成绩发布与统计分支 | LAB、CRS |
| 实验数据 | 草稿、已发布、已截止、已发布成绩等状态实验；开启/关闭自动评测；要求/不要求实验报告 | LAB |
| 测试用例数据 | 公开/隐藏测试用例、不同分值权重、时间限制、内存限制、顺序号 | LAB |
| 提交数据 | 代码文本、源文件、语言、提交状态、评测状态、版本号、是否当前有效版本 | LAB |
| 评测结果数据 | `PENDING`、`RUNNING`、`ACCEPTED`、`WRONG_ANSWER`、`TIME_LIMIT_EXCEEDED`、`COMPILE_ERROR`、`RUNTIME_ERROR`、`SYSTEM_ERROR` | LAB |
| 报告数据 | PDF 报告、版本号、文件名、文件大小、报告评分、报告评语 | LAB |
| 评分数据 | 自动分、报告分、人工分、最终分、教师评语、评分变更原因 | LAB、GRD |
| 统计数据 | 已提交人数、未提交人数、评测完成率、平均分、分数分布、逾期提交数 | LAB |
| 跨模块数据 | `LAB_EXPERIMENT_PUBLISHED`、`EXPERIMENT_SCORE_PUBLISHED` 通知事件；GRD 来源成绩 `SourceGradeType.LAB` 样本 | LAB、LRN、GRD |

## 7 测试用例汇总

### 7.1 自动化执行结果

| 测试类别 | 命令 | 执行结果 |
| --- | --- | --- |
| 后端 LAB 相关测试 | `mvn test "-Dtest=LabExperimentControllerTest,LabSubmissionControllerTest,LabExperimentMigrationTest,LabExperimentTransactionTest,LabEvaluationServiceTest"` | 47 条通过，0 失败，0 错误，0 跳过 |
| 前端 LAB 单元测试 | `npm run test:unit -- tests/unit/api/labs.spec.ts tests/unit/lab/LabTeacherView.spec.ts tests/unit/lab/LabStudentView.spec.ts tests/unit/lab/LabSubmissionHistoryView.spec.ts` | 4 个测试文件通过，29 条测试通过 |
| #222 后端定向回归 | `mvn test "-Dtest=LabSubmissionSourceFileMigrationTest,LabSubmissionControllerTest"` | 41/41 通过 |
| 后端完整回归 | `mvn -q test` | 302 tests，0 failures，0 errors，1 个 Docker-only skip |
| #222 前端完整验证 | `npm run test:unit`、`npm run typecheck`、`npm run build` | 53 files / 521 tests、类型检查、构建均通过 |

前两行为 #222 之前既有基线；#222 使用后三行及 MAN-LAB-011 作为新增链路证据。

### 7.2 LAB 核心用例表

| 用例编号 | 对应需求 | 覆盖对象 | 前置条件/测试数据 | 操作步骤 | 预期结果 | 自动化覆盖/证据 | 实际结果 | 通过状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TC-LAB-01 | FR-LAB-01 | `UI-LAB-01`、`UI-LAB-04`、`API-LAB-01/02/03`、`DB-LAB-01/02` | 教师具备课程管理权限；准备标题、截止时间、语言、测试用例 | 创建实验草稿，读取列表和详情 | 返回实验 ID，状态为 `DRAFT`，测试用例随实验保存 | `teacherCreatesListsAndReadsLabThroughDocumentedApis`、教师端创建草稿用例覆盖 | 自动化覆盖 | 通过 |
| TC-LAB-02 | FR-LAB-01 | `API-LAB-01`、异常码 `LAB-400-01/LAB-400-02` | 标题为空、截止时间非法、满分非法 | 提交非法创建请求 | 返回 400，页面保留输入并提示错误 | `controllerRejectsInvalidPayloadAndPermissionViolations`、教师端非法表单拦截用例覆盖 | 自动化覆盖 | 通过 |
| TC-LAB-03 | FR-LAB-01 | `API-LAB-04/06/07`、`UI-LAB-04` | 已创建草稿实验 | 更新实验，发布实验，截止实验 | 草稿可更新；发布后状态为 `PUBLISHED`；截止后状态为 `CLOSED` | `teacherUpdatesPublishesClosesAndDeletesDraftLab`、教师端更新/发布/截止用例覆盖 | 自动化覆盖 | 通过 |
| TC-LAB-04 | FR-LAB-01 | `API-LAB-06`、LRN 事件 | 已创建草稿实验，存在课程学生列表 | 教师发布实验 | 发送 `LAB_EXPERIMENT_PUBLISHED` 通知事件，学生可见 | `teacherUpdatesPublishesClosesAndDeletesDraftLab`、`studentCourseMemberCanReadPublishedLabsButCannotSeeHiddenExpectedOutput` | 自动化覆盖 | 通过 |
| TC-LAB-05 | FR-LAB-01 | `API-LAB-05`、`UI-LAB-04` | 存在草稿实验 | 删除草稿实验 | 删除成功，草稿不再出现在教师列表 | `teacherUpdatesPublishesClosesAndDeletesDraftLab`、教师端删除草稿用例覆盖 | 自动化覆盖 | 通过 |
| TC-LAB-06 | FR-LAB-08 | `API-LAB-14`、`UI-LAB-08` | 存在已发布实验和课程学生 | 查询实验统计 | 返回提交率、未提交名单、评测完成率、平均分和分数分布 | `teacherQueriesLabStatisticsWithUnsubmittedStudentsScoreDistributionAndLateCount`、教师端统计面板用例覆盖 | 自动化覆盖 | 通过 |
| TC-LAB-07 | FR-LAB-01/07 | `API-LAB-18`、LRN 事件 | 实验已截止，教师准备发布成绩 | 发布实验成绩 | 状态变为 `SCORE_PUBLISHED`，发送成绩发布通知 | `teacherCanReleaseScoresAfterClosingLab`、教师端发布成绩用例覆盖 | 自动化覆盖 | 通过 |
| TC-LAB-08 | FR-LAB-02 | `UI-LAB-02`、`API-LAB-03/08`、`DB-LAB-01/03` | 学生为课程成员，实验已发布 | 查看实验详情、提交代码 | 可看到说明和公开测试用例，提交成功并返回 `PENDING` 状态 | `loads published lab detail and submits code successfully`、`studentCanSubmitCodeTwiceAndVersionIncrements` | 自动化覆盖 | 通过 |
| TC-LAB-09 | FR-LAB-02 | `API-LAB-08`、异常码 `LAB-400-03/LAB-400-04/LAB-409-01` | 提交内容为空、语言不支持、实验已截止 | 分别提交非法请求 | 返回对应错误码，且不生成有效提交 | `submissionRejectsMissingContentUnsupportedLanguageAndExpiredLab`、学生端前端校验用例覆盖 | 自动化覆盖 | 通过 |
| TC-LAB-10 | FR-LAB-02 | `API-LAB-08`、异常码 `LAB-403-01/LAB-400-06` | 非课程成员、教师误用学生提交接口、源文件类型不支持 | 分别提交 | 返回 403 或文件格式错误 | `nonCourseMemberCannotSubmitPublishedLab`、`teacherCannotSubmitStudentLabEndpoint`、`submissionRejectsUnsupportedSourceFileType` | 自动化覆盖 | 通过 |
| TC-LAB-11 | FR-LAB-02 | `UI-LAB-02` | 存在断点恢复参数、提交失败、历史加载失败 | 进入学生页，恢复草稿、制造接口失败 | 页面恢复上次代码，失败时提示明确 | `restores lab draft code from the resume query parameter`、`surfaces backend submission errors on the page`、`shows a history loading failure without breaking the detail page` | 自动化覆盖 | 通过 |
| TC-LAB-12 | FR-LAB-03 | `UI-LAB-05`、`API-LAB-09`、`DB-LAB-03` | 学生多次提交同一实验 | 查询本人历史 | 按时间倒序展示，最新版本 `isLatest=true`，仅一个 `isFinal=true` | `studentCanSubmitCodeTwiceAndVersionIncrements`、`studentCanViewOwnSubmissionHistoryInDescendingOrder`、历史页前端用例覆盖 | 自动化覆盖 | 通过 |
| TC-LAB-13 | FR-LAB-03 | `API-LAB-09/10`、`UI-LAB-03/05` | 教师具备课程管理权限；存在多名学生提交 | 按学生、提交状态、评测状态、逾期条件筛选并查看详情 | 支持筛选、详情读取、历史版本不被误标为最新 | `teacherCanFilterLabSubmissionHistoryAndViewSubmissionDetail`、`teacherFiltersDoNotPromoteHistoricalSubmissionToLatest`、教师端筛选用例覆盖 | 自动化覆盖 | 通过 |
| TC-LAB-14 | FR-LAB-03 | `API-LAB-10`、异常码 `LAB-403-03/LAB-404-02` | 学生尝试访问他人提交；提交不属于实验 | 查询详情和结果 | 越权返回 403，错绑记录返回 404 | `studentCannotViewAnotherStudentsSubmissionDetail`、`studentCannotViewAnotherStudentsEvaluationResult`、`submissionDetailReturnsNotFoundWhenSubmissionDoesNotBelongToLab` | 自动化覆盖 | 通过 |
| TC-LAB-15 | FR-LAB-04 | `API-LAB-11/12`、`DB-LAB-02/04` | 存在公开和隐藏测试用例；学生提交正确代码 | 提交并轮询评测结果 | 评测通过，分数正确，学生看不到隐藏用例输入/期望输出，教师可见全部 | `autoEvaluateSubmissionEventuallyReturnsAcceptedAndHidesHiddenCaseFromStudent`、学生端评测结果展示用例覆盖 | 自动化覆盖 | 通过 |
| TC-LAB-16 | FR-LAB-04 | `API-LAB-12`、`DB-LAB-04` | 提交错误代码 | 查询评测结果 | 返回 `WRONG_ANSWER`，按通过用例权重计算得分并保存 case 详情 | `autoEvaluateSubmissionReturnsWrongAnswerAndPersistsCaseDetails`、学生端失败详情展示用例覆盖 | 自动化覆盖 | 通过 |
| TC-LAB-17 | FR-LAB-04 | `API-LAB-12`、异常码 `LAB-500-01/LAB-500-02` | 构造超时和编译错误代码 | 查询评测结果 | 状态分别为 `TIME_LIMIT_EXCEEDED`、`COMPILE_ERROR`，并保留错误信息 | `autoEvaluateSubmissionReturnsTimeLimitExceeded`、`autoEvaluateSubmissionReturnsCompileError` | 自动化覆盖 | 通过 |
| TC-LAB-18 | FR-LAB-04 | `API-LAB-12`、异常码 `LAB-500-03/LAB-500-04` | 构造运行错误代码、模拟评测器异常 | 查询评测结果或直接调服务 | 状态分别为 `RUNTIME_ERROR`、`SYSTEM_ERROR`，提交记录保留且允许重评 | `autoEvaluateSubmissionReturnsRuntimeError`、`evaluatorExceptionMarksSubmissionAsSystemErrorAndPreservesEvaluationRecord` | 自动化覆盖 | 通过 |
| TC-LAB-19 | FR-LAB-04 | `API-LAB-11` | 已有提交记录 | 教师触发重评接口 | 状态先回到受理，再重新生成评测结果 | `teacherCanTriggerEvaluationEndpointForExistingSubmission` | 自动化覆盖 | 通过 |
| TC-LAB-20 | FR-LAB-04/NFR-LAB-05 | `DB-LAB-01/02`、事务边界 | 实验创建/更新时第二条测试用例写入失败 | 执行创建或更新 | 事务整体回滚，不留下半成品实验或测试用例 | `createRollsBackExperimentWhenSecondTestcaseInsertFails`、`updateRollsBackDeletedAndReplacedTestcasesWhenInsertFails` | 自动化覆盖 | 通过 |
| TC-LAB-21 | FR-LAB-05 | `UI-LAB-02`、`API-LAB-16/17`、`DB-LAB-06` | 学生已有提交，实验允许报告 | 上传报告两次并查看详情 | 报告版本递增，教师在提交详情中看到最新报告 | `studentCanUploadReportTwiceAndTeacherCanViewLatestReportFromSubmissionDetail`、学生端报告上传用例覆盖 | 自动化覆盖 | 通过 |
| TC-LAB-22 | FR-LAB-05 | `API-LAB-16`、异常码 `LAB-400-06/LAB-409-01` | 报告类型不支持；实验已截止 | 上传报告 | 返回报告格式错误或截止错误 | `reportUploadRejectsUnsupportedFileTypeAndStudentCannotViewOthersReport`、`expiredLabRejectsReportUpload` | 自动化覆盖 | 通过 |
| TC-LAB-23 | FR-LAB-05 | `UI-LAB-02/03`、下载接口 | 学生或教师查看最新报告 | 下载报告 | 通过 blob 下载，文件名正确 | `downloads the latest report through the lab download action`、`downloads a submission report through the lab download action`、`labs.spec.ts` 覆盖 | 自动化覆盖 | 通过 |
| TC-LAB-24 | FR-LAB-06 | `UI-LAB-06`、`API-LAB-17`、`DB-LAB-06` | 已有报告记录 | 教师评分实验报告 | 报告分和评语保存成功并刷新界面 | `teacherCanScoreUploadedReport`、`scores a submission report and updates the visible report feedback`、`labs.spec.ts` 覆盖 | 自动化覆盖 | 通过 |
| TC-LAB-25 | FR-LAB-06 | `UI-LAB-06`、`API-LAB-13`、`DB-LAB-05` | 已有提交与评测结果 | 教师为提交打分 | 自动分、人工分、最终分和评语保存成功 | `teacherCanScoreSubmissionAndPersistScoreRecord`、`shows persisted submission scoring data and saves rescoring changes` | 自动化覆盖 | 通过 |
| TC-LAB-26 | FR-LAB-06 | `API-LAB-13`、异常码 `LAB-400-05/LAB-403-01` | 分数超范围、无管理权限、学生越权评分 | 提交评分请求 | 返回 400 或 403，且数据库不被污染 | `submissionScoreRejectsOutOfRangeAndInvalidAccess`、`blocks submission score saving when required score fields are empty`、`keeps invalid submission score input as page feedback instead of throwing` | 自动化覆盖 | 通过 |
| TC-LAB-27 | FR-LAB-06/NFR-LAB-03 | `DB-LAB-07` | 已存在评分记录 | 修改已评分成绩并填写原因 | 生成评分变更日志，记录旧分、新分、原因和操作人 | `updatingSubmissionScoreRequiresReasonAndPersistsChangeLog` | 自动化覆盖 | 通过 |
| TC-LAB-28 | FR-LAB-07 | `UI-LAB-07`、`API-LAB-18` | 存在教师评分但尚未发布成绩 | 学生查询实验结果 | 隐藏最终分、教师评语、报告评分，仅展示允许公开的评测摘要 | `studentResultViewHidesTeacherScoreUntilLabScoresAreReleased`、学生端隐藏成绩细节用例覆盖 | 自动化覆盖 | 通过 |
| TC-LAB-29 | FR-LAB-07 | `UI-LAB-07`、`API-LAB-18` | 实验成绩已发布 | 学生查询实验结果 | 展示最终分、人工分、报告分、教师评语、发布时间 | `shows the latest teacher score and feedback beside evaluation results`、`shows published teacher scoring details from the lab result api` | 自动化覆盖 | 通过 |
| TC-LAB-30 | FR-LAB-07/08 | `API-LAB-18`、GRD 来源成绩 | 实验成绩已发布，存在多个学生得分 | 教师发布成绩后查询 GRD 来源成绩 | 仅发布后才对外暴露来源成绩，且分数和状态正确 | `releasedLabScoresExposeSourceGradesForGrdSync` | 自动化覆盖 | 通过 |
| TC-LAB-31 | FR-LAB-08 | `API-LAB-14`、`UI-LAB-08` | 多名学生中部分已提交、部分未提交，含逾期样本 | 查询实验统计 | 返回提交率、未提交名单、平均分、评测完成率、逾期提交数和分数分布 | `teacherQueriesLabStatisticsWithUnsubmittedStudentsScoreDistributionAndLateCount`、教师端统计图用例覆盖 | 自动化覆盖 | 通过 |
| TC-LAB-32 | FR-LAB-08/NFR-LAB-04 | `API-LAB-14` | 学生尝试访问实验统计 | 查询统计 | 返回 403 | `studentCannotQueryLabStatistics` | 自动化覆盖 | 通过 |
| TC-LAB-33 | FR-LAB-03/08 | 前端历史页与统计页 | 学生无提交历史；教师统计加载失败 | 打开页面 | 空状态和失败提示明确，不影响其余页面 | `shows an empty state when the student has no submissions yet`、`surfaces history loading failures on the page`、`shows a teacher-facing error when lab statistics loading fails` | 自动化覆盖 | 通过 |
| TC-LAB-34 | FR-LAB-02/03、NFR-LAB-01/03/05 | API-LAB-10、DB-LAB-09 | 新文件、无文件、仅旧 file_id 三类提交 | 查询详情并检查 JSON | 顶层 `hasFile`；`sourceFile` 仅 null 或四字段对象，无 fileId/storageKey/status/URL | `studentCanSubmitSourceFileWhenCourseMember`、`teacherCanInspectTrustedSourceMetadataAndDownloadTheExactSubmissionVersion` | 定向 41/41 通过 | 通过 |
| TC-LAB-35 | FR-LAB-03/06、NFR-LAB-01/03 | API-LAB-19、UI-LAB-06 | `canManageCourse` 教师与 Unicode 文件样本 | 固定路径下载指定版本并核对响应 | 内容、文件名、MIME、长度正确，无 URL/内部键 | 同上自动化；MAN-LAB-011 下载 200/84 B/SHA-256 一致 | 自动化与浏览器通过 | 通过 |
| TC-LAB-36 | FR-LAB-03/06、NFR-LAB-04 | API-LAB-19 鉴权 | 匿名/失效会话/学生本人/非成员/其他课程教师 | 分别请求并监测存储读取 | 稳定 401/403，拒绝发生在物理读取前 | `sourceDownloadReauthorizesRoleAndCourseManagementWithoutLeakingStorageState`；MAN-LAB-011 403/401 | 自动化与真实请求通过 | 通过 |
| TC-LAB-37 | FR-LAB-03/06、NFR-LAB-03/04 | API-LAB-19 归属 | 多课程、实验、提交和资产 | 组合交叉 ID 请求 | 读取前统一失败，不泄漏或回退其他版本 | `sourceDownloadRejectsCrossLabAndDeletedSubmissionBindingsAsTheSameMissingTarget` | 定向 41/41 通过 | 通过 |
| TC-LAB-38 | FR-LAB-02/03/06、NFR-LAB-01/03/05 | API-LAB-10/19、三类新错误码 | 无文件/旧元数据/DELETED/物理缺失 | 查询详情并请求下载 | DTO 阻塞态及 LAB-404-03/409-03/500-05 稳定 | `sourceDownloadDistinguishesNoFileLegacyMetadataAndDeletedAssets`、`sourceDownloadReturnsStableStorageAndMetadataIntegrityErrors` | 定向 41/41 通过 | 通过 |
| TC-LAB-39 | FR-LAB-02/06、NFR-LAB-04/05 | 上传、响应头、存储异常 | Unicode/换行/路径/MIME/超限/读取异常样本 | 上传并由教师下载 | 无路径/头注入或内部信息泄漏，400-06/500-05 稳定 | `sourceFilenameSanitizationPreventsPathAndHeaderInjectionWhileKeepingUnicode`；MAN-LAB-011 UTF-8 filename* | 自动化与浏览器通过 | 通过 |
| TC-LAB-40 | FR-LAB-06、NFR-LAB-04/05 | UI-LAB-06 双入口 | 两类资产及 pending/401/403/失败响应 | 分别下载、重复点击并重试 | 源文件/报告状态独立，固定路径 blob，pending 去重且失败可恢复 | `LabSubmissionReviewView.spec.ts` 专项、`labs.spec.ts`；521/521、typecheck/build；MAN-LAB-011 | 前端自动化与浏览器通过 | 通过 |
| TC-LAB-41 | FR-LAB-02/03、NFR-LAB-01/03/05 | H2/MySQL/compose、DB-LAB-09、事务 | 三套 schema、非法约束和事务失败样本 | 执行迁移/约束/补偿场景 | 字段与约束一致；事务失败时成功补偿不留可访问孤儿文件 | `sourceUploadDeletesThePhysicalFileWhenTheDatabaseTransactionRollsBack` 强制 DB 失败，断言提交/资产 0 行且目录集合不变 | 直接自动化通过；delete 自身失败的重试/审计不在范围 | 通过 |
| TC-LAB-N01 | NFR-LAB-01 | 提交、评测、评分主链路 | 依次执行提交、评测异常、评分、成绩发布 | 提交先落库，异常不丢记录，成绩发布可重复执行 | `studentCanSubmitCodeTwiceAndVersionIncrements`、`evaluatorExceptionMarksSubmissionAsSystemErrorAndPreservesEvaluationRecord`、`teacherCanReleaseScoresAfterClosingLab` | 自动化覆盖 | 通过 |
| TC-LAB-N02 | NFR-LAB-02 | 提交受理、异步评测、统计查询 | 观察提交初始状态、异步轮询、统计结果生成 | 提交快速返回 `PENDING/RUNNING`；统计接口返回结构稳定 | `loads published lab detail and submits code successfully`、`autoEvaluateSubmissionEventuallyReturnsAcceptedAndHidesHiddenCaseFromStudent`、`teacherQueriesLabStatisticsWithUnsubmittedStudentsScoreDistributionAndLateCount` | 自动化覆盖 | 通过 |
| TC-LAB-N03 | NFR-LAB-03 | 提交、评测、报告、评分、变更留痕 | 完整执行提交、报告、评分、改分、发布成绩 | 所有关键动作均有版本或日志留痕 | `studentCanUploadReportTwiceAndTeacherCanViewLatestReportFromSubmissionDetail`、`teacherCanScoreSubmissionAndPersistScoreRecord`、`updatingSubmissionScoreRequiresReasonAndPersistsChangeLog`、迁移测试相关用例覆盖 | 自动化覆盖 | 通过 |
| TC-LAB-N04 | NFR-LAB-04 | 课程权限、本人数据、隐藏用例、越权拒绝 | 学生访问教师接口、非成员访问实验、学生查看他人提交/结果 | 返回 403，隐藏测试用例对学生不可见 | `controllerRejectsInvalidPayloadAndPermissionViolations`、`nonCourseMemberCannotSubmitPublishedLab`、`studentCannotViewAnotherStudentsSubmissionDetail`、`studentCannotQueryAnotherStudentsLabResult` | 自动化覆盖 | 通过 |
| TC-LAB-N05 | NFR-LAB-05 | 可重复验证、事务和迁移 | 运行目标自动化测试 | 迁移、事务、控制器、前端页面可重复执行 | `LabExperimentMigrationTest`、`LabExperimentTransactionTest`、LAB 前端单测覆盖 | 自动化覆盖 | 通过 |

### 7.3 前端 LAB 用例摘要

| 测试文件 | 覆盖内容 | 结果 |
| --- | --- | --- |
| `frontend/tests/unit/api/labs.spec.ts` | 报告下载、报告评分、实验统计 API wrapper | 3 条通过 |
| `frontend/tests/unit/lab/LabTeacherView.spec.ts` | 教师创建/编辑/发布/截止/发布成绩/删除草稿、统计、提交筛选、报告下载、报告评分、提交评分 | 11 条通过 |
| `frontend/tests/unit/lab/LabStudentView.spec.ts` | 学生详情、提交、断点恢复、前端校验、失败提示、评测结果、报告上传下载、成绩展示 | 12 条通过 |
| `frontend/tests/unit/lab/LabSubmissionHistoryView.spec.ts` | 提交历史、空状态、失败提示 | 3 条通过 |

## 8 测试执行日志

### 8.1 后端 LAB 执行日志

| 日志编号 | 时间 | 命令/测试类 | 执行内容 | 结果 |
| --- | --- | --- | --- | --- |
| LAB-LOG-001 | 2026-06-11 23:12 | `LabExperimentControllerTest` | 实验创建、发布、截止、成绩发布、统计、权限与异常矩阵 | 8 条通过 |
| LAB-LOG-002 | 2026-06-11 23:12 | `LabSubmissionControllerTest` | 提交、历史、评测、报告、评分、结果展示、GRD 来源成绩与权限边界 | 29 条通过 |
| LAB-LOG-003 | 2026-06-11 23:12 | `LabExperimentMigrationTest` | 迁移脚本、报告表、评分表、评分变更表与软删除约束 | 7 条通过 |
| LAB-LOG-004 | 2026-06-11 23:12 | `LabExperimentTransactionTest` | 实验与测试用例创建/更新的事务回滚 | 2 条通过 |
| LAB-LOG-005 | 2026-06-11 23:12 | `LabEvaluationServiceTest` | 评测器异常时状态落库与失败结果留痕 | 1 条通过 |
| LAB-LOG-006 | 2026-06-11 23:12 | Maven 汇总 | `Tests run: 47, Failures: 0, Errors: 0, Skipped: 0` | 构建成功 |

### 8.2 前端 LAB 执行日志

| 日志编号 | 时间 | 命令/测试文件 | 执行内容 | 结果 |
| --- | --- | --- | --- | --- |
| LAB-LOG-007 | 2026-06-11 23:12 | `tests/unit/api/labs.spec.ts` | LAB API wrapper：报告下载、报告评分、统计接口 | 3 条通过 |
| LAB-LOG-008 | 2026-06-11 23:12 | `tests/unit/lab/LabTeacherView.spec.ts` | 教师端实验管理、统计、提交筛选、报告和评分交互 | 11 条通过 |
| LAB-LOG-009 | 2026-06-11 23:12 | `tests/unit/lab/LabStudentView.spec.ts` | 学生端实验详情、提交、评测结果、报告上传下载、成绩展示 | 12 条通过 |
| LAB-LOG-010 | 2026-06-11 23:12 | `tests/unit/lab/LabSubmissionHistoryView.spec.ts` | 提交历史、空态、失败提示 | 3 条通过 |
| LAB-LOG-011 | 2026-06-11 23:12 | Vitest 汇总 | `Test Files 4 passed (4)`、`Tests 29 passed (29)` | 构建成功 |

## 9 手工测试与联调确认

| 手测编号 | 模块 | 场景 | 操作要点 | 预期结果 | 当前结果 |
| --- | --- | --- | --- | --- | --- |
| MAN-LAB-001 | LAB/AUTH/CRS | 教师创建并发布实验 | 浏览器登录教师账号，进入课程，创建实验并发布 | 实验保存成功，课程学生可见，教师列表状态刷新 | 待手工验收 |
| MAN-LAB-002 | LAB | 学生查看实验并提交代码 | 学生进入实验详情，选择语言并提交代码 | 提交成功，显示受理状态并最终刷新评测结果 | 待手工验收 |
| MAN-LAB-003 | LAB | 学生上传实验报告 | 学生在已有提交基础上上传 PDF/DOCX/ZIP 报告 | 报告上传成功，最新版本正确，下载入口可用 | 待手工验收 |
| MAN-LAB-004 | LAB | 教师查看提交并评分 | 教师筛选学生提交，查看代码、报告、评测结果并评分 | 自动分、报告分、人工分、最终分和评语保存成功 | 待手工验收 |
| MAN-LAB-005 | LAB | 学生查看发布前后结果差异 | 成绩发布前后分别进入结果页 | 发布前隐藏教师评分与报告评分，发布后展示完整反馈 | 待手工验收 |
| MAN-LAB-006 | LAB | 教师查看实验统计 | 打开统计页查看提交率、未提交名单和分数分布 | 统计数据与实际样本一致，页面可视化显示正常 | 待手工验收 |
| MAN-LAB-007 | LAB | 权限与异常边界 | 非成员、学生访问教师入口、他人结果访问、实验已截止重提 | 页面提示明确，接口返回受控错误 | 待手工验收 |
| MAN-LAB-008 | LAB | 真实 Docker 沙箱专项测试 | 提交 AC/WA/编译错误/运行错误/超时/内存超限样本，多语言并发执行 | 状态、日志、资源限制和超时控制符合设计 | 待专项测试 |
| MAN-LAB-009 | LAB | 基础性能样本 | 准备更多课程学生和实验提交，观察提交受理与统计接口响应时间 | 提交快速受理，统计查询在设计阈值内返回 | 待专项测试 |
| MAN-LAB-010 | LAB/LRN/GRD | 跨模块联调 | 发布实验、提交、评分、发布成绩后检查通知中心和成绩同步 | LRN 能看到实验发布/成绩发布通知，GRD 能读取 LAB 来源成绩 | 待联调确认 |
| MAN-LAB-011 | LAB/AUTH/CRS | 提交源文件受控下载 | student001 在实验 `950211` 上传 `student-source-林晓.py` 生成提交 `950204`；teacher001 在 UI-LAB-06 核对元数据并下载；学生 bearer 与匿名请求验证越权 | 指定版本内容/文件名/MIME 正确，两入口独立，越权失败，页面无 fileId/storageKey/路径/URL | 通过；页面显示 `text/x-python-script`、84 B，下载 200/Content-Length 84/UTF-8 filename*，上传/下载 SHA-256 均 `1aa9b0c2b985e0062b13b77eb0676eeae53ccb3064deae0ad88eff49bd6f7e17`；学生 403 `ERR-AUTH-05`、匿名 401 `ERR-AUTH-04`；教师控制台 0 errors/warnings；证据 `output/playwright/issue-222/01~06` |

## 10 缺陷、风险与处理建议

| 风险编号 | 风险说明 | 影响范围 | 建议处理 |
| --- | --- | --- | --- |
| R-LAB-001 | issue #156 要求能追溯功能、接口、页面、数据表和跨模块集成点，但正式设计文档未定义 `IC-LAB-*` 编号体系。 | 集成追踪一致性 | 本文已补充文档级集成编号，并在 4.3 节标明其来源和对应关系；该编号只服务测试整合，不改变设计文档正式编号。 |
| R-LAB-002 | 当前自动化评测使用 fake sandbox 或可控样本，不能替代真实 Docker 沙箱的资源隔离、并发和多语言压力验证。 | FR-LAB-04、NFR-LAB-02、NFR-LAB-04 | 在 DEV/FAT 环境补充真实沙箱专项测试并记录结果。 |
| R-LAB-003 | 统一环境下 LRN 通知中心和 GRD 成绩页仍需验证 LAB 事件是否完整展示。 | FR-LAB-01、FR-LAB-07、FR-LAB-08 | 在模块联调阶段执行 `MAN-LAB-010` 并补充截图或验收记录。 |
| R-LAB-004 | 真实浏览器视觉、上传控件行为、下载行为和跨页刷新体验不能完全由 Vitest 单测替代。 | UI-LAB-01 ~ UI-LAB-08 | 测试负责人整合时补跑浏览器端到端验收。 |
| R-LAB-006 | #222 仅要求事务失败时成功补偿删除；补偿删除本身失败后的告警、审计和可重试清理机制未冻结为已实现能力。 | 文件生命周期/运维 | 不在本报告虚报；作为后续运维风险单独跟踪。 |

## 11 验收结论

| 验收项 | 结论 | 说明 |
| --- | --- | --- |
| 功能覆盖 | 通过 | TC-LAB-01 ~ 41 已追踪；#222 后端定向 41/41、完整 302 tests 和前端 521/521 通过 |
| 接口覆盖 | 通过 | API-LAB-10 安全 DTO 与 API-LAB-19 内容/响应头/权限/异常通过自动化和 MAN-LAB-011 |
| 页面覆盖 | 通过 | UI-LAB-06 双入口、元数据、pending/失败/兼容状态通过 53 files/521 tests 与 1440/390 浏览器验证 |
| 数据一致性 | 通过 | DB-LAB-09 可信字段、一对一绑定和强制事务失败物理补偿有直接自动化证据 |
| 权限与安全 | 通过 | `canManageCourse`、学生本人排除、交叉 ID、响应头和无泄漏通过自动化；MAN-LAB-011 补充 200/403/401 |
| 非功能 | 有条件通过 | 可靠性、可追踪性、安全性、可测试性已有自动化证据；真实性能和真实沙箱专项仍需补充 |
| 最终结论 | 通过（保留运维限制） | #222 已取得完整自动化和真实浏览器证据；FileStorageService.delete 自身失败后的告警/审计/重试不在 #222 范围 |

## 12 附录

### 12.1 推荐执行命令

```powershell
cd C:\Users\李世旺\Desktop\Temp\LESSON\软工基础\大作业\OJSE\OnlineJudge\backend
mvn test "-Dtest=LabExperimentControllerTest,LabSubmissionControllerTest,LabExperimentMigrationTest,LabExperimentTransactionTest,LabEvaluationServiceTest"

cd C:\Users\李世旺\Desktop\Temp\LESSON\软工基础\大作业\OJSE\OnlineJudge\frontend
npm run test:unit -- tests/unit/api/labs.spec.ts tests/unit/lab/LabTeacherView.spec.ts tests/unit/lab/LabStudentView.spec.ts tests/unit/lab/LabSubmissionHistoryView.spec.ts
```

### 12.2 文档校验

```powershell
git diff --check
```
