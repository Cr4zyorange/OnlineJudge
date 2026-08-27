# TST-DOC-06 HWK 作业与自动评测测试文档

| 文档编号 | TST-DOC-06 |
| --- | --- |
| 文档名称 | HWK 作业与自动评测测试文档 |
| 项目名称 | 在线教学与实训平台 |
| 所属阶段 | 系统测试与验收测试 |
| 报告版本 | V1.9 |
| 编写日期 | 2026-08-27 |
| 编写人 | HWK 模块负责人 |
| 对应 issue | #296 CODE 提交后台评测 Worker；#264 HWK 业务场景文档与测试闭环；#157 TST-DOC-06；#225 HWK 统计与待处理名单；#224 HWK 草稿逻辑删除；#214 HWK FILE 附件上传与安全提交 |
| 测试范围 | HWK 作业发布、草稿删除、提交/历史/评测/批阅、统计与待处理名单；FILE 单附件上传、24h 恢复/清理、原子绑定/补偿、所有权、受控下载、权限、安全与响应式页面 |
| 测试结论 | #296 已补齐 CODE 后台 Worker：提交创建 PENDING 任务，事务提交后独立消费并进入终态；API-HWK-11 保持纯读取，异常落 SYSTEM_ERROR 且提交保留。FR-HWK-04 与 TC-HWK-10/11 本地自动化判定 PASS；真实 Docker 专项仍按部署环境复核 |

## 1 文档控制

### 1.1 修订记录

| 版本 | 日期 | 修订人 | 修订说明 |
| --- | --- | --- | --- |
| V1.0 | 2026-06-09 | HWK 模块负责人 | 按 #152 统一结构整理 HWK 测试范围、用例、自动化覆盖、执行日志、手工验收点和残余风险 |
| V1.1 | 2026-08-22 | HWK 模块负责人 | 按 #225 补充固定五档归一化分布、待评测/待批阅 attention、当前活跃学生范围、SQL 聚合/组合索引、权限、URL/隐私和响应式验收契约，并记录 RED/GREEN、全量回归与浏览器证据 |
| V1.2 | 2026-08-22 | HWK 模块负责人 | 按 #224 补充 API-HWK-22、HWK_4095、TC-HWK-19、UI-HWK-01 删除入口、父表原子软删/子历史保留/普通更新防复活；记录全量自动化、typecheck/build 和 1440×900/390×844 浏览器证据 |
| V1.3 | 2026-08-22 | HWK 模块负责人 | 按 #214 补充 API-HWK-23/24、DB-HWK-08、TC-HWK-20 ~ 27、MAN-HWK-012，回填后端 340/定向 94、前端 545、MySQL 9.6 与 `output/playwright/issue-214/01~10` 证据 |
| V1.4 | 2026-08-25 | HWK 模块负责人 | 按 #264 拆分 UC-HWK-01/02 需求层 SSD，补概要组件顺序图、详细对象顺序图和业务场景分类；记录文档契约 RED→GREEN、共享 HWK E2E runner 2/2 及 LRN/GRD 真实边界结果；CODE 产品结论后由 V1.8 纠正 |
| V1.5 | 2026-08-26 | HWK 模块负责人 | 按 PR #276 评审纠正 GRD 假阳性、UML 组合片段和通知失败结论；通知失败设计/实现冲突标记 FAIL 并关联 #281，待复测结果不预写为 PASS |
| V1.6 | 2026-08-26 | HWK 模块负责人 | #281 / PR #285 合并后复测通知失败整体回滚契约；补充定向 9/9、共享 E2E runner 2/2、后端 375、前端 556 及闭环契约 GREEN 证据；CODE 产品结论后由 V1.8 纠正 |
| V1.7 | 2026-08-26 | HWK 模块负责人 | 按项目统一图形工具回退六张新增图，改用仓库 Mermaid 渲染脚本生成白底 SVG，并更新三层文档引用与闭环契约 |
| V1.8 | 2026-08-27 | HWK 模块负责人 | 按 PR #276 复审纠正 CODE 评测假阳性：E2E 不再以 API-HWK-11 读取触发评测，FR-HWK-04 与 TC-HWK-10/11 如实标记 FAIL；同步三层图源、SVG 边语义校验和反向变异测试，并明确 #264 文档/测试闭环可关闭、#296 独立负责产品修复 |
| V1.9 | 2026-08-27 | HWK 模块负责人 | 按 #296 实现提交后后台评测 Worker、PENDING 原子认领、API-HWK-11 纯读取与 SYSTEM_ERROR 兜底；E2E 仅轮询 API-HWK-10 验收终态，TC-HWK-10/11 更新为 PASS |

### 1.2 审批记录

| 角色 | 姓名/负责人 | 审批意见 | 日期 |
| --- | --- | --- | --- |
| 项目负责人 | 待填写 | 待审批 | 2026-06-09 |
| 测试负责人 | @MontesquieuE | 待整合确认 | 2026-06-09 |
| HWK 模块负责人 | HWK 负责人 | 待确认 | 2026-06-09 |

## 2 测试概述

本文件用于记录 HWK 作业与自动评测模块在当前版本下的测试依据、测试环境、测试数据、测试用例、执行结果、手工验收清单、缺陷风险和验收结论。覆盖范围对齐 `FR-HWK-01 ~ FR-HWK-06`、`NFR-HWK-01 ~ NFR-HWK-05`、`UI-HWK-01 ~ UI-HWK-09`、`API-HWK-01 ~ API-HWK-24`、`DB-HWK-01 ~ DB-HWK-08`、`TC-HWK-01 ~ TC-HWK-27` 与 `TC-HWK-N01 ~ TC-HWK-N05`。

2026-06-09 的历史记录只覆盖 #225/#224 之前的基础流程。2026-08-22 已独立执行 #225 的统计契约和 #224 的 DRAFT 原子逻辑删除、403/404/409 分类、普通更新防复活、子历史保留、UI-HWK-01 交互及 1440×900/390×844 浏览器行为，并完成全量回归。真实 Docker 沙箱压力、真实 MySQL 容器迁移及 LRN/GRD 生产联调仍列为专项或部署验收项。

## 3 测试依据

