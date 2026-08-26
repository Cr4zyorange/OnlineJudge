# TST-DOC-07 GRD 成绩评价与教学分析测试文档

| 文档编号 | TST-DOC-07 |
| --- | --- |
| 文档名称 | GRD 成绩评价与教学分析测试文档 |
| 项目名称 | 在线教学与实训平台 |
| 所属阶段 | 系统测试与验收测试 |
| 报告版本 | V1.9 |
| 编写日期 | 2026-08-26 |
| 编写人 | GRD 模块负责人 |
| 对应 issue | #158、#266 D2-GRD 业务场景文档与测试闭环 |
| 测试范围 | GRD 成绩项配置、来源成绩同步、总评计算、教师成绩管理、成绩发布、学生成绩查询、教学分析、成绩异议复核、权限、安全、跨模块成绩来源与通知 |
| 测试结论 | GRD 单元/接口/迁移/集成测试及真实 LAB/HWK → GRD → LRN API E2E 闭环通过；来源事务提交后的 LRN 通知落库交由 #283 / PR #284 独立实现与验证，PR #270 不包含 LRN 生产代码；页面视觉验收和生产级性能压测仍需专项确认 |

## 1 文档控制

### 1.1 修订记录

| 版本 | 日期 | 修订人 | 修订说明 |
| --- | --- | --- | --- |
| V1.0 | 2026-06-10 | GRD 模块负责人 | 按 #152 统一结构整理 GRD 测试范围、测试数据、用例追踪、自动化执行日志、手工验收点和残余风险 |
| V1.1 | 2026-08-25 | GRD 模块负责人 | 按 #266 补齐来源超时/删除回归、真实 LAB/HWK → GRD → LRN 共享 E2E、权限与幂等边界，并回填当前基线证据 |
| V1.2 | 2026-08-25 | GRD 模块负责人 | 按 PR #270 评审意见对齐成绩项/异议响应真实契约，并将变异型 E2E 收口到自动清理的 disposable H2 包装入口 |
| V1.3 | 2026-08-25 | GRD 模块负责人 | 按 PR #270 二轮评审明确 OTHER_COURSE_ITEM 可空编号、同步/发布失败边界和复核调整的真实仓储依赖 |
| V1.4 | 2026-08-25 | GRD 模块负责人 | 按 PR #270 三轮评审区分非课程成员 403 与课程成员成绩未发布 400 的异常契约 |
| V1.5 | 2026-08-25 | GRD 模块负责人 | 按 PR #270 四轮评审增加 disposable 随机 proof 校验，并修正教学分析和复核提交的真实依赖边界 |
| V1.6 | 2026-08-25 | GRD 模块负责人 | 按 PR #270 五轮评审明确空来源结果不可判定任务存在性，并补齐教师复核请求查询与权限拒绝调用链 |
| V1.7 | 2026-08-25 | GRD 模块负责人 | 按 PR #270 六轮评审明确来源提供方未映射异常的通用 500 响应，并补齐教学分析权限拒绝终止路径 |
| V1.8 | 2026-08-25 | GRD 模块负责人 | 按 PR #270 七轮评审隔离外部 Spring/Compose 环境，并补充当时的复核通知时序证据（已由 V1.9 事务边界取代） |
| V1.9 | 2026-08-26 | GRD 模块负责人 | 按 PR #270 打回意见将 LRN 事务缺陷拆为 #283 / PR #284 独立交付；#270 仅更新 GRD 文档、图和契约，合并顺序依赖 #284，并将 GRD SRS 图重编为 4-56 ~ 4-59 |

### 1.2 审批记录

| 角色 | 姓名/负责人 | 审批意见 | 日期 |
| --- | --- | --- | --- |
| 项目负责人 | 待填写 | 待审批 | 2026-06-10 |
| 测试负责人 | @MontesquieuE | 待整合确认 | 2026-06-10 |
| GRD 模块负责人 | GRD 负责人 | 待确认 | 2026-06-10 |

## 2 测试概述

本文件用于记录 GRD 成绩评价与教学分析模块在当前版本下的测试依据、测试环境、测试数据、测试用例、执行结果、手工验收清单、缺陷风险和验收结论。覆盖范围对齐 `FR-GR-01 ~ FR-GR-07`、`NFR-GR-01 ~ NFR-GR-05`、`UI-GRD-01 ~ UI-GRD-10`、`API-GRD-01 ~ API-GRD-21`、`DB-GRD-01 ~ DB-GRD-08`、`TC-GR-01 ~ TC-GR-12`。

当前已执行 GRD 后端 Spring Boot 自动化测试、前端 Vue/Vitest 单元测试、文档/E2E 契约测试，并通过 disposable 包装入口在独立临时 H2 文件库中调用真实 AUTH、CRS、LAB、HWK、GRD、LRN API。包装脚本在成功、失败和中断时均停止后端并删除临时数据；后端以空环境启动并固定 H2 profile/driver/凭据、SQL 初始化、课程 schema 与演示数据开关，不继承外部 Compose/MySQL 配置；运行前生成一次性随机 token 与权限收紧的 proof 文件，用例联合校验 proof 路径/属主/权限、loopback URL 和仍存活的隔离后端 PID，直接共享环境或手工设置单一运行标志时该变异型场景跳过。闭环覆盖教师和学生真实登录、课程和成员创建、LAB/HWK 提交与评分、成绩项同步和重算、缺失成绩、完整/部分发布、学生查询、异议复核、LRN 发布/复核通知、重复同步/发布幂等、权限拒绝和异常参数。页面视觉状态与生产级性能压测仍列为专项验收项。

## 3 测试依据