| 序号 | 文档/代码依据 | 用途 |
| --- | --- | --- |
| 1 | `docs/开发/HWK-作业与自动评测模块开发流程.md` | HWK 主流程、开发顺序、P0 闭环、权限与跨模块事件要求 |
| 2 | `docs/最终提交/软件需求规格说明书.md` | FR-HWK、NFR-HWK 需求和验收来源 |
| 3 | `docs/最终提交/软件概要设计说明书.md` | 模块边界、跨模块依赖和追踪关系来源 |
| 4 | `docs/最终提交/软件详细设计说明书.md` | UI、API、数据库、测试编号和追踪矩阵来源 |
| 5 | `docs/过程/概要/作业与自动评测模块概要设计提交稿（hwk）.md` | HWK 过程设计、非功能要求和页面/API 对照补充 |
| 6 | `docs/过程/详细设计/HWK-作业与自动评测模块-详细设计提交稿.md` | HWK 详细流程、状态、异常和测试编号补充 |
| 7 | `backend/src/test/java/com/onlinejudge/hwk` | HWK 后端自动化测试实现 |
| 8 | `frontend/tests/unit/hwk` | HWK 前端 API 与页面单元测试实现 |
| 9 | `database/migrations/20260530_01_create_hwk_homework.sql`、`20260601_01_create_hwk_submission.sql`、`20260602_01_create_hwk_evaluation.sql`、`20260602_02_create_hwk_review_log.sql` | HWK 数据表和迁移约束依据 |
| 10 | GitHub Issue #225《补齐作业统计分布与待处理名单契约》 | API-HWK-09/15 兼容增量、状态口径、实现边界和验收项来源 |
| 11 | GitHub Issue #224《补齐草稿作业逻辑删除契约与教师端入口》 | API-HWK-22、HWK_4095、TC-HWK-19、父表原子软删、子历史保留、普通更新防复活和 UI-HWK-01 验收来源 |
| 12 | GitHub Issue #214《补齐 HWK FILE 作业附件上传与安全提交链路》 | API-HWK-23/24、DB-HWK-08、TC-HWK-20 ~ 27、MAN-HWK-012、附件状态机、受控下载与错误码契约来源 |

## 4 测试范围

### 4.1 功能与非功能范围

| 编号 | 测试对象 | 主要验证点 | 当前覆盖状态 |
| --- | --- | --- | --- |
| FR-HWK-01 | 作业创建与发布 | 教师/助教创建草稿、编辑、原子逻辑删除 DRAFT、保存题目/测试用例、发布、关闭、发布通知；删除只改父表，普通更新不得复活 | PASS；#281 复测证明必需通知失败返回 `503/HWK_5003` 并整体回滚，作业保持 `DRAFT` |
| FR-HWK-02 | 学生作业查看与提交 | 学生查看已发布作业，标准答案和隐藏用例不可见，提交文本/客观题/代码，截止和重复提交规则 | 后端和前端自动化已覆盖 |
| FR-HWK-03 | 提交历史管理 | 学生个人历史、教师全班分页列表、筛选、提交详情、最新有效提交标识，以及 attention 未传时的兼容行为 | 既有列表与 #225 attention 兼容回归均已覆盖 |
| FR-HWK-04 | 自动评测 | 客观题自动评分、代码题 IO 评测、失败状态保留、评测结果查询、重评 | PASS：客观题同步评分；CODE 提交后由后台 Worker 从 PENDING 推进到终态，API-HWK-11 仅读，异常保留提交 |
| FR-HWK-05 | 教师批阅与重评 | 人工分数、评语、重评日志，以及待评测/待批阅 attention 的题型、评测终态和批阅状态组合 | 既有批阅与 #225 attention 新语义已由自动化和浏览器覆盖 |
| FR-HWK-06 | 作业反馈与结果展示 | 成绩可见性；当前活跃学生单次作业统计；五档归一化；未提交、待评测、待批阅服务端分页；向 GRD 提供成绩来源 | #225 单次作业统计与名单契约通过；GRD 生产全链路仍待统一环境确认 |
| NFR-HWK-01 | 可靠性 | 提交、评测、批阅、通知失败和分数记录不丢失；草稿删除原子化且普通更新不能复活 | PASS；通知失败不留下已发布作业或部分通知，受控错误与事务状态可追踪 |
| NFR-HWK-02 | 性能 | 三类名单分页、统计 SQL 聚合、组合索引、聚合总数不受当前页影响 | #225 SQL、迁移、极大页码与大于单页样本通过；生产规模压力和 MySQL EXPLAIN 待部署复核 |
| NFR-HWK-03 | 可追踪性 | 提交、评测、批阅、重评、成绩发布均有记录或日志；删除父作业后全部子数据和历史保留 | 六类子记录主键与关键内容保持测试通过 |
| NFR-HWK-04 | 安全性 | 当前用户来源、课程成员校验、统计/attention/草稿删除的学生与无权限教师 403、姓名失败不泄露裸 ID | 删除 403、状态 409、重复删除 404 契约通过 |
| NFR-HWK-05 | 可测试性 | 草稿删除权限/状态/重复请求/并发/历史保留/末页回退及五档、分页、URL、迁移和权限可稳定复现 | #224 全量自动化、typecheck/build 与 4 张响应式截图已落档 |

### 4.2 页面、接口、数据表覆盖

| 类别 | 编号范围 | 覆盖说明 |
| --- | --- | --- |
| 页面 | UI-HWK-01 ~ UI-HWK-09 | UI-HWK-01 的 DRAFT-only 删除、确认取消/pending/失败保留/末页回退由单测覆盖；1440×900/390×844 浏览器证据通过 |
| 接口 | API-HWK-01 ~ API-HWK-24 | API-HWK-23 单 `file` 上传/GET/DELETE 和 API-HWK-24 受控下载的成功、鉴权、所有权、状态与存储错误通过 |
| 数据表 | DB-HWK-01 ~ DB-HWK-08 | 既有契约保留；`t_hwk_submission_attachment` 的 UUID 唯一、UPLOADED/BOUND/DELETED、一提交一附件和 fresh/重复迁移已由 H2 与本机真实 MySQL 9.6 验证 |
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
| 操作系统 | V1.0：Windows；#225：macOS |
| 后端运行环境 | Java 25，Spring Boot 3.4.5，Maven 3.9.11，JUnit 5，MockMvc，H2 MySQL mode |
| 前端运行环境 | Node.js，Vue 3.5，Vite 6.4，Vitest 3.2，jsdom，Playwright Chromium |
| 数据库 | 自动化测试使用 H2 内存库；迁移脚本按 MySQL 8.0 兼容约束编写 |
| 鉴权方式 | 后端测试使用 `X-User-Id`、`X-User-Role` 或 Bearer Session；前端测试 mock API wrapper |
| 执行日期 | 2026-06-09；#225：2026-08-22 |

## 6 测试数据

| 数据类别 | 数据说明 | 使用模块 |
| --- | --- | --- |
| 教师/助教用户 | `X-User-Id=501` 等课程管理者；Bearer 集成测试动态创建教师账号 | HWK、AUTH、CRS |
| 学生用户 | `X-User-Id=101`、`601` 等课程成员；非成员学生用于越权验证 | HWK、AUTH、CRS |
| 课程数据 | `courseId=101` 等测试课程，包含教师、助教、当前活跃学生、已退出/已删除成员、非成员和无权限教师分支 | HWK、CRS |
| 作业数据 | 客观题、文本题、文件题、代码题，包含满分 100 和非 100 样本；状态包含 DRAFT、NOT_OPEN、PUBLISHED、CLOSED、SCORE_PUBLISHED、ARCHIVED；另准备已删除父记录、删除前旧实体和当前页唯一草稿 | HWK |
| 题目数据 | 客观题题干、选项、标准答案、分值和排序 | HWK |
| 测试用例数据 | 公开/隐藏 IO 用例、分值权重、语言白名单、时间/内存限制 | HWK、LAB 公共评测抽象 |
| 提交数据 | 文本答案、客观题 JSON、代码文本、语言、历史/最终版本、删除记录、SUBMITTED/LATE/REJECTED、无分数及五档边界分数 | HWK |
| 评测和批阅数据 | NONE/PENDING/RUNNING 及六类评测终态，UNREVIEWED/REVIEWED/NEED_REVIEW，人工分数、评语、重评理由和日志 | HWK |
| 草稿删除关联数据 | DRAFT 父作业及题目、测试用例、判题配置、提交、评测、批阅/重评历史快照，用于验证只软删父表和并发旧更新不复活 | HWK |
| 跨模块数据 | HOMEWORK_PUBLISHED 通知事件、HWK 来源成绩、作业截止提醒 | HWK、LRN、GRD |

## 7 测试用例汇总

### 7.1 自动化执行结果

| 测试类别 | 命令 | 执行结果 |
| --- | --- | --- |
| 后端 HWK 相关测试 | `mvn test "-Dtest=HomeworkControllerTest,HomeworkBearerAuthControllerTest,HomeworkMigrationTest,HomeworkSubmissionServiceTest"` | 44 条通过，0 失败，0 错误，0 跳过 |
| 前端 HWK 单元测试 | `node node_modules/vitest/vitest.mjs run tests/unit/hwk/homeworksApi.spec.ts tests/unit/hwk/HomeworkStudentListView.spec.ts tests/unit/hwk/HomeworkStudentView.spec.ts tests/unit/hwk/HomeworkSubmissionHistoryView.spec.ts tests/unit/hwk/HomeworkTeacherView.spec.ts --pool=threads` | 5 个测试文件通过，28 条测试通过 |
| #225 后端全量 | `cd backend && mvn test` | 283 条执行，0 失败，0 错误，1 条真实 Docker 沙箱测试因 daemon 不可用跳过 |
| #225 迁移专项 | `mvn -Dtest=com.onlinejudge.hwk.database.HomeworkMigrationTest test`、`sh -n database/mysql/apply-compose-migration.sh` | 10 条迁移测试通过；shell 语法通过 |
| #225 前端全量 | `npm run test:unit`、`npm run typecheck`、`npm run build` | 53 个文件、506 条测试通过；类型检查通过；生产构建 189 modules 通过 |
| #225 浏览器验收 | 本地 H2 + Vite + fake sandbox，Playwright Chromium 检查 1440px 与 390px 的统计页、三类 Tab、深链、键盘、403 和隐私降级 | 9 张截图通过，证据见 `output/playwright/issue-225/README.md` |
| #224 后端全量 | `cd backend && mvn test` | 290 tests，0 failures，0 errors，1 skipped；跳过项为 `DockerSandboxExecutorTest` 环境假设 |
| #224 前端全量与构建 | `npm run test:unit`、`npm run typecheck`、`npm run build` | 53 files / 511 tests 全部通过；类型检查和生产构建通过 |
| #224 浏览器验收 | 本地 H2 + Vite + fake sandbox，Playwright Chromium 验证 1440×900 与 390×844 的 DRAFT-only 入口、取消无请求和真实删除 | DELETE 200，响应 `deleted=true`；390px `documentWidth=innerWidth=390`；控制台 0 error/0 warning；4 张截图见 `output/playwright/issue-224/README.md` |
| #214 完整自动化 | `mvn -q test`；#214 定向 9 类；`npm run test:unit`、`npm run typecheck`、`npm run build` | 后端全量 340 total = 339 passed + 1 Docker-only skipped，0 failures/0 errors；定向 9 类 94/94；前端 53 files / 545 tests，typecheck/build 通过 |
| #214 数据库契约 | 本机真实 MySQL 9.6：`compose-schema.sql` fresh schema + `20260822_03_create_hwk_submission_attachment.sql` 连续执行两次 | 新建库与增量迁移路径均通过；Docker daemon 不可用，未声称容器启停/卷路径已通过 |
| #214 浏览器验收 | H2 真实服务；教师/两学生/匿名；1440×1000/390×844；上传与恢复失败 mock | MAN-HWK-012 通过；伪装 PDF 400/HWK_4005；存储 500/HWK_5002 后重试 201；提交 201；恢复 GET 5002 保留 session/UUID，解除后 GET 200 + DELETE 200；越权被拒绝，两视口无溢出且无脚本 Console error；证据 `output/playwright/issue-214/01~10` |

说明：前两行保留 2026-06-09 的 V1.0 基线。#225 的 RED 阶段分别观察到统计/attention 初始批次 7 failures + 6 errors、边界补充批次 2 failures + 1 error、迁移专项 3 failures + 1 error，以及前端 14 failures；修复后再执行上述 GREEN 与全量命令。真实 MySQL 8.4 容器未执行，原因是本机 Docker daemon socket 不存在。

### 7.2 HWK 核心用例表