| 序号 | 文档/代码依据 | 用途 |
| --- | --- | --- |
| 1 | `docs/开发/GRD-成绩评价与教学分析模块开发流程.md` | GRD 主流程、P0 闭环、权限、来源成绩和跨模块通知要求 |
| 2 | `docs/最终提交/软件需求规格说明书.md` | FR-GR、NFR-GR 需求和验收来源 |
| 3 | `docs/最终提交/软件概要设计说明书.md` | 模块边界、跨模块依赖和系统结构来源 |
| 4 | `docs/最终提交/软件详细设计说明书.md` | UI、API、数据库、异常、状态机、测试编号和追踪矩阵来源 |
| 5 | `docs/过程/需求/成绩评价与教学分析模块.md` | GRD 过程需求补充 |
| 6 | `docs/过程/概要/成绩评价与教学分析模块概要设计提交稿（grd）.md` | GRD 过程概要设计、非功能和页面/API 补充 |
| 7 | `docs/过程/详细设计/GRD-成绩评价与教学分析-详细设计提交稿.md` | GRD 详细流程、状态、异常和测试关注点补充 |
| 8 | `backend/src/test/java/com/onlinejudge/grd` | GRD 后端自动化测试实现 |
| 9 | `frontend/tests/unit/grd`、`frontend/src/views/grd/StudentGradeView.spec.ts` | GRD 前端 API、路由和页面单元测试实现 |
| 10 | `database/migrations/20260525_01_create_grd_grade_item.sql` | GRD 数据表和迁移约束依据 |
| 11 | `frontend/tests/e2e/grd/grade-lifecycle.spec.ts` | 真实 LAB/HWK → GRD → LRN 共享 E2E 闭环、权限、异常和幂等证据 |
| 12 | `frontend/tests/contracts/grd-doc-test-closure.contract.test.mjs` | 五个既有 UC 图组、静态资源引用和共享 E2E 接入契约 |

## 4 测试范围

### 4.1 功能与非功能范围

| 编号 | 测试对象 | 主要验证点 | 当前覆盖状态 |
| --- | --- | --- | --- |
| FR-GR-01 | 成绩项配置与计算规则 | 成绩项查询、创建、修改、停用、规则校验、权重和来源类型；LAB/HWK 正整数编号、OTHER_COURSE_ITEM 可空编号 | 后端和前端自动化已覆盖；当前 GradeItem 修改不接收原因、不检查关联成绩发布状态、不写规则变更日志；现有来源契约无法区分不存在或跨课程与未发布或暂无成绩，空结果统一生成 `MISSING` |
| FR-GR-02 | 成绩汇总与总评生成 | 同步 LAB/HWK 来源成绩、缺失/未评分状态、加权分和总评计算 | 后端、前端和真实来源 API E2E 已覆盖 |
| FR-GR-03 | 教师成绩管理 | 教师总表、学生明细、单项成绩调整、总评调整、变更记录 | 后端和前端自动化已覆盖 |
| FR-GR-04 | 成绩发布与状态控制 | 发布前检查、发布记录、发布后学生可见、相同范围重复发布返回同一 `publishId`、来源事务提交后的通知独立事务语义、发布后调整留痕 | GRD 后端、前端和真实 API E2E 已覆盖；after-commit 回调与 LRN 独立事务由 #283 / PR #284 独立测试与交付，来源事务回滚不生成通知，通知失败不能反向回滚已提交发布；`notificationStatus` 仍为 `SENT` |
| FR-GR-05 | 学生成绩查询与结果展示 | 学生只查看本人已发布成绩、未发布不可见、来源和反馈展示 | 后端、前端和真实 API E2E 已覆盖 |
| FR-GR-06 | 班级成绩统计与教学分析 | 课程总评和成绩项均分、最高分、最低分、及格率、完成率、分布和快照 | 后端和前端自动化已覆盖；当前快照指纹不含 GradeItem 规则。仍启用且计入总评的 LAB/HWK 项可由同步刷新；改为 `includedInFinal=false` 或 `enabled=false` 后不再刷新该项 GradeRecord，成绩项级快照可能继续复用，课程总评仍会重算；大规模性能待专项确认 |
| FR-GR-07 | 成绩异议与复核申请 | 学生提交异议、重复申请拦截、教师同意/驳回、复用调整留痕和通知 | 后端、前端和真实 API E2E 已覆盖；复核通知在来源事务内登记 after-commit 回调，提交后由 LRN 独立事务持久化；该生产修复由 #283 / PR #284 独立交付，#270 不包含 LRN 生产代码 |
| NFR-GR-01 | 可靠性 | 来源同步、重算、发布、通知失败和事务边界保持数据一致 | 自动化覆盖来源超时、来源删除、缺失、重复同步/发布和核心事务边界；来源提供方未映射异常在服务层整体回滚，API 当前返回 HTTP 500、`code=500` 和“系统错误，请联系管理员”；#283 / PR #284 独立验证来源事务回滚时无通知、提交后独立事务落库以及通知失败不反向回滚；当前没有 `FAILED` 回写、持久 outbox 或自动重试 |
| NFR-GR-02 | 性能 | 成绩总表、学生个人成绩、教学分析分页和基础统计响应 | 自动化覆盖基础样本；生产规模压测待补充 |
| NFR-GR-03 | 可追踪性 | 计算批次、发布记录、变更记录、复核记录、统计快照 | 自动化覆盖 |
| NFR-GR-04 | 安全性 | 教师课程权限、学生本人过滤、未发布不可见、无权限复核拒绝 | 自动化覆盖 |
| NFR-GR-05 | 可测试性 | 关键功能、异常、状态流转和跨模块契约可重复验证 | 自动化覆盖 |

### 4.2 页面、接口、数据表覆盖