| 用例编号 | 对应需求 | 覆盖对象 | 前置条件/测试数据 | 操作步骤 | 预期结果 | 实际结果 | 通过状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TC-HWK-01 | FR-HWK-01 | UI-HWK-02；API-HWK-01、02、16；DB-HWK-01、02 | 教师/助教具备课程管理权限；准备客观题作业和题目数据 | 创建草稿，保存题目，读取详情 | 作业为 DRAFT，字段和题目正确落库 | `teacherCreatesObjectiveHomeworkDraftAndSavesQuestions`、前端教师创建/编辑用例通过 | 通过 |
| TC-HWK-02 | FR-HWK-01 | UI-HWK-03；API-HWK-03、05；LRN 事件 | 已有配置完整作业 | 教师发布作业，学生查询列表/详情 | 状态变为 PUBLISHED，学生可见，发送 HOMEWORK_PUBLISHED | `teacherPublishesConfiguredHomeworkAndNotificationIsEmitted`、前端发布用例通过 | 通过 |
| TC-HWK-03 | FR-HWK-01 | API-HWK-18；DB-HWK-03、07 | 代码题未配置测试用例 | 执行发布 | 返回 `HWK_4007`，状态不变 | `codeHomeworkWithoutTestCasesIsRejectedWhenPublishing`、前端代码题校验用例通过 | 通过 |
| TC-HWK-04 | FR-HWK-02 | UI-HWK-04；API-HWK-06、17；DB-HWK-02、03 | 已发布客观题/代码题，包含标准答案和隐藏用例 | 学生打开作业详情 | 显示说明和提交要求，不泄露答案、隐藏用例输出 | `studentPublishedHomeworkListAndDetailDoNotExposeAnswersOrHiddenTestCaseOutput` 通过 | 通过 |
| TC-HWK-05 | FR-HWK-02 | UI-HWK-05；API-HWK-07；DB-HWK-04 | 学生为课程成员；作业已发布且未截止 | 提交文本、客观题或代码答案 | 生成提交记录，返回提交编号、时间和初始评测/批阅状态 | `studentSubmitsPublishedTextHomeworkAndReceivesSubmissionReceipt`、前端学生提交用例通过 | 通过 |
| TC-HWK-06 | FR-HWK-02 | API-HWK-07；错误码 `HWK_4004` | 作业已超过截止时间且不允许迟交 | 学生提交作业 | 返回截止错误，不生成有效提交 | `studentCannotSubmitAfterDeadlineWhenLateSubmitIsDisabled` 通过 | 通过 |
| TC-HWK-07 | FR-HWK-03 | UI-HWK-06；API-HWK-08；DB-HWK-04 | 作业允许重复提交，学生提交多次 | 查询我的提交历史 | 历史完整，仅最新提交 `is_final=1` | `studentSubmissionHistoryKeepsPreviousVersionsAndMarksOnlyLatestFinal`、前端历史用例通过 | 通过 |
| TC-HWK-08 | FR-HWK-03、05 | UI-HWK-06、08；API-HWK-09、10；DB-HWK-04 | 教师/助教有权限；含历史、删除、REJECTED、非当前学生、各题型和状态 | 不传 attention 回归旧列表；分别传两类 attention 并组合旧筛选、翻页和恢复 URL | 未传时行为不变；attention 只返回当前活跃学生的最终有效 SUBMITTED/LATE，状态语义正确，按 `submitted_at DESC,id DESC` 稳定分页 | Controller/Repository 及前端队列、路由用例通过；真实深链刷新、返回和前进/后退通过 | 通过 |
| TC-HWK-09 | FR-HWK-04 | API-HWK-07、11；DB-HWK-05 | 客观题作业配置标准答案和分值 | 学生提交客观题答案，查询评测结果 | 自动计算分数，生成评测记录 | `objectiveHomeworkSubmissionCreatesEvaluationRecordAndResultView` 通过 | 通过 |
| TC-HWK-10 | FR-HWK-04 | UI-HWK-05、07；API-HWK-07、10、11；DB-HWK-03、05 | 代码题配置 IO 用例和语言白名单 | 学生提交代码；不调用 API-HWK-11，仅轮询提交详情 | 独立 Worker 将 PENDING 推进到终态并返回通过用例数和分数 | `codeHomeworkSubmissionEvaluatesIoCasesAndTeacherCanReevaluate` 不读取 API-HWK-11 即等待到 ACCEPTED；E2E 仅轮询 API-HWK-10 并断言 100 分 | 通过 |
| TC-HWK-11 | FR-HWK-04；NFR-HWK-01 | API-HWK-07、10、11；DB-HWK-04、05 | 代码提交包含编译错误或评测器异常 | 学生提交代码；不调用 API-HWK-11，仅轮询提交详情 | Worker 记录 COMPILE_ERROR/SYSTEM_ERROR，提交记录不丢失 | E2E 编译错误进入 COMPILE_ERROR；`codeHomeworkWorkerFailurePreservesSubmissionAndRecordsSystemError` 证明异常落 SYSTEM_ERROR 且提交保留 | 通过 |
| TC-HWK-12 | FR-HWK-04、05 | UI-HWK-08；API-HWK-12；DB-HWK-05、06 | 已有提交和评测记录；教师提供重评理由 | 教师触发重评 | 新增评测记录，保留旧记录，写入重评日志 | `codeHomeworkSubmissionEvaluatesIoCasesAndTeacherCanReevaluate`、`objectiveReevaluationUpdatesSubmissionSummary` 通过 | 通过 |
| TC-HWK-13 | FR-HWK-05 | UI-HWK-08；API-HWK-13；DB-HWK-04、06 | 教师/助教有课程管理权限；存在待批阅提交 | 填写人工分数和评语 | 更新 manualScore、finalScore、comment，写入日志 | `courseManagerReviewsSubmissionAndReadsReviewAuditLogs`、前端教师批阅用例通过 | 通过 |
| TC-HWK-14 | FR-HWK-05 | API-HWK-13；错误码 `HWK_4008` | 作业总分 100，教师填写超出总分的分数 | 提交批阅 | 返回分数范围错误，不更新成绩 | `teacherReviewRejectsScoreOutsideHomeworkTotalScore` 通过 | 通过 |
| TC-HWK-15 | FR-HWK-05；NFR-HWK-03 | API-HWK-12、13、21；DB-HWK-06 | 存在批阅、重评和发布成绩操作 | 查询批阅日志 | 日志记录操作人、时间、原因和分数变化 | `courseManagerReviewsSubmissionAndReadsReviewAuditLogs`、`studentCannotReadPrivateReviewLogs` 通过 | 通过 |
| TC-HWK-16 | FR-HWK-06 | UI-HWK-07；API-HWK-10、11、14 | 学生成绩已发布 | 学生查询详情和反馈 | 展示允许公开的评测摘要、成绩和教师评语 | `scorePublishExposesStudentFeedbackAndHomeworkSourceGrades` 通过 | 通过 |
| TC-HWK-17 | FR-HWK-06；NFR-HWK-04 | API-HWK-08、10、11 | 学生成绩未发布 | 学生查询历史、详情和评测结果 | 不显示未公开最终分和教师评语 | `studentHistoryAndDetailHideUnpublishedScoresAndTeacherComment`、`objectiveHomeworkSubmissionShowsEvaluationButHidesUnpublishedFinalScore` 通过 | 通过 |
| TC-HWK-18 | FR-HWK-05、06；NFR-HWK-02、04 | UI-HWK-08、09；API-HWK-09、15；DB-HWK-04、05 | 五档边界、非 100 满分、空分布、无分数、历史/删除/REJECTED/非当前学生、TEXT/FILE NONE、代码评测中/终态样本 | 教师查询统计并切换未提交、待评测、待批阅；验证分页、生成时间、URL、权限和姓名失败 | 保留旧字段并返回六个新增字段；五档固定且归一化正确，`scoredCount` 等于档位合计；评测/批阅/活跃学生口径准确；三类名单服务端分页稳定、URL 可恢复；学生/无权限教师 403 且无泄漏 | 后端聚合/Controller、前端 100 条 focused、全量回归与 9 张浏览器证据通过 | 通过 |
| TC-HWK-19 | FR-HWK-01；NFR-HWK-01、03、04、05 | UI-HWK-01；API-HWK-22；DB-HWK-01~07；HWK_4001/HWK_4031/HWK_4095 | DRAFT/全部非 DRAFT、课程管理者/无权限用户、已删除作业、完整子数据和历史、删除前旧更新、当前页唯一草稿 | 验证成功、取消无请求、无权限、非 DRAFT、重复删除、删除与编辑/发布竞争、子历史保留、pending 互斥、失败保留、成功刷新/末页回退和 1440px/390px | 成功返回 `deleted=true` 与删除时间；403/404/409 分类准确；普通更新不能复活；只删除父表；仅 DRAFT 显示入口，页面反馈和页码正确 | `courseManagerSoftDeletesDraftAndPreservesHomeworkHistory`、`onlyDraftHomeworkCanBeDeleted`、`staleEditAndPublishCannotRestoreDeletedDraft`、三条并发分类服务测试、教师页删除交互/API 单测通过；后端 290 tests、前端 511 tests；4 张浏览器截图通过 | 通过 |
| TC-HWK-20 | FR-HWK-02；NFR-HWK-03、04 | API-HWK-23；DB-HWK-08 | 课程学生、已发布 FILE 作业、合法白名单文件 | multipart 单 `file` 上传并查询 | 返回服务端 UUID、安全元数据、UPLOADED 与 24h 过期时间，不泄露内部存储引用 | 自动化与浏览器通过；真实 `fileId=9931efa8-57f9-4b18-9636-d14d96c43ad0` | 通过 |
| TC-HWK-21 | FR-HWK-02；NFR-HWK-04 | API-HWK-23；HWK_4031/4042 | 非成员、他人学生、跨课程/作业 UUID | 上传、GET、DELETE 或绑定 | 只允许本人/本课程/本作业；格式合法但未知/跨归属 UUID 用 404 隐藏 | 第二学生入课前上传 403/HWK_4031；入课后他人 fileId 读取/绑定 404/HWK_4042 | 通过 |
| TC-HWK-22 | FR-HWK-02；NFR-HWK-04 | API-HWK-23；HWK_4005/4131/4151 | 空文件、10 MiB 边界、11 类白名单、扩展/MIME/签名伪装 | 上传并核对错误和物理/元数据副作用 | 空文件或内容签名/结构无效为 HWK_4005；超限为 HWK_4131；扩展名或声明 MIME 不支持/不匹配为 HWK_4151 | 自动化通过；浏览器伪装 PDF 返回 400/HWK_4005 | 通过 |
| TC-HWK-23 | FR-HWK-02；NFR-HWK-01、03 | API-HWK-07/23；DB-HWK-04/08 | FILE `fileIds` 空/多值/非 UUID、过期/重用/并发资产 | 提交并核对提交/资产状态 | 恰好一个 UUID；提交与 UPLOADED→BOUND 同事务，非法情况不产生孤儿提交 | 自动化通过；浏览器提交 201，`submission=950304` | 通过 |
| TC-HWK-24 | NFR-HWK-01、03、05 | DB-HWK-08；迁移/补偿/清理 | 存储成功后 DB 失败、首次删除失败、跨存储实例、过期 UPLOADED/DELETED、H2/MySQL/Compose | 注入失败，运行持久 journal、迁移和清理 | DB 回滚不留有效元数据；立即删除失败持久 marker，定时重试成功后删对象并 ack；跨实例可恢复 | `failedDatabaseInsertAndImmediateDeletePersistCleanupUntilRetrySucceeds`、`deferredDeletionQueueSurvivesStorageServiceRestartAndClearsAfterSuccess` 通过；MySQL 9.6 fresh/重复迁移通过 | 通过 |
| TC-HWK-25 | FR-HWK-03、05；NFR-HWK-03、04 | API-HWK-08/09/10；DB-HWK-08 | 多版本 FILE 提交与不可用资产 | 查询学生历史/教师详情 | 每个版本只返回精确绑定的安全附件摘要，不串版或泄露存储引用 | 自动化与 MAN-HWK-012 历史/批阅页通过 | 通过 |
| TC-HWK-26 | FR-HWK-03、05；NFR-HWK-04 | API-HWK-24 | 提交者、课程管理者、匿名、他人提交 | 下载并核对鉴权、响应头和 SHA-256 | 每次重鉴权；仅提交者/课程管理者成功；精确版本且不泄露内部引用 | 学生/教师 SHA-256 均 `d1847d02cb36254509d0ec2df0eaf20805ce3f6aed4e25a809aea88f8d8568fa`；匿名 401，他人 403 | 通过 |
| TC-HWK-27 | FR-HWK-02、03；NFR-HWK-05 | UI-HWK-05/06/08；API-HWK-23/24 | 上传/恢复失败、迟到响应、路由切换、sessionStorage | 选择→上传→失败保留/重试/移除→提交；刷新恢复 GET 失败后重载 | 无假 fileId/路由污染；sessionStorage 不含 File/本地路径；恢复失败保留 session/UUID | 前端 53 files / 545 tests；`fileId=d3b0f3f1-e989-4a6f-8665-ba35daa29329` 恢复 GET 500/HWK_5002 后保留，解除 mock 后 GET 200、DELETE 200 | 通过 |
| TC-HWK-N01 | NFR-HWK-01 | API-HWK-03、07、11、13 | 模拟通知投递失败、评测失败、重复提交冲突 | 执行发布、提交、查询和批阅 | 主数据保持一致，错误以受控响应返回 | `publishRollsBackHomeworkWhenRequiredNotificationDeliveryFails` 断言 `503/HWK_5003`、`DRAFT` 与无通知残留；重复提交冲突用例通过 | 通过 |
| TC-HWK-N02 | NFR-HWK-02 | API-HWK-05、09、15；组合索引与增量迁移 | 数据量大于单页，包含活跃/退出学生和多版本提交 | 查询三类名单和统计，检查 Repository 查询及迁移元数据 | 1 基页码、size 1～100、稳定排序和聚合总数正确；统计为 SQL 聚合，不加载全部最终提交；组合索引存在且列顺序正确 | SQL 聚合、极大页码、fresh/H2/存量 MySQL 脚本契约 10 条通过；真实 MySQL EXPLAIN 待部署复核 | 有条件通过 |
| TC-HWK-N03 | NFR-HWK-03 | API-HWK-10、20、21；DB-HWK-04、05、06 | 存在多次提交、评测、重评、批阅 | 查询详情、评测日志、批阅日志 | 提交和日志可追溯 | 迁移测试和控制器日志用例通过 | 通过 |
| TC-HWK-N04 | NFR-HWK-04 | 全部 HWK 接口；重点 API-HWK-09、15 | 学生、无权限教师、姓名服务失败、缓存数据 | 越权查询统计/名单并触发姓名降级 | 返回 `HWK_4031`/403 且不泄露统计、名单或学生标识；页面不展示裸 `studentId` | 专属 403 自动化通过；390px 学生深链落到 403；姓名服务 503 时只显示安全占位 | 通过 |
| TC-HWK-N05 | NFR-HWK-05 | #225 后端、前端、迁移和浏览器流程 | 稳定边界数据、MockMvc、Vitest、H2/MySQL 脚本、1440/390 浏览器 | 重复执行增量自动化与浏览器脚本/清单 | 结果可重复，命令、数量、截图和失败重试均可追溯 | RED/GREEN、全量计数、9 张截图及迁移入口已落档 | 通过 |