| 类别 | 编号范围 | 覆盖说明 |
| --- | --- | --- |
| 页面 | UI-GRD-01 ~ UI-GRD-10 | 前端测试覆盖教师成绩项配置、教师成绩总表、学生明细、调整、发布、教学分析、变更记录、学生个人成绩、学生异议、教师复核；共享 Playwright 已验证真实 API 业务闭环，页面视觉仍待手工验收 |
| 接口 | API-GRD-01 ~ API-GRD-21 | 后端 MockMvc 和前端 API wrapper 覆盖主要路由、请求方法、请求体、分页参数、权限、错误码和响应数据 |
| 数据表 | DB-GRD-01 ~ DB-GRD-08 | 迁移测试覆盖成绩项、成绩记录、课程总评、发布记录、计算批次、异议申请、变更日志、统计快照的可执行持久化和关键约束 |
| 跨模块 | AUTH、CRS、LAB、HWK、LRN | 共享 Playwright runner 使用真实登录、课程成员、LAB/HWK 提交评分、GRD 同步发布复核和 LRN 通知 API 完成闭环；`GrdLrnIntegrationTest` 补充服务集成证据 |

### 4.3 仍需专项确认的范围

| 范围项 | 说明 | 处理方式 |
| --- | --- | --- |
| 页面视觉与交互验收 | 共享 Playwright 用例从浏览器进程调用真实 API，但未逐页核对 UI-GRD-01 ~ UI-GRD-10 的布局、加载/空/失败状态和视觉效果 | 作为手工验收用例 MAN-GRD-001、003、005、006、008、009 |
| 生产级性能压测 | 自动化覆盖基础样本和分页，不包含生产规模课程、学生和成绩项压测 | 作为专项性能测试 MAN-GRD-010 |

## 5 测试环境

| 环境项 | 内容 |
| --- | --- |
| 操作系统 | macOS |
| 后端运行环境 | Java 25，Spring Boot 3.4.5，Maven 3.9.11，JUnit 5，MockMvc，H2 |
| 前端运行环境 | Node.js，Vue 3.5，Vite 6.3，Vitest 3.2，Playwright，系统 Chrome |
| 数据库 | 单元/接口测试使用 H2 内存库；真实 E2E 使用独立 H2 文件库；迁移脚本按 MySQL 兼容约束编写 |
| 鉴权方式 | 单元测试使用测试认证上下文；真实 E2E 使用 `/api/v1/auth/login` 返回的 JWT Bearer token |
| 执行日期 | 2026-08-25 |

## 6 测试数据

| 数据类别 | 数据说明 | 使用模块 |
| --- | --- | --- |
| 教师用户 | `X-User-Id=501` 等课程管理者；无权限教师用于课程权限拒绝验证 | GRD、AUTH、CRS |
| 学生用户 | `X-User-Id=101`、`201` 等课程成员；非成员学生用于无权限和本人过滤验证 | GRD、AUTH、CRS |
| 课程数据 | `courseId=101` 等测试课程，包含教师授权、学生名单、非成员和大班发布范围样本 | GRD、CRS |
| 成绩项数据 | LAB/HWK 来源成绩项、总评计入项、禁用项、重复名称、非法权重、非法满分、非法来源编号 | GRD、LAB、HWK |
| 来源成绩数据 | 实验成绩、作业成绩，包含 SCORED、MISSING、UNSUBMITTED、UNGRADED 等状态和更新时间 | GRD、LAB、HWK |
| 成绩记录数据 | rawScore、weightedScore、publishStatus、comment、sourceUpdatedAt、calculatedAt | GRD |
| 总评数据 | finalScore、finalStatus、publishStatus、calculationBatchId、publishedAt | GRD |
| 发布和变更数据 | 发布范围、发布数量、通知状态、单项成绩调整、总评调整、调整原因、操作人 | GRD、LRN |
| 异议数据 | GRADE_ITEM/FINAL_SCORE 目标、PENDING/APPROVED/REJECTED 状态、处理说明、调整后分数 | GRD、LRN |
| 统计数据 | 课程总评和单项成绩的平均分、最高分、最低分、及格率、完成率、分数段分布、来源时间点 | GRD |
| 真实 E2E 数据 | 每次运行动态创建隔离公开课程、2 名学生、1 个 LAB、1 个 HWK 和 2 个各占 50% 的成绩项；一名学生完成并得分，另一名保持缺失 | AUTH、CRS、LAB、HWK、GRD、LRN |

## 7 测试用例汇总

### 7.1 自动化执行结果

| 测试类别 | 命令 | 执行结果 |
| --- | --- | --- |
| 后端 GRD 相关测试 | `mvn test -Dtest=GradeItemControllerTest,GradeRecordControllerTest,GradeItemMigrationTest,GradeAnalysisServiceTest,GradeItemServiceTest,GradeRecordServiceTest,GradeReviewServiceTest,GrdLrnIntegrationTest` | 8 个测试类通过，63 条通过，0 失败，0 错误，0 跳过 |
| 前端 GRD 单元测试 | `node node_modules/vitest/vitest.mjs run tests/unit/grd/gradeItemsApi.spec.ts tests/unit/grd/gradeRecordsApi.spec.ts tests/unit/grd/GradeItemConfigView.spec.ts tests/unit/grd/TeacherGradeTableView.spec.ts tests/unit/grd/App.spec.ts src/views/grd/StudentGradeView.spec.ts --pool=threads` | 6 个测试文件通过，40 条测试通过 |
| GRD 文档/E2E 契约 | `node --test tests/contracts/grd-doc-test-closure.contract.test.mjs` | 5 条通过，0 失败 |
| 真实跨模块 E2E | `E2E_BROWSER_CHANNEL=chrome npm run test:e2e:grd:disposable` | 1 条 disposable 闭环场景通过，0 失败；临时后端和 H2 数据自动清理 |

说明：前端测试运行时 Node 输出 `--localstorage-file` 未提供有效路径的警告，测试断言全部通过；该警告不影响 GRD 页面、路由和 API 用例结果。