### 7.3 前端 HWK 用例摘要

| 测试文件 | 覆盖内容 | 结果 |
| --- | --- | --- |
| `frontend/tests/unit/hwk/homeworksApi.spec.ts` | API-HWK-01 ~ 22 路由构造、请求方法、参数、ApiResponse 解包；API-HWK-22 DELETE 路径；attention 透传和统计字段 | 所属前端全量 53 files / 511 tests 通过 |
| `frontend/tests/unit/hwk/HomeworkStudentListView.spec.ts` | 学生作业列表、详情链接、空状态 | 2 条通过 |
| `frontend/tests/unit/hwk/HomeworkStudentView.spec.ts` | 学生详情、文本提交、空提交校验、代码语言选择、评测结果、学习进度记录、断点恢复 | 7 条通过 |
| `frontend/tests/unit/hwk/HomeworkSubmissionHistoryView.spec.ts` | 学生历史、教师分页列表、教师批阅、重评、筛选、空状态 | 6 条通过 |
| `frontend/tests/unit/hwk/HomeworkTeacherView.spec.ts` | 教师创建/编辑、代码题测试用例校验、发布/关闭、批阅入口、统计、成绩发布；DRAFT-only 删除、确认取消、pending、失败保留和末页回退 | 删除交互用例及所属前端全量 53 files / 511 tests 通过 |
| `frontend/tests/unit/hwk/HomeworkStatisticsView.spec.ts` | 固定五档、空分布、三类 Tab、服务端分页、姓名失败隐私和深链 | 12 条通过 |
| `frontend/tests/unit/hwk/HomeworkSubmissionWorkspaceView.spec.ts` | attention URL 恢复、刷新、前进/后退、旧筛选兼容与分页 | 22 条通过 |
| `frontend/tests/unit/app/router.spec.ts` | 统计页和提交队列 query 深链恢复 | 全文件 37 条通过 |

## 8 测试执行日志

HWK-LOG-001 ~ HWK-LOG-013 为 2026-06-09 的 V1.0 历史日志；HWK-LOG-014 起为 2026-08-22 的 #225 RED/GREEN 与全量证据。

### 8.1 后端 HWK 执行日志

| 日志编号 | 时间 | 命令/测试类 | 执行内容 | 结果 |
| --- | --- | --- | --- | --- |
| HWK-LOG-001 | 2026-06-09 16:30 | Maven 目标测试 | 普通沙箱执行 HWK 后端目标测试 | 因 `backend/target/classes/schema.sql` 写入受限失败，未进入断言阶段 |
| HWK-LOG-002 | 2026-06-09 16:31 | `HomeworkBearerAuthControllerTest` | Bearer 登录态、AUTH/CRS 成员联动、作业可见性、提交和批阅权限 | 2 条通过 |
| HWK-LOG-003 | 2026-06-09 16:31 | `HomeworkControllerTest` | HWK 控制器主流程、异常、权限、评测、批阅、统计、通知和成绩来源 | 35 条通过 |
| HWK-LOG-004 | 2026-06-09 16:31 | `HomeworkMigrationTest` | HWK 迁移语法、外键、唯一约束、提交/评测/批阅日志表契约 | 6 条通过 |
| HWK-LOG-005 | 2026-06-09 16:31 | `HomeworkSubmissionServiceTest` | 重复提交版本冲突返回受控业务错误 | 1 条通过 |
| HWK-LOG-006 | 2026-06-09 16:31 | Maven 汇总 | `Tests run: 44, Failures: 0, Errors: 0, Skipped: 0` | 构建成功 |
| HWK-LOG-014 | 2026-08-22 | 初始 RED 55 条 | 固定五档、有效范围、attention、SQL Repository 与组合索引 | 7 failures + 6 errors，按预期证明契约缺失 |
| HWK-LOG-015 | 2026-08-22 | 边界 RED 49 条 | 极大页码、负分/超满分脏数据、CODE 超满分写入 | 2 failures + 1 error，按预期失败 |
| HWK-LOG-016 | 2026-08-22 | 迁移 RED 10 条 | attention 索引列序、重试安全与存量 Compose 升级入口 | 3 failures + 1 error，按预期失败 |
| HWK-LOG-017 | 2026-08-22 | `mvn test` | #225 GREEN 后端全量回归 | 283 条执行，0 失败，0 错误，1 条 Docker 专项跳过，BUILD SUCCESS |
| HWK-LOG-018 | 2026-08-22 | `HomeworkMigrationTest`、`sh -n` | fresh/H2 索引、MySQL 守卫/原子/重试契约和升级脚本语法 | 10 条通过；shell 语法通过 |
| HWK-LOG-023 | 2026-08-25 | 11 个 `com.onlinejudge.hwk` 测试类 | #264 当前基线 HWK Controller/Service/Repository/迁移/附件定向回归 | 101 条通过；0 失败、0 错误、0 跳过；BUILD SUCCESS |
| HWK-LOG-024 | 2026-08-25 | `mvn test` | #264 后端全量回归 | 368 条执行；0 失败、0 错误、1 条 Docker-only 跳过；BUILD SUCCESS |

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
| HWK-LOG-019 | 2026-08-22 | 前端 focused RED | 统计、队列、批阅、API、路由契约 | 14 failures / 85 passed，按预期失败 |
| HWK-LOG-020 | 2026-08-22 | 前端 focused GREEN | 统计、队列、批阅、API、路由契约 | 5 files / 100 tests 通过 |
| HWK-LOG-021 | 2026-08-22 | `npm run test:unit`、`typecheck`、`build` | 前端全量、类型与生产构建 | 53 files / 506 tests、类型检查、189 modules 构建通过 |
| HWK-LOG-022 | 2026-08-22 | Playwright Chromium | H2 + Vite + fake sandbox，1440/390、深链、键盘、403、姓名 503 | 9 张截图通过，见 `output/playwright/issue-225/README.md` |
| HWK-LOG-025 | 2026-08-25 | Vitest `tests/unit/hwk` | #264 当前基线 HWK 页面、API、权限、异常和附件定向回归 | 11 files / 182 tests；全部通过 |
| HWK-LOG-026 | 2026-08-25 | Vitest 全量、`vue-tsc --noEmit`、Vite build | #264 前端全量回归、类型检查和生产构建 | 53 files / 551 tests、类型检查、189 modules 构建全部通过 |
| HWK-LOG-027 | 2026-08-25 | `verify-hwk-doc-test-closure.test.sh` | 拆分 SSD、三层图、场景分类与 E2E 存在性契约 | 图组 RED 因教师发布 SSD 缺失失败；规范调整后旧图号断言再次 RED；E2E RED 因 HWK 场景文件缺失失败；GREEN 通过 |
| HWK-LOG-028 | 2026-08-25 | Playwright `homework-lifecycle.spec.ts` | 教师页面创建/发布→学生 TEXT 提交→教师批阅/发布成绩→学生结果；OBJECTIVE/CODE/FILE、评测/附件异常、过期、越权、重评；LRN/GRD 真实 API 边界 | runner 2/2 PASS；其中 CODE 通过 API-HWK-11 读取触发评测，产品结论已由 HWK-LOG-031 推翻；LRN/GRD 断言仍有效 |
| HWK-LOG-029 | 2026-08-26 | #281 定向、Playwright、Maven/Vitest 全量、typecheck/build | 合并 `origin/dev@a30a096` 后复测通知失败整体回滚；共享演示账号场景改为串行避免跨场景会话竞争 | 通知/LRN 9/9、HWK E2E runner 2/2、后端 375（5 skipped）、前端 53 files / 556、typecheck、189 modules build 均通过；CODE 完整闭环结论已由 HWK-LOG-031 推翻 |
| HWK-LOG-030 | 2026-08-26 | `render-mermaid.mjs`、`verify-hwk-doc-test-closure.test.sh` | 六张新增 UML 从专用图源回退为仓库统一 Mermaid `.mmd`，生成白底 SVG 并更新需求/概要/详细三层引用 | 6/6 SVG 渲染成功；组合片段包含明确 `alt/else` 分支；闭环契约与静态图目视检查 PASS |
| HWK-LOG-031 | 2026-08-27 | `homework-lifecycle.spec.ts`、`verify-mermaid-svg.test.mjs` | 不读取 API-HWK-11 观察 CODE 提交状态；交换 SVG 消息边 from/to 验证语义比较器 | CODE 提交等待后仍为 PENDING，FR-HWK-04 判定 FAIL；反向消息边被比较器拒绝 |
| HWK-LOG-032 | 2026-08-27 | `HomeworkControllerTest`、`HomeworkSubmissionServiceTest`、后端全量、`homework-lifecycle.spec.ts` | 提交后不读 API-HWK-11 等待后台终态；验证 PENDING 纯读与评测器异常落库 | 后端相邻 49/49、全量 390（383 passed、7 skipped）PASS；E2E 契约改为仅轮询 API-HWK-10，typecheck/build/Playwright 列表通过，共享 Compose runner 待复测 |

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
| MAN-HWK-010 | HWK/CRS | #225 统计与待处理响应式验收 | 使用有权限教师在 1440px 和 390px 下查看五档和三类 Tab，翻页、深链提交队列、刷新/前进/后退、键盘操作；再以学生访问并模拟姓名服务失败 | 五档和生成时间清晰，三类名单服务端分页稳定且 URL 恢复；窄屏无横向溢出；学生落到 403，姓名失败不展示裸 `studentId` | 通过；9 张截图及尺寸、URL、控制台记录见 `output/playwright/issue-225/README.md` |
| MAN-HWK-011 | HWK/CRS | #224 草稿逻辑删除与响应式教师入口 | 使用有权限教师在 1440px/390px 查看 DRAFT 与非 DRAFT；验证取消无请求、确认删除和成功刷新；失败保留/pending/末页回退由组件测试覆盖 | 仅 DRAFT 显示入口；窄屏无溢出；反馈明确；成功后作业消失且页码有效，失败时原行、筛选和页码不丢失 | 通过；真实 `DELETE /api/v1/homeworks/950312` 返回 200、`deleted=true`，总数 3→2；1440×900/390×844 四张截图，390px 无溢出，控制台 0 error/0 warning；见 `output/playwright/issue-224/README.md` |
| MAN-HWK-012 | HWK/CRS/AUTH | #214 FILE 附件上传与安全提交 | H2 真实服务；教师/两学生/匿名；验证伪装文件、存储失败重试、刷新恢复、提交、受控下载、越权、恢复 GET 失败保留/重载/删除，检查 1440×1000/390×844 布局与 Console | 伪装 PDF 400/HWK_4005；存储 500/HWK_5002 后重试 201；提交 201；学生/教师哈希一致；匿名/他人被拒绝；恢复 GET 5002 保留 session/UUID，解除后 GET 200 + DELETE 200；无溢出和脚本 Console error | 通过；自动化后端 340 total = 339 passed + 1 Docker-only skipped（0 failures/0 errors）、定向 9 类 94/94、前端 53 files / 545 tests + typecheck/build；MySQL 9.6 fresh/重复迁移通过；截图 `output/playwright/issue-214/01~10` |