### 7.2 GRD 核心用例表

| 用例编号 | 对应需求 | 覆盖对象 | 前置条件/测试数据 | 操作步骤 | 预期结果 | 实际结果 | 通过状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TC-GR-01 | FR-GR-01 | UI-GRD-01；API-GRD-01 ~ 05、07；DB-GRD-01、05 | 教师具备课程管理权限；准备 LAB/HWK 来源编号或 OTHER_COURSE_ITEM、满分、权重、排序数据；另准备已关联发布成绩的 GradeItem | 查询、创建、修改、停用成绩项；保存后单独执行规则校验 | 保存接口返回完整 GradeItem，不附带校验或重算结果；修改请求不要求 reason、不检查发布状态且不写 GradeChangeLog；独立校验接口返回 `valid`、`totalIncludedWeight`、`errors`；OTHER_COURSE_ITEM 可不关联任务；非法权重、重复名称、不支持的来源类型或 LAB/HWK 非正数编号被拒绝；任务不存在或跨课程与任务未发布或暂无成绩均按空来源结果生成 `MISSING` | `GradeItemControllerTest`、`GradeItemServiceTest`、`GradeItemConfigView.spec.ts`、`gradeItemsApi.spec.ts`、`GradeRecordServiceTest` 通过；规则修改审计缺口由文档-实现契约锁定 | 通过 |
| TC-GR-02 | FR-GR-02 | UI-GRD-02；API-GRD-06 ~ 09；DB-GRD-02、03、05 | 课程内有学生名单；LAB/HWK 来源成绩含已评分、缺失、未提交、未评分状态 | 教师以无请求体 POST 同步来源成绩，再单独查询成绩总表和学生明细 | 同步响应返回批次编号及 affectedItem/affectedStudent/synced/missing/ungraded 六项计数，不包含成绩表；独立 GET 返回成绩记录、加权分和总评，缺失状态可见 | `teacherSyncsLabAndHomeworkSourceGradesThenCalculatesFinalScores`、`TeacherGradeTableView.spec.ts` 同步用例通过 | 通过 |
| TC-GR-03 | FR-GR-03 | UI-GRD-02、03、04、08；API-GRD-08 ~ 14；DB-GRD-02、03、07 | 已有成绩记录和课程总评；教师填写调整原因 | 查询总表、进入学生明细、调整单项成绩和总评、查询变更记录 | 分数更新，已发布成绩不回退未发布，变更记录保存旧值、新值、原因和操作人 | `teacherAdjustsGradeRecordWithReasonAndQueriesChangeLogsThroughApi`、`teacherAdjustsCourseFinalScoreWithReasonAndKeepsChangeLog`、前端明细调整用例通过 | 通过 |
| TC-GR-04 | FR-GR-04 | UI-GRD-05；API-GRD-12 ~ 14；DB-GRD-02、03、04、07 | 成绩已计算且可发布；存在选中学生范围 | 教师发布成绩，重复执行同一范围发布，查询发布记录 | 发布后学生可见，记录发布批次；重复发布返回同一 `publishId`，不重复记录或通知；当前首次发布响应返回 `notificationStatus=SENT` | `teacherPublishesSelectedGradesAndEmitsGradePublishedEvent`、`repeatedPublishUsesRangeIdempotencyKeyAndDoesNotNotifyAgain`、`grade-lifecycle.spec.ts`、前端发布记录用例通过 | 通过 |
| TC-GR-05 | FR-GR-05 | UI-GRD-06；API-GRD-15；DB-GRD-02、03 | 准备非课程成员、课程成员未发布成绩和本人已发布成绩 | 学生查询我的课程成绩 | 非成员返回 ERR-GRD-02/403；课程成员未发布返回 ERR-GRD-04/400 且不泄露分数字段；已发布只返回本人数据 | `nonMemberStudentCannotQueryPublishedCourseGradesThroughApi`、真实 E2E 未发布断言、`StudentGradeView.spec.ts` 通过 | 通过 |
| TC-GR-06 | FR-GR-06 | UI-GRD-07；API-GRD-16、17；DB-GRD-08、02、03、09 | 成绩记录包含多分数段、缺失、未评分、未提交样本；另准备仅修改规则、仍计入总评以及改为不计入总评的场景 | 教师查询课程总评分析和成绩项完成情况；修改 LAB/HWK 规则后分别在同步前后查询 | 成绩或学生集合来源版本变化时返回新快照；仅保存规则时允许复用旧快照。仍启用且计入总评的项由同步刷新 GradeRecord；改为 `includedInFinal=false` 时同步不刷新该项，成绩项级快照可能继续复用，课程总评则重算并在下次分析查询生成新快照 | `GradeAnalysisServiceTest`、`teacherQueriesCourseGradeAnalysisThroughApi`、`teacherQueriesGradeItemCompletionThroughApi`、前端分析用例通过；同步筛选和快照限制由文档-实现契约锁定 | 通过 |
| TC-GR-07 | FR-GR-07 | UI-GRD-09、10；API-GRD-18 ~ 21；DB-GRD-06、07 | 学生已有已发布成绩；教师具备课程权限 | 学生提交异议，教师筛选并处理，同意修改或驳回 | 申请状态流转，重复 PENDING 申请被拒绝，同意修改写入变更记录并通知学生 | `GradeReviewServiceTest`、`studentSubmitsGradeReviewAndTeacherProcessesItThroughApi`、前端复核处理用例通过 | 通过 |
| TC-GR-08 | NFR-GR-01 | API-GRD-06、07、12；DB-GRD-02 ~ 05 | 模拟来源刷新、发布、重复发布和大班发布范围 | 同步、重算、发布、重复发布 | 数据事务边界稳定，发布幂等，发布范围摘要有长度边界 | `syncSourceGradesDeclaresTransactionalBoundaryForSyncAndRecalculation`、发布幂等和大班发布用例通过 | 通过 |
| TC-GR-09 | NFR-GR-02 | UI-GRD-02、06、07；API-GRD-08、15、16；DB-GRD-02、03、08 | 准备分页和基础统计样本 | 查询教师总表、学生个人成绩、教学分析 | 接口支持分页和筛选，基础统计可返回 | 后端查询用例和前端分页/分析用例通过；生产规模压测待补充 | 有条件通过 |
| TC-GR-10 | NFR-GR-03 | UI-GRD-08；API-GRD-06、12、13、14、21；DB-GRD-04 ~ 08 | 存在同步、发布、调整、复核、统计流程 | 查询批次、发布记录、变更记录、复核记录和快照 | 发布、分数调整、来源同步实际改变已发布成绩、复核和统计可追踪；当前 GradeItem 规则修改本身不写变更日志 | 迁移、服务和控制器日志/快照用例通过；规则修改审计缺口由契约测试确认 | 通过 |
| TC-GR-11 | NFR-GR-04 | 全部 GRD 页面；全部 GRD API；DB-GRD-02、03、06 | 准备无权限教师、非成员学生、教师访问学生接口、未发布成绩 | 执行越权访问或敏感查询 | 返回受控错误，不泄露他人成绩、全班明细、未发布成绩或无权限复核 | 权限控制器/服务测试和前端未发布状态用例通过 | 通过 |
| TC-GR-12 | NFR-GR-05 | 全部 GRD 流程 | 稳定测试数据、MockMvc、Vitest、H2 迁移 | 重复执行自动化测试 | 核心流程、异常和状态流转可重复验证 | 本文第 8 章命令已通过 | 通过 |