## 10 缺陷、风险与处理建议

| 风险编号 | 风险说明 | 影响范围 | 建议处理 |
| --- | --- | --- | --- |
| R-HWK-001 | #264 已执行共享 Playwright 两条场景；MAN-HWK-001 ~ MAN-HWK-006 的全页面、多视口人工走查仍需在发布候选环境复核 | UI-HWK-01 ~ UI-HWK-09 | 发布候选环境按 MAN-HWK-001 ~ MAN-HWK-006 补充视觉与会话异常走查；不得覆盖 #296 的后台终态自动化证据 |
| R-HWK-002 | #296 已消除读请求触发评测缺陷；剩余风险为真实 Docker 多语言、资源限制与高并发专项未在本机执行 | FR-HWK-04、NFR-HWK-01、NFR-HWK-02、NFR-HWK-05 | 后台 Worker、原子认领、纯读取与 SYSTEM_ERROR 兜底已自动化通过；部署环境继续执行真实 Docker 专项，不回退为 API-HWK-11 同步评测 |
| R-HWK-003 | #264 已通过真实本地服务验证 LRN 通知与 GRD 成绩同步边界；生产环境联调尚未记录完整结果 | FR-HWK-06、NFR-HWK-03 | 在统一测试环境复跑作业发布、成绩发布、通知中心和成绩同步闭环 |
| R-HWK-004 | Maven 和 Vitest 在普通沙箱下存在写入/子进程权限限制 | 本地验证流程 | 本地开发机可直接运行；受限环境下需使用已批准的提权命令 |
| R-HWK-005 | 本机 Docker daemon socket 不存在，#225 存量迁移尚未在真实 MySQL 8.4 容器执行首次、重跑与 EXPLAIN | DB-HWK-04、TC-HWK-N02、部署升级 | 当前由 H2 执行测试、MySQL 脚本静态契约和 shell 语法覆盖；部署时按 `apply-compose-migration.sh` 入口实跑并保存输出 |
| R-HWK-006 | #224 浏览器使用 H2 与 fake sandbox，未在生产数据库上复测条件 UPDATE/FOR UPDATE 的并发语义 | API-HWK-22、DB-HWK-01 | Repository/Service 自动化与 SQL 契约已覆盖并发分类和防复活；部署环境复测时补 MySQL 当前读证据，不影响本地验收结论 |
| R-HWK-007 | #214 存储根目录整卷不可写时，物理删除和 `.pending-deletes` marker 可同时失败；病毒扫描未纳入本期 | TC-HWK-22/24、部署运维 | journal 失败不被吞掉；修复卷后使用配对 DB+完整卷（含隐藏 marker）受限对账。生产上线前补恶意载荷扫描/隔离与告警治理 |