### 7.3 前端 GRD 用例摘要

| 测试文件 | 覆盖内容 | 结果 |
| --- | --- | --- |
| `frontend/tests/unit/grd/gradeItemsApi.spec.ts` | API-GRD-01 ~ 05 路由构造、请求方法、认证上下文缺失失败 | 2 条通过 |
| `frontend/tests/unit/grd/gradeRecordsApi.spec.ts` | API-GRD-06 ~ 21 同步、重算、表格、调整、发布、分析、完成情况、异议接口 | 6 条通过 |
| `frontend/tests/unit/grd/GradeItemConfigView.spec.ts` | 成绩项创建、列表刷新、规则校验、来源编号校验、修改、停用、规则验证 | 7 条通过 |
| `frontend/tests/unit/grd/TeacherGradeTableView.spec.ts` | 来源同步、总表筛选分页、学生明细、单项/总评调整、发布记录、教学分析、异议筛选和处理 | 15 条通过 |
| `frontend/src/views/grd/StudentGradeView.spec.ts` | 学生已发布成绩展示、未发布状态不泄露分数、提交总评异议并展示 PENDING 状态 | 4 条通过 |
| `frontend/tests/unit/grd/App.spec.ts` | GRD 课程导航、教师/学生成绩路由、课程上下文缺失提示、全局路由安全状态 | 6 条通过 |

### 7.4 Issue #266 闭环证据矩阵

| 证据编号 | 场景/边界 | 自动化证据 | 结果 |
| --- | --- | --- | --- |
| E2E-GRD-001 | 主成功：真实 LAB/HWK 提交、评分、同步、总评、部分发布、学生查询、异议驳回、LRN 发布与复核通知 | `frontend/tests/e2e/grd/grade-lifecycle.spec.ts` | 通过 |
| E2E-GRD-002 | 备选：同课程第二名学生来源成绩缺失，分析显示 1/2 完成，完整课程发布被 `ERR-GRD-04` 拒绝，部分发布成功 | `grade-lifecycle.spec.ts` | 通过 |
| E2E-GRD-003 | 状态/幂等：重复来源同步不重复产生结果；同一范围重复发布返回相同 `publishId` | `grade-lifecycle.spec.ts` | 通过 |
| E2E-GRD-004 | 权限/异常：重复 PENDING 异议返回 `ERR-GRD-08`；学生访问教师接口 403；未登录访问 401；非法分析目标 400 | `grade-lifecycle.spec.ts` | 通过 |
| UT-GRD-SOURCE-001 | 来源超时：同步在任何成绩、总评、批次、日志或通知写入前整体失败 | `GradeRecordServiceTest.sourceTimeoutAbortsSyncBeforeWritingPartialGradeState` | 通过 |
| UT-GRD-SOURCE-002 | 来源失败：前一个来源已拉取、后一个来源不可用时，不持久化任何部分成绩状态 | `GradeRecordServiceTest.sourceFailureAfterEarlierSourceFetchDoesNotPersistPartialGradeState` | 通过 |
| UT-GRD-SOURCE-003 | 来源删除：再次同步转为 `MISSING/INCOMPLETE`，保留已发布状态，记录旧分数到空值并发送变更通知 | `GradeRecordServiceTest.deletedSourceTaskBecomesMissingOnResyncAndKeepsPublishedChangeTrace` | 通过 |
| UT-GRD-SOURCE-004 | 来源部分缺失、评分变化、重复同步和发布幂等 | `GradeRecordServiceTest` 既有回归用例 | 通过 |
| CT-GRD-DOC-001 | 五个既有 UC 的需求 SSD、概要场景、详细顺序/活动/状态图及静态资源引用完整 | `frontend/tests/contracts/grd-doc-test-closure.contract.test.mjs` | 通过 |

## 8 测试执行日志

### 8.1 后端 GRD 执行日志

| 日志编号 | 时间 | 命令/测试类 | 执行内容 | 结果 |
| --- | --- | --- | --- | --- |
| GRD-LOG-001 | 2026-06-10 15:51 | `GradeItemMigrationTest` | GRD 迁移、成绩项/记录/总评/变更日志/统计快照持久化约束 | 5 条通过 |
| GRD-LOG-002 | 2026-06-10 15:51 | `GradeRecordControllerTest` | 来源同步、总表、调整、发布、学生查询、分析、完成情况、权限和异议 API | 13 条通过 |
| GRD-LOG-003 | 2026-06-10 15:51 | `GradeItemControllerTest` | 成绩项查询、创建、修改、停用、规则校验和权限错误 | 7 条通过 |
| GRD-LOG-004 | 2026-06-10 15:51 | `GradeItemServiceTest` | 成绩项业务规则、课程权限、权重上限、重复名称和来源编号校验 | 7 条通过 |
| GRD-LOG-005 | 2026-06-10 15:51 | `GradeReviewServiceTest` | 学生异议申请、重复申请拦截、教师同意复核和通知 | 3 条通过 |
| GRD-LOG-006 | 2026-06-10 15:51 | `GradeAnalysisServiceTest` | 课程总评分析、成绩项分析、完成情况、权限校验和统计快照 | 4 条通过 |
| GRD-LOG-007 | 2026-06-10 15:51 | `GradeRecordServiceTest` | 来源同步、总评计算、发布、幂等、发布后重算、变更通知、事务边界 | 10 条通过 |
| GRD-LOG-008 | 2026-06-10 15:51 | Maven 汇总 | `Tests run: 49, Failures: 0, Errors: 0, Skipped: 0` | 构建成功 |

### 8.2 前端 GRD 执行日志

| 日志编号 | 时间 | 命令/测试文件 | 执行内容 | 结果 |
| --- | --- | --- | --- | --- |
| GRD-LOG-009 | 2026-06-10 15:51 | `gradeItemsApi.spec.ts` | GRD 成绩项 API wrapper 路由、方法和认证上下文 | 2 条通过 |
| GRD-LOG-010 | 2026-06-10 15:51 | `gradeRecordsApi.spec.ts` | GRD 成绩同步、重算、表格、调整、发布、分析和异议 API wrapper | 6 条通过 |
| GRD-LOG-011 | 2026-06-10 15:51 | `StudentGradeView.spec.ts` | 学生已发布成绩、未发布提示、成绩异议申请状态 | 3 条通过 |
| GRD-LOG-012 | 2026-06-10 15:51 | `GradeItemConfigView.spec.ts` | 教师成绩项配置页创建、校验、更新和停用交互 | 4 条通过 |
| GRD-LOG-013 | 2026-06-10 15:51 | `TeacherGradeTableView.spec.ts` | 教师成绩总表、同步、分页、明细、调整、发布、分析、复核处理 | 9 条通过 |
| GRD-LOG-014 | 2026-06-10 15:51 | `App.spec.ts` | GRD 导航与路由、教师/学生成绩入口、课程上下文和权限状态 | 25 条通过 |
| GRD-LOG-015 | 2026-06-10 15:51 | Vitest 汇总 | `Test Files 6 passed (6)`、`Tests 49 passed (49)` | 构建成功 |

### 8.3 Issue #266 当前基线执行日志

基线：`dev@758afd98ba2caad5a00fb6e12413c48f0156b2fb`；执行分支：`test/266-grd-doc-test-closure`；日期：2026-08-25；环境：macOS、Java 25.0.1、Maven 3.9.11、Spring Boot 3.4.5、Node.js、Playwright 系统 Chrome、独立 H2 文件库。