## 11 验收结论

| 验收项 | 结论 | 说明 |
| --- | --- | --- |
| 功能覆盖 | 通过 | 固定五档/attention、草稿逻辑删除等既有证据保留；#296 补齐 CODE 后台 Worker，FR-HWK-04 与 TC-HWK-10/11 通过 |
| 接口覆盖 | 通过 | API-HWK-07 创建 PENDING 任务并在提交后异步消费；API-HWK-10/11 只读状态，不新增或修改公共请求/响应契约 |
| 页面覆盖 | 通过 | UI-HWK-01 删除交互单测及 1440×900/390×844 浏览器证据通过，390px 无横向溢出且控制台干净 |
| 数据一致性 | 有条件通过 | 父表原子软删、普通更新防复活、六类子记录保留自动化通过；真实 MySQL 并发当前读仍按部署环境复核 |
| 权限与安全 | 通过 | 草稿删除无权限 403、非 DRAFT 409、重复删除 404；既有统计权限和隐私证据保持通过 |
| 非功能 | 有条件通过 | 分页、稳定排序、SQL 聚合、索引和后台任务可重复认领已有证据；生产压测、真实 MySQL EXPLAIN 和真实 Docker 专项仍待部署环境执行 |
| 最终结论 | #296 PASS | 后台 Worker、API-HWK-11 纯读取、异常终态和不读取 API-HWK-11 的验收契约已补齐；#264 已由 PR #276 完成文档与测试交付，#296 合并后独立关闭 FR-HWK-04 产品缺陷 |

## 12 附录

### 12.1 执行命令

V1.0 历史命令保留如下；#224/#225 的历史结果与 #264 的实际全量、专项和共享 E2E 命令附在其后。

```powershell
cd D:\repos\OnlineJudge\backend
& 'D:\Program Files\apache-maven-3.9.16\bin\mvn.cmd' test '-Dtest=HomeworkControllerTest,HomeworkBearerAuthControllerTest,HomeworkMigrationTest,HomeworkSubmissionServiceTest'

cd D:\repos\OnlineJudge\frontend
& 'D:\Program Files\nodejs\node.exe' '.\node_modules\vitest\vitest.mjs' run tests/unit/hwk/homeworksApi.spec.ts tests/unit/hwk/HomeworkStudentListView.spec.ts tests/unit/hwk/HomeworkStudentView.spec.ts tests/unit/hwk/HomeworkSubmissionHistoryView.spec.ts tests/unit/hwk/HomeworkTeacherView.spec.ts --pool=threads
```

```bash
cd backend
mvn test
mvn -Dtest=com.onlinejudge.hwk.database.HomeworkMigrationTest test

cd ../frontend
npm run test:unit
npm run typecheck
npm run build

cd ..
sh -n database/mysql/apply-compose-migration.sh
```

### 12.2 V1.0 历史执行摘要与 #224/#225/#264 状态

| 项目 | 摘要 |
| --- | --- |
| V1.0 后端 HWK 自动化测试 | 4 个测试类，44 passed / 0 failed / 0 errors / 0 skipped（2026-06-09 历史记录） |
| V1.0 前端 HWK 自动化测试 | 5 files passed / 28 tests passed（2026-06-09 历史记录） |
| V1.0 自动化覆盖 | 作业创建/发布、提交、历史、自动评测、重评、批阅、基础统计、通用权限、隐藏数据、建表约束、AUTH/CRS 联动 |
| #225 后端 | 283 tests / 0 failures / 0 errors / 1 skipped；迁移专项 10/10 |
| #225 前端 | 53 files / 506 tests；typecheck 与 189 modules build 通过 |
| #225 浏览器 | MAN-HWK-010 通过；9 张截图见 `output/playwright/issue-225/README.md` |
| #224 后端 | 290 tests / 0 failures / 0 errors / 1 skipped；跳过项为 Docker 沙箱环境假设 |
| #224 前端 | 53 files / 511 tests；typecheck 与生产 build 通过 |
| #224 浏览器 | MAN-HWK-011 通过；真实 DELETE 200/deleted=true；1440×900/390×844 四张截图见 `output/playwright/issue-224/README.md` |
| #264 自动化 | HWK 后端 101/101、HWK 前端 182/182、通知/LRN 定向 9/9；全量后端 375 tests（5 个环境专项 skipped）、全量前端 556/556；共享入口契约 3/3；typecheck、189 modules build、文档闭环契约通过 |
| #264 共享 E2E | Playwright runner 原始计数：2 passed / 0 failed；复用 #267/#268 的公共夹具，串行使用共享演示账号。第二个通过的断言在不读取 API-HWK-11 时复现 CODE 持续 PENDING，因此 FR-HWK-04 的后台 Worker 产品验收仍为 FAIL，由 #296 独立修复 |
| #296 自动化 | 后端相邻 49/49、全量 390（383 passed、7 skipped）；前端 typecheck、189 modules build、Playwright 2 条用例加载、文档/SVG 语义契约通过；Compose runner 因 Docker daemon 不可用待复测 |
| 手工/联调状态 | #224/#225 已验收；#264 真实本地 LRN/GRD 边界通过；真实 MySQL 容器、真实 Docker 沙箱和生产环境联调仍按风险项复核 |