| 日志编号 | 命令/范围 | 结果 |
| --- | --- | --- |
| GRD-266-LOG-001 | `GradeRecordServiceTest` | 13 passed / 0 failed / 0 errors / 0 skipped |
| GRD-266-LOG-002 | GRD Controller、Service、Migration、`GrdLrnIntegrationTest` 共 8 类 | 63 passed / 0 failed / 0 errors / 0 skipped；Maven BUILD SUCCESS |
| GRD-266-LOG-003 | 6 个 GRD Vitest 文件 | 6 files passed / 40 tests passed |
| GRD-266-LOG-004 | 文档/E2E 契约 | 5 passed / 0 failed |
| GRD-266-LOG-005 | disposable 包装入口 + 临时 H2 + 共享 Playwright runner + 系统 Chrome | 1 real API lifecycle passed / 0 failed；退出后后端和临时数据均清理 |
| GRD-266-LOG-006 | `npm run typecheck` | 通过 |
| GRD-266-LOG-007 | `npm run build` | Vite 生产构建通过，189 modules transformed |
| GRD-266-LOG-008 | `git diff --check` | 通过 |
| GRD-266-LOG-009 | disposable 失败清理自测：错误教师密码 + 端口 18081 | Playwright 按预期非零退出；包装脚本停止后端，端口无监听，临时 H2 目录无残留 |
| GRD-266-LOG-010 | PR #270 二轮契约回归 | 先扩展契约断言并确认 4 passed / 1 failed；修正 OTHER_COURSE_ITEM、同步失败和复核仓储依赖后 5 passed / 0 failed |
| GRD-266-LOG-011 | PR #270 三轮异常映射回归 | 先扩展契约断言并确认 4 passed / 1 failed；区分非成员 ERR-GRD-02/403 与成员未发布 ERR-GRD-04/400 后 5 passed / 0 failed |
| GRD-266-LOG-012 | PR #270 四轮隔离与依赖回归 | 先扩展契约断言并确认 3 passed / 2 failed；增加随机 proof 校验并修正教学分析/复核依赖后 5 passed / 0 failed |
| GRD-266-LOG-013 | PR #270 五轮来源语义与复核授权回归 | 先扩展契约断言并确认 4 passed / 1 failed；修正空来源语义、复核请求查询及权限拒绝顺序图后 5 passed / 0 failed |
| GRD-266-LOG-014 | PR #270 六轮异常响应与分析授权回归 | 先扩展契约断言并确认 4 passed / 1 failed；修正通用 HTTP 500 响应和教学分析权限终止路径后 5 passed / 0 failed |
| GRD-266-LOG-015 | PR #270 七轮环境隔离与通知时序回归 | 契约 RED 3 passed / 2 failed，且外部 `SPRING_PROFILES_ACTIVE=compose` 验收按预期失败；清空继承环境、固定 H2 配置并修正当时文档中的通知时序后契约 5 passed / 0 failed、同一外部环境下真实 E2E 1 passed / 0 failed；事务边界后续由 #283 / PR #284 独立收口 |
| GRD-266-LOG-016 | PR #270 十二轮凭据隔离与复核通知可见性回归 | 契约 RED 3 passed / 2 failed；固定 disposable 教师/学生种子凭据并将通知可见性改为持久化成功条件后 5 passed / 0 failed；外部四项账号/密码均为错误值时真实 H2 lifecycle 仍 1 passed / 0 failed，端口、进程和临时目录无残留 |
| GRD-266-LOG-017 | PR #270 十三轮同步异常契约回归 | 契约 RED 4 passed / 1 failed；移除 OP-GR-02 不会触发的规则错误分支，并同步详细设计接口、流程和 ERR-GRD-03 适用范围后 5 passed / 0 failed |
| GRD-266-LOG-018 | PR #270 十四轮分析快照边界回归 | 契约 RED 4 passed / 1 failed；按当前实现明确 GradeItem 规则不进入快照指纹，LAB/HWK 规则变更须执行来源同步，且同步自动重算课程总评并推进来源版本；修正文档和状态图后 5 passed / 0 failed |
| GRD-266-LOG-019 | PR #270 十五轮同步筛选与规则审计边界回归 | 契约 RED 4 passed / 1 failed；明确停用/不计入总评项不会被同步刷新、成绩项快照可复用，并记录 GradeItem 修改不检查发布状态、不接收原因或写规则变更日志；同时对齐 API-GRD-07 仅汇总现有 weightedScore 的真实行为后 5 passed / 0 failed |
| GRD-266-LOG-020 | PR #270 最新 `dev@570dd0c` 图号冲突回归 | 图组契约 RED 3 passed / 2 failed，确认 CRS 已占用图 4-38 ~ 4-55；合并最新 dev、保留 CRS 章节并将 GRD 四张 SSD 调整为图 4-56 ~ 4-59 后 GREEN 5 passed / 0 failed，六个相关 SVG 均通过 XML 解析 |
| GRD-266-LOG-021 | PR #270 通知反向一致性契约 | 通知契约 RED 4 passed / 1 failed，确认复核 SSD 仍描述为来源事务内持久化；改为来源事务回滚不生成通知、提交后 LRN 独立事务落库，并锁定 #283 / PR #284 独立交付边界后 GREEN 5 passed / 0 failed |
| GRD-266-LOG-022 | #283 / PR #284 LRN after-commit 生产修复 | `dev@570dd0c` 上的真实 Spring/H2 事务测试 RED 4 total / 2 passed / 2 failed，分别复现提交前通知可见和 `UnexpectedRollbackException`；PR #284 head `58d5a0e` GREEN 4 passed / 0 failed，相关回归 11 passed / 0 failed，全量后端 377 total / 372 passed / 0 failures / 0 errors / 5 skipped |
| GRD-266-LOG-023 | PR #270 返工版回归（2026-08-26） | macOS 26.6.2、Java 25.0.1、Maven 3.9.11、Spring Boot 3.4.5、Node 25.8.2、npm 11.11.1、Chrome 151；`origin/dev@570dd0c`。后端全量 377 total / 372 passed / 0 failures / 0 errors / 5 skipped，相关 GRD/LRN 23 passed / 0 failed；GRD Vitest 6 files / 41 passed；全部文档/E2E 契约 8 passed；disposable H2 真实 lifecycle 1 passed；类型检查、Vite 生产构建（189 modules）、四项 shell contract、SVG XML 与 `git diff --check` 通过 |

## 9 手工测试与联调确认

| 手测编号 | 模块 | 场景 | 操作要点 | 预期结果 | 当前结果 |
| --- | --- | --- | --- | --- | --- |
| MAN-GRD-001 | GRD/AUTH/CRS | 教师配置成绩项 | 浏览器登录教师账号，进入课程成绩项配置页，创建 LAB/HWK 或 OTHER_COURSE_ITEM 成绩项并校验权重 | 成绩项保存成功；OTHER_COURSE_ITEM 无需任务编号；非法权重、来源类型或 LAB/HWK 非正数编号提示明确；当前不存在或跨课程、未发布或暂无成绩的 LAB/HWK 来源均在同步后显示为 `MISSING`，不声称已确认任务存在性 | 待手工验收 |
| MAN-GRD-002 | GRD/LAB/HWK | 同步来源成绩并计算总评 | 准备真实实验/作业评分，教师触发同步和重算 | 成绩记录、加权分、缺失状态、总评与 LAB/HWK 来源一致 | 真实 API E2E 已通过 |
| MAN-GRD-003 | GRD | 教师成绩总表与学生明细 | 教师筛选分页查看成绩总表，打开学生明细，查看来源任务和状态 | 总表分页、筛选、明细、缺失状态和来源信息正确 | 待手工验收 |
| MAN-GRD-004 | GRD/LRN | 成绩发布与学生可见 | 教师发布成绩，学生刷新个人成绩页，通知中心查看成绩发布通知 | 发布记录保存，学生只能看到本人已发布成绩，LRN 通知可见 | API 与通知查询 E2E 已通过；页面视觉待验收 |
| MAN-GRD-005 | GRD | 成绩调整与变更记录 | 教师对已发布单项成绩或总评进行带原因调整 | 分数更新，变更记录显示旧值、新值、原因、操作人和时间 | 待手工验收 |
| MAN-GRD-006 | GRD | 教学分析 | 教师查看课程总评分析和单项成绩完成情况 | 均分、最高分、最低分、及格率、完成率、分布和来源时间点正确 | 待手工验收 |
| MAN-GRD-007 | GRD/LRN | 成绩异议复核 | 学生对已发布成绩提交异议，教师处理同意或驳回，学生查看结果 | 申请状态流转正确，重复申请被拦截，处理结果通知可见 | API 与通知查询 E2E 已通过；页面视觉待验收 |
| MAN-GRD-008 | GRD/AUTH/CRS | 权限边界 | 非课程教师、非成员学生、学生访问教师接口、教师访问学生个人接口 | 页面提示权限不足，接口返回受控错误，不泄露成绩数据 | API 401/403 和本人过滤 E2E 已通过；页面提示待验收 |
| MAN-GRD-009 | GRD | 页面状态 | 制造加载中、空成绩项、无成绩记录、接口失败、会话过期 | 页面有清晰提示，按钮禁用或引导正确 | 待手工验收 |
| MAN-GRD-010 | GRD | 基础性能 | 准备大批量学生、成绩项和成绩记录，查询总表、个人成绩和分析 | 分页正常，响应时间满足测试负责人设定阈值 | 待专项测试 |

## 10 缺陷、风险与处理建议

| 风险编号 | 风险说明 | 影响范围 | 建议处理 |
| --- | --- | --- | --- |
| R-GRD-001 | 真实 API 业务闭环已通过，但 UI-GRD-01 ~ UI-GRD-10 的视觉、加载/空/失败状态未逐页留存截图 | UI-GRD-01 ~ UI-GRD-10 | 测试负责人按 MAN-GRD-001、003、005、006、008、009 补跑页面视觉验收 |
| R-GRD-004 | 生产规模性能压测未执行 | NFR-GR-02 | 准备大班课程、多个成绩项和批量成绩记录，补充总表、个人成绩和分析接口响应时间 |
| R-GRD-005 | 前端测试运行存在 Node `--localstorage-file` 警告 | 本地验证流程 | 当前不影响断言结果；如测试负责人要求无警告日志，可后续统一调整 Vitest/Node 启动参数 |

## 11 验收结论

| 验收项 | 结论 | 说明 |
| --- | --- | --- |
| 功能覆盖 | 通过 | FR-GR-01 ~ FR-GR-07 均有自动化覆盖，真实 LAB/HWK → GRD → LRN API 闭环已通过 |
| 接口覆盖 | 通过 | API-GRD-01 ~ API-GRD-21 的主路由、权限、错误分支和响应结构由后端/前端自动化覆盖 |
| 页面覆盖 | 有条件通过 | Vue 单测覆盖主要页面状态和交互，共享 Playwright 覆盖真实 API 闭环；视觉验收待手工确认 |
| 数据一致性 | 通过 | DB-GRD-01 ~ DB-GRD-08 的关键持久化、状态、日志和快照由迁移/服务测试覆盖 |
| 权限与安全 | 通过 | 教师课程权限、学生本人过滤、未发布不可见、无权限复核等分支均有自动化覆盖 |
| 非功能 | 有条件通过 | 可靠性、可追踪性、安全性、可测试性及跨模块联调已覆盖；生产规模性能待补充 |
| 最终结论 | 有条件通过 | Issue #266 要求的业务场景文档、异常边界和真实 API 测试闭环已完成；剩余项仅为页面视觉验收和生产规模性能专项，不阻塞本 issue 的文档/测试交付 |

## 12 附录

### 12.1 执行命令

```bash
cd /Users/xigma/Library/CloudStorage/OneDrive-个人/github/OnlineJudge/backend
mvn test -Dtest=GradeItemControllerTest,GradeRecordControllerTest,GradeItemMigrationTest,GradeAnalysisServiceTest,GradeItemServiceTest,GradeRecordServiceTest,GradeReviewServiceTest,GrdLrnIntegrationTest

cd /Users/xigma/Library/CloudStorage/OneDrive-个人/github/OnlineJudge/frontend
node node_modules/vitest/vitest.mjs run tests/unit/grd/gradeItemsApi.spec.ts tests/unit/grd/gradeRecordsApi.spec.ts tests/unit/grd/GradeItemConfigView.spec.ts tests/unit/grd/TeacherGradeTableView.spec.ts tests/unit/grd/App.spec.ts src/views/grd/StudentGradeView.spec.ts --pool=threads
node --test tests/contracts/grd-doc-test-closure.contract.test.mjs
E2E_BROWSER_CHANNEL=chrome npm run test:e2e:grd:disposable
```

### 12.2 本次执行摘要

| 项目 | 摘要 |
| --- | --- |
| 后端 GRD 自动化测试 | 8 个测试类，63 passed / 0 failed / 0 errors / 0 skipped |
| 前端 GRD 自动化测试 | 6 files passed / 40 tests passed |
| 文档/E2E 契约 | 5 passed / 0 failed |
| 真实跨模块 E2E | 1 passed / 0 failed；LAB/HWK → GRD → LRN 主成功、备选、异常、权限和幂等边界 |
| 自动化覆盖 | 成绩项、规则校验、来源同步/超时/删除/缺失、总评计算、成绩调整、完整/部分发布、学生查询、教学分析、异议复核、权限、日志、通知、快照 |
| 手工/专项状态 | 真实 API 联调已完成；待补 UI 视觉验收和生产规模性能记录 |
