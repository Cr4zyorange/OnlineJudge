# TST-DOC-08 测试文档（完整协作底稿）

| 文档编号 | TST-DOC-08 |
| --- | --- |
| 文档名称 | 测试文档（完整协作底稿） |
| 项目名称 | 在线教学与实训平台 |
| 所属阶段 | 系统测试与验收测试 |
| 报告版本 | V1.0 |
| 编写日期 | 2026-06-11 |
| 编写人 | AUTH、CRS、LRN、LAB、HWK、GRD 模块负责人 / 测试负责人 |
| 测试范围 | AUTH、CRS、LRN、LAB、HWK、GRD |
| 测试日志范围 | 记录 AUTH、CRS、LRN、LAB、HWK、GRD 六个模块执行日志 |
| 测试结论 | 有条件通过；后端/前端自动化、本地真实服务闭环探针、本地安全探针、Docker Python 基础专项和本地 DEV 浏览器手工矩阵通过，生产级安全扫描、生产实流量长稳和最终人工审批仍需验收环境确认 |

## 1 文档控制

### 1.1 修订记录

| 版本 | 日期 | 修订人 | 修订说明 |
| --- | --- | --- | --- |
| V1.0 | 2026-06-11 | 各模块负责人 / 测试负责人 | 依据 TST-DOC-01 规范和 CRS 编号范例整合 AUTH、CRS、LRN、LAB、HWK、GRD 测试数据、用例、执行日志、手工验收、风险、验收结论和附录 |

### 1.2 审批记录

| 角色 | 姓名/负责人 | 审批意见 | 日期 |
| --- | --- | --- | --- |
| 项目负责人 | 待填写 | 待审批 | 2026-06-11 |
| 测试负责人 | @MontesquieuE | 有条件通过，待最终验收环境确认 | 2026-06-11 |
| 各模块负责人 | AUTH、CRS、LRN、LAB、HWK、GRD 负责人 | 模块自动化材料通过，待最终验收确认 | 2026-06-11 |

## 2 测试概述

本报告用于记录在线教学与实训平台在当前版本下的测试范围、测试依据、测试环境、测试用例覆盖、各模块执行日志和验收结论。报告结构按照模板中的文档控制、范围说明、环境说明、用例表、执行记录、缺陷风险和验收结论组织。

本次自动化验证已覆盖后端 Spring Boot 单元/集成测试、前端 Vue/Vitest 单元测试，以及各模块的接口、权限、数据一致性、前后端调用链路和基础非功能场景。2026-06-12 已补跑后端 `mvn test` 和前端 `npm run test:unit` 完整自动化测试，最新复跑结果为后端 252 条测试执行、0 失败、0 错误、1 跳过，前端 33 个测试文件、186 条测试通过；同日补跑本地性能/压力相关后端批次，154 条测试执行、0 失败、0 错误、0 跳过；Docker daemon 启动后补跑真实 Docker 沙箱专项样本，执行器 smoke、AC、编译错误、运行错误、超时清理、内存限制和 4 并发样本通过。当前各模块自动化测试与文档材料已完成整合；CRS 已补充本地 DEV 手工联调记录，本地可执行压测、真实服务闭环探针、本地安全探针、真实 Docker 沙箱基础专项和本地 DEV 浏览器手工矩阵已补充。本地 DEV 浏览器矩阵覆盖注册/资料/管理员、学生学习任务/通知/提醒、教师实验/作业/成绩、学生提交、教师评分批阅、成绩同步、成绩异议复核等链路。生产级安全扫描、生产实流量长稳验证和最终人工审批仍需最终验收环境确认。

## 3 测试依据

| 序号 | 文档/代码依据 | 用途 |
| --- | --- | --- |
| 1 | `docs/最终提交/软件需求规格说明书.md` | 功能需求、非功能需求、验收标准来源 |
| 2 | `docs/最终提交/软件概要设计说明书.md` | 系统架构、模块边界、跨模块协作依据 |
| 3 | `docs/最终提交/软件详细设计说明书.md` | 页面、接口、数据表、异常、安全和测试编号来源 |
| 4 | `docs/过程/需求/` | 各模块需求补充和验收场景 |
| 5 | `docs/过程/概要/` | 各模块概要设计和交互边界 |
| 6 | `docs/过程/详细设计/` | 各模块详细设计、API、数据表、流程和状态机 |
| 7 | `backend/src/test/java` | 后端自动化测试实现 |
| 8 | `frontend/tests/unit`、`frontend/src/views/grd/StudentGradeView.spec.ts` | 前端自动化测试实现 |

## 4 测试范围

### 4.1 总体范围

| 模块 | 功能范围 | 主要验证点 | 当前覆盖状态 |
| --- | --- | --- | --- |
| AUTH 用户权限与平台安全 | 登录注册、角色权限、会话、审计、账号状态 | Bearer 鉴权、权限拦截、账号异常、管理员操作 | 自动化已覆盖核心 API 与前端状态 |
| CRS 课程与教学资源 | 课程、选课、章节、资源、成员、公告、课程首页摘要 | 接口、权限、成员状态、文件约束、公告置顶、课程归属 | 自动化已执行，日志见第 8 章 |
| LRN 学习过程与通知提醒 | 学习任务、进度、记录、统计、通知、提醒规则 | API 封装、前端视图、提醒规则与通知状态 | 自动化已覆盖核心单元场景 |
| LAB 实训实验 | 实验发布、提交、评测、报告、评分、统计 | 教师/学生视图、提交历史、后端服务与控制器 | 自动化已覆盖核心单元场景 |
| HWK 作业与自动评测 | 作业发布、学生提交、批阅、提交历史、自动评测 | 教师/学生视图、API、后端控制器与服务 | 自动化已覆盖核心单元场景 |
| GRD 成绩评价与教学分析 | 成绩项、成绩同步、成绩表、发布、复核、分析 | 成绩配置、成绩表、学生成绩、后端服务与控制器 | 自动化已覆盖核心单元场景 |

### 4.2 不在本次自动化范围

| 范围项 | 说明 | 处理方式 |
| --- | --- | --- |
| 完整验收环境浏览器端到端 | 已执行本地 DEV 真实服务闭环探针和浏览器手工矩阵；FAT/UAT 环境仍需按验收账号补截图或测试记录 | 最终验收补充 |
| 真实文件存储目录清理 | CRS 后端 MockMvc 覆盖了资源上传下载和限制，本地 DEV 已抽查资源上传下载；生产文件目录清理仍需环境抽查 | 最终验收抽查 |
| 多用户并发选课/审批 | 自动化覆盖重复加入、满员、待审批、拒绝后重申等单请求链路，本地性能批次已覆盖基础规模；生产并发仍需验收环境复核 | 最终验收复核 |
| 生产级安全扫描 | 已覆盖未登录、越权、文件类型、异常映射和本地安全探针；未附 OWASP ZAP 或等效工具报告 | 专项验收补充 |

## 5 测试环境

| 环境项 | 内容 |
| --- | --- |
| 操作系统 | Windows |
| 后端运行环境 | Java 25，Spring Boot 3.4.5，Maven，JUnit 5，MockMvc，H2 |
| 前端运行环境 | Node.js，npm，Vue 3.5，Vite 6.3，Vitest 3.2，jsdom |
| 测试数据库 | 后端测试使用 H2 内存库，集成测试分别使用 `onlinejudge`、`auth_crs_integration` 等测试库 |
| 鉴权方式 | 测试中使用 `X-User-Id`、`X-User-Role` 模拟用户上下文；前端 HTTP 客户端使用 Bearer Token |
| 执行日期 | 2026-06-08 至 2026-06-12 |

## 6 测试数据

### 6.1 AUTH 测试数据

| 数据类别 | 数据说明 | 使用模块 |
| --- | --- | --- |
| 学生用户 | 公开注册生成的 `STUDENT` 用户，含用户名、邮箱、手机号、显示名称和密码 | AUTH、CRS |
| 教师用户 | 种子或测试创建的 `TEACHER` 用户，用于课程创建和教师边界验证 | AUTH、CRS |
| 管理员用户 | 具备用户、角色、权限、审计日志管理权限的 `ADMIN` 用户 | AUTH |
| 账号状态数据 | `ACTIVE`、`FROZEN`、`DISABLED`，以及连续登录失败后的锁定截止时间 | AUTH |
| 角色权限数据 | `STUDENT`、`TEACHER`、`ADMIN` 角色；菜单、接口、资源和操作权限点 | AUTH |
| 会话数据 | 有效、过期、已撤销会话；令牌摘要而非完整 Bearer Token | AUTH |
| 审计日志数据 | 登录成功/失败、越权访问、角色调整、权限调整、账号禁用、密码修改 | AUTH |
| 跨模块数据 | 课程成员、课程创建、非成员访问拒绝和 Header 当前用户上下文 | AUTH、CRS |

### 6.2 CRS 测试数据

| 数据类别   | 数据说明                                                     | 使用模块 |
| ---------- | ------------------------------------------------------------ | -------- |
| 教师用户   | `101`、`301`、`351`、`901`、`1001` 等测试教师编号            | CRS      |
| 学生用户   | `201`、`302`、`352`、`902`、`1002` 等测试学生编号            | CRS      |
| 课程数据   | 公开课、邀请码课程、审批制课程、归档课程、满员课程、性能规模课程 | CRS      |
| 章节数据   | 多级章节、同级排序、删除与读取场景                           | CRS      |
| 资源数据   | PDF 文档、无章节资源、超大文件、伪装可执行文件、不支持类型文件 | CRS      |
| 成员数据   | ACTIVE、PENDING、REJECTED、REMOVED 状态，TEACHER、ASSISTANT、STUDENT 角色 | CRS      |
| 公告数据   | 普通公告、置顶公告、超长公告、含脚本内容公告                 | CRS      |
| 跨模块数据 | `lrn_learning_task` 插入的课程首页近期任务                   | CRS/LRN  |

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

### 6.4 LAB 测试数据

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

### 6.5 HWK 测试数据

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

### 6.6 GRD 测试数据

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


## 7 测试用例汇总

### 7.1 AUTH 测试用例汇总

#### 7.1.1 自动化执行结果

| 测试类别 | 命令 | 执行结果 |
| --- | --- | --- |
| 后端 AUTH 相关测试 | `$env:JAVA_HOME='C:\Program Files\Java\jdk-25'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; & 'C:\Code\SE\.codex-tools\apache-maven-3.9.9\bin\mvn.cmd' "-Dtest=AuthControllerTest,AuthAdminControllerTest,AuthMigrationScriptTest,HeaderCurrentUserProviderTest,AuthCrsIntegrationTest" test` | 5 个测试类，34 条通过，0 失败，0 错误，0 跳过 |
| 前端 AUTH/API 单元测试 | `npm run test:unit -- tests/unit/auth/AuthView.spec.ts tests/unit/auth/AuthProfileView.spec.ts tests/unit/auth/AuthAdminView.spec.ts tests/unit/auth/authApi.spec.ts tests/unit/api/http.spec.ts --pool=threads` | 5 个测试文件，22 条通过 |

#### 7.1.2 AUTH 核心用例表

| 用例编号 | 对应需求 | 覆盖对象 | 前置条件/测试数据 | 操作步骤 | 预期结果 | 实际结果 | 通过状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TC-UA-01 | FR-UA-01 | UI-AUTH-01、02；API-AUTH-01、02、03、04；DB-AUTH-01、06、07 | 未注册学生账号；正常账号密码；Bearer 会话 | 注册学生，登录，读取 `/me`，退出后再次访问需认证接口 | 注册成功；登录返回 token、用户、角色、权限；退出撤销 token | `userRegistersLogsInReadsCurrentUserAndLogoutRevokesToken`、前端登录/注册和 `authApi.spec.ts` 通过 | 通过 |
| TC-UA-02 | FR-UA-02 | UI-AUTH-05、06、07、08；API-AUTH-08 ~ 15；DB-AUTH-02 ~ 05、07 | 管理员账号；学生账号；角色和权限点 | 管理员查询用户、创建用户、调整用户角色、创建/更新角色、调整角色权限；学生访问管理接口 | 管理员操作成功并记录审计；普通学生被拒绝 | `adminAssignsUserRolesAndRolePermissionsWithAuditLogs`、`studentCannotAccessRoleManagementApi`、`studentCannotChangeRolePermissions`、前端管理页用例通过 | 通过 |
| TC-UA-03 | FR-UA-03 | UI-AUTH-10、11；API-AUTH-04、16；SVC-AUTH-08；DB-AUTH-02 ~ 06 | 有效 Bearer Token；仅伪造 Header 的请求；AUTH/CRS 测试课程 | 使用 Bearer 调用 `/me` 和权限校验；使用 header-only 身份访问业务接口；教师创建课程、学生加入、非成员访问 | Bearer 当前用户生效；header-only 被拒绝；业务模块基于 AUTH 用户上下文继续做 CRS 成员校验 | `currentUserRequiresBearerSessionInsteadOfHeaderOnlyIdentity`、`businessApiRejectsHeaderOnlyIdentityWhenSessionTokenIsMissing`、`AuthCrsIntegrationTest` 通过 | 通过 |
| TC-UA-04 | FR-UA-04 | UI-AUTH-03、04；API-AUTH-05、06、07；DB-AUTH-01、06、07 | 已登录用户；有效原密码；非法联系方式；重复错误密码 | 读取/修改资料；提交非法资料；修改密码；使用旧会话和旧密码；多次失败登录 | 资料不含敏感字段；非法资料拒绝；密码重新哈希；旧会话失效；失败次数触发锁定 | `currentUserProfileCanBeReadAndUpdatedWithoutSensitiveFields`、`profileUpdateRejectsInvalidContactAndDisplayNameBeforeSaving`、`passwordChangeRequiresOldPasswordRehashesAndRevokesExistingSessions`、`repeatedLoginFailuresSetTemporaryLockUntilTimestamp` 通过 | 通过 |
| TC-UA-05 | FR-UA-05 | UI-AUTH-10、11；API-AUTH-01、04、16；DB-AUTH-01、06、07；ERR-AUTH-01 ~ 06 | 错误密码、过期/撤销/伪造 token、禁用账号、无权限用户 | 分别访问登录、`/me`、业务接口、权限校验和管理接口 | 返回统一安全错误；前端跳转登录失效、账号异常或 403 页面；不泄露内部细节 | 后端异常矩阵、`http.spec.ts` 401/403/账号异常跳转用例通过 | 通过 |
| TC-UA-06 | FR-UA-06 | UI-AUTH-09；API-AUTH-17；DB-AUTH-07 | 已有登录、越权、角色调整、账号状态变更等安全事件 | 管理员按操作人、类型、时间和结果查询审计日志 | 查询结果分页返回；日志包含时间、操作者、类型、对象、结果和失败原因，不含明文密码/完整令牌 | `adminQueriesAuditLogsWithOperationResultOperatorAndTimeFilters`、`loginAuditBoundsClientIpAndUserAgentToColumnLimits`、`adminCreateUserDoesNotExposePasswordInResponseOrAuditLogs` 通过 | 通过 |
| TC-UA-07 | FR-UA-07 | 全部 AUTH 页面和接口；DB-AUTH-01 ~ 07；SVC-AUTH-06、08 | 非法 JSON、空权限码、伪造令牌、重复邮箱/手机号、参数篡改 | 提交非法输入和伪造认证信息；尝试通过参数或 header 绕过权限 | 返回受控错误；不暴露账号存在性、堆栈、密码、令牌；后端不信任前端身份 | `malformedAuthJsonReturnsSafeValidationErrorWithoutInternalDetails`、`forgedBearerTokenUsesSafeAuthenticationFailureMessage`、`registrationRejectsDuplicateEmailAndPhoneUsedForLogin` 等通过 | 通过 |
| TC-UA-N01 | NFR-UA-01 | 密码、令牌、接口鉴权、敏感响应 | 已登录用户和管理员操作 | 检查会话表、接口响应、审计日志和前端请求头 | 密码不明文；token 仅摘要入库；Bearer 注入；无用户自控 header 鉴权 | 后端 token 摘要测试和前端 `http.spec.ts` Bearer 用例通过 | 通过 |
| TC-UA-N02 | NFR-UA-02 | 关键安全流程可靠性 | 会话、账号状态、角色权限、审计日志 | 执行登录、退出、禁用账号、修改密码、角色权限调整 | 主数据、会话和日志状态一致；失败返回受控响应 | 后端 34 条目标测试通过 | 通过 |
| TC-UA-N03 | NFR-UA-03 | 页面提示和状态 | 登录/注册、资料/密码、管理页、账号异常、403、会话过期 | 触发页面成功、失败和异常状态 | 用户看到清晰反馈，不展示无权限管理入口 | 前端 22 条目标单测通过； | 通过 |
| TC-UA-N04 | NFR-UA-04 | 高频接口和分页 | 用户列表、审计日志、权限查询、索引 | 查询分页列表和权限校验；检查迁移索引 | 分页参数可用，关键字段有索引，响应受控 | API 分页与迁移索引已覆盖；2026-06-12 本地压测批次通过，生产实流量长稳可复核 | 通过 |
| TC-UA-N05 | NFR-UA-05 | 可测试性 | 稳定种子数据、H2、MockMvc、Vitest、jsdom | 重复执行后端和前端目标测试 | 核心安全场景可重复验证 | 本文第 8 章命令已通过 | 通过 |

#### 7.1.3 前端 AUTH 用例摘要

| 测试文件 | 覆盖内容 | 结果 |
| --- | --- | --- |
| `frontend/tests/unit/auth/authApi.spec.ts` | `API-AUTH-01 ~ 17` 路由、方法、参数、Bearer 调用、登录态写入、审计日志筛选、权限校验、资料和密码接口 | 7 条通过 |
| `frontend/tests/unit/auth/AuthView.spec.ts` | 登录成功、角色入口展示、注册模式、后端校验失败反馈 | 2 条通过 |
| `frontend/tests/unit/auth/AuthProfileView.spec.ts` | 当前用户资料加载、资料修改、密码修改、密码确认前端校验 | 2 条通过 |
| `frontend/tests/unit/auth/AuthAdminView.spec.ts` | 管理员用户角色、角色权限和页面状态渲染 | 1 条通过 |
| `frontend/tests/unit/api/http.spec.ts` | Bearer Token 注入、禁止用户自控 header 鉴权、multipart/binary、401 登录失效、403 无权限、账号禁用/锁定跳转 | 10 条通过 |

### 7.2 CRS 测试用例汇总

#### 7.2.1 CRS总体自动化结果

| 测试类别          | 命令                                                         | 执行结果                          |
| ----------------- | ------------------------------------------------------------ | --------------------------------- |
| 后端 CRS 相关测试 | `mvn "-Dtest=CourseControllerTest,AuthCrsIntegrationTest,HeaderCoursePermissionClientTest" test` | 31 条通过，0 失败，0 错误，0 跳过 |
| 前端单元测试      | `npm run test:unit`                                          | 32 个测试文件通过，170 条测试通过 |

#### 7.2.2 CRS 用例表

| 用例编号  | 对应需求  | 用例名称                                             | 覆盖对象                                                     | 优先级 | 自动化状态 |
| --------- | --------- | ---------------------------------------------------- | ------------------------------------------------------------ | ------ | ---------- |
| TC-CR-01  | FR-CR-01  | 教师创建课程并自动成为课程教师                       | `API-CRS-01`、`API-CRS-19`、`DB-CRS-01`、`DB-CRS-04`         | 高     | 通过       |
| TC-CR-02  | FR-CR-02  | 学生加入公开课、邀请码课程、审批制课程               | `API-CRS-14`、成员状态机、`DB-CRS-04`                        | 高     | 通过       |
| TC-CR-03  | FR-CR-03  | 教师管理多级章节，学生只读章节树                     | `API-CRS-06`、`API-CRS-07`、`API-CRS-08`、`API-CRS-09`、`DB-CRS-02` | 中     | 通过       |
| TC-CR-04  | FR-CR-04  | 教师上传、更新、删除资源，成员下载资源               | `API-CRS-10`、`API-CRS-11`、`API-CRS-12`、`API-CRS-13`、资源下载接口、`DB-CRS-03` | 中     | 通过       |
| TC-CR-05  | FR-CR-05  | 课程成员列表、学生名单、角色调整、移除成员、权限校验 | `API-CRS-15`、`API-CRS-16`、`API-CRS-17`、`API-CRS-18`、`API-CRS-19`、`DB-CRS-04` | 高     | 通过       |
| TC-CR-06  | FR-CR-06  | 教师发布、编辑、置顶、删除公告，成员查看置顶优先     | `API-CRS-20`、`API-CRS-21`、`API-CRS-22`、`DB-CRS-05`、LRN 近期任务 | 中     | 通过       |
| TC-CR-N01 | NFR-CR-01 | 未登录、越权、移除成员访问资源被拒绝                 | 安全与权限控制                                               | 高     | 通过       |
| TC-CR-N02 | NFR-CR-02 | 课程列表分页参数归一化                               | 分页与稳定性                                                 | 中     | 通过       |
| TC-CR-N03 | NFR-CR-03 | 105 门课程、105 个资源基础规模列表响应               | 基础性能样本                                                 | 中     | 通过       |
| TC-CR-N04 | NFR-CR-04 | 伪装可执行文件、超大文件、不支持类型文件拒绝上传     | 文件安全与失败可见性                                         | 高     | 通过       |
| TC-CR-N05 | NFR-CR-05 | 404、400、409、公告超长等异常映射                    | 异常处理与可追踪性                                           | 中     | 通过       |

#### 7.2.3 前端 CRS 用例摘要

| 文件                                               | 覆盖内容                                                     | 结果      |
| -------------------------------------------------- | ------------------------------------------------------------ | --------- |
| `frontend/tests/unit/CourseManagementView.spec.ts` | 课程列表、详情弹窗、教师管理入口、公告发布、我的课程、课程创建、章节管理、章节拖拽、资源下载、邀请码选课、审批制选课、成员审批/拒绝、角色调整、移除成员、最后教师限制提示 | 18 条通过 |
| `frontend/tests/unit/app/courseMain.spec.ts`       | `/courses` 入口挂载共享路由                                  | 1 条通过  |
| `frontend/tests/unit/api/http.spec.ts`             | Bearer 鉴权、multipart、二进制下载、401/403/账号异常处理     | 10 条通过 |

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
| TC-LN-N01 | NFR-LN-01 | 全部 LRN 页面 | 全部 LRN 接口 | DB-LRN-01 ~ DB-LRN-07 | 登录态、课程权限、事件接口鉴权、用户数据隔离 | 已覆盖 |
| TC-LN-N02 | NFR-LN-02 | UI-LRN-04 | API-LRN-09 | DB-LRN-04、DB-LRN-05 | 通知幂等、不重复生成、失败可记录 | 已覆盖 |
| TC-LN-N03 | NFR-LN-03 | UI-LRN-01 ~ UI-LRN-05 | API-LRN-01 ~ API-LRN-11 | 全部 LRN 表 | 空态、失败态、重试、主流程入口可用 | 已覆盖 |
| TC-LN-N04 | NFR-LN-04 | UI-LRN-01、UI-LRN-04 | API-LRN-01、API-LRN-06 | DB-LRN-01、DB-LRN-04 | 分页上限、基础规模、列表响应 | 已覆盖；2026-06-12 本地压测批次通过 |
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
| TC-LN-02-07 | FR-LN-02 | CRS/LAB/HWK 页面继续学习恢复断点 | 已存在 `lastPosition` | CRS `chapterId`、LAB/HWK `resume` 查询参数 | 从学习进度页点击继续学习 | 目标页面按断点恢复章节、代码或作答上下文 | 前端单元测试通过；2026-06-12 本地 DEV 浏览器点击继续学习并显示断点恢复 | 通过 | `CourseManagementView.spec.ts`、`LabStudentView.spec.ts`、`HomeworkStudentView.spec.ts` |
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
| TC-LN-05-03 | FR-LN-05 | 通知业务跳转 | 用户已登录且通知含 `actionUrl` | 指向任务、成绩、公告或系统页面的通知 | 在通知中心点击查看/跳转 | 跳转到对应业务页面，通知内容不丢失 | 前端组件测试覆盖链接渲染；2026-06-12 本地 DEV 浏览器点击通知详情后跳转课程成绩相关页面 | 通过 | `NotificationCenterView.spec.ts` |
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

### 7.4 LAB 测试用例汇总

#### 7.4.1 自动化执行结果

| 测试类别 | 命令 | 执行结果 |
| --- | --- | --- |
| 后端 LAB 相关测试 | `mvn test "-Dtest=LabExperimentControllerTest,LabSubmissionControllerTest,LabExperimentMigrationTest,LabExperimentTransactionTest,LabEvaluationServiceTest"` | 47 条通过，0 失败，0 错误，0 跳过 |
| 前端 LAB 单元测试 | `npm run test:unit -- tests/unit/api/labs.spec.ts tests/unit/lab/LabTeacherView.spec.ts tests/unit/lab/LabStudentView.spec.ts tests/unit/lab/LabSubmissionHistoryView.spec.ts` | 4 个测试文件通过，29 条测试通过 |

#### 7.4.2 LAB 核心用例表

| 用例编号 | 对应需求 | 覆盖对象 | 前置条件/测试数据 | 操作步骤 | 预期结果 | 实际结果 | 通过状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TC-LAB-01 | FR-LAB-01 | `UI-LAB-01`、`UI-LAB-04`、`API-LAB-01/02/03`、`DB-LAB-01/02` | 教师具备课程管理权限；准备标题、截止时间、语言、测试用例 | 创建实验草稿，读取列表和详情 | 返回实验 ID，状态为 `DRAFT`，测试用例随实验保存 | `teacherCreatesListsAndReadsLabThroughDocumentedApis`、教师端创建草稿用例覆盖 | 自动化覆盖 | 通过 |
| TC-LAB-02 | FR-LAB-01 | `API-LAB-01`、异常码 `LAB-400-01/LAB-400-02` | 标题为空、截止时间非法、满分非法 | 提交非法创建请求 | 返回 400，页面保留输入并提示错误 | `controllerRejectsInvalidPayloadAndPermissionViolations`、教师端非法表单拦截用例覆盖 | 自动化覆盖 | 通过 |
| TC-LAB-03 | FR-LAB-01 | `API-LAB-04/06/07`、`UI-LAB-04` | 已创建草稿实验 | 更新实验，发布实验，截止实验 | 草稿可更新；发布后状态为 `PUBLISHED`；截止后状态为 `CLOSED` | `teacherUpdatesPublishesClosesAndDeletesDraftLab`、教师端更新/发布/截止用例覆盖 | 自动化覆盖 | 通过 |
| TC-LAB-04 | FR-LAB-01 | `API-LAB-06`、LRN 事件 | 已创建草稿实验，存在课程学生列表 | 教师发布实验 | 发送 `LAB_EXPERIMENT_PUBLISHED` 通知事件，学生可见 | `teacherUpdatesPublishesClosesAndDeletesDraftLab`、`studentCourseMemberCanReadPublishedLabsButCannotSeeHiddenExpectedOutput` | 自动化覆盖 | 通过 |
| TC-LAB-05 | FR-LAB-01 | `API-LAB-05`、`UI-LAB-04` | 存在草稿实验 | 删除草稿实验 | 删除成功，草稿不再出现在教师列表 | `teacherUpdatesPublishesClosesAndDeletesDraftLab`、教师端删除草稿用例覆盖 | 自动化覆盖 | 通过 |
| TC-LAB-06 | FR-LAB-01 | `API-LAB-14`、`UI-LAB-08` | 存在已发布实验和课程学生 | 查询实验统计 | 返回提交率、未提交名单、评测完成率、平均分和分数分布 | `teacherQueriesLabStatisticsWithUnsubmittedStudentsScoreDistributionAndLateCount`、教师端统计面板用例覆盖 | 自动化覆盖 | 通过 |
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
| TC-LAB-N01 | NFR-LAB-01 | 提交、评测、评分主链路 | 依次执行提交、评测异常、评分、成绩发布 | 提交先落库，异常不丢记录，成绩发布可重复执行 | `studentCanSubmitCodeTwiceAndVersionIncrements`、`evaluatorExceptionMarksSubmissionAsSystemErrorAndPreservesEvaluationRecord`、`teacherCanReleaseScoresAfterClosingLab` | 自动化覆盖 | 通过 |
| TC-LAB-N02 | NFR-LAB-02 | 提交受理、异步评测、统计查询 | 观察提交初始状态、异步轮询、统计结果生成 | 提交快速返回 `PENDING/RUNNING`；统计接口返回结构稳定 | `loads published lab detail and submits code successfully`、`autoEvaluateSubmissionEventuallyReturnsAcceptedAndHidesHiddenCaseFromStudent`、`teacherQueriesLabStatisticsWithUnsubmittedStudentsScoreDistributionAndLateCount` | 自动化覆盖 | 通过 |
| TC-LAB-N03 | NFR-LAB-03 | 提交、评测、报告、评分、变更留痕 | 完整执行提交、报告、评分、改分、发布成绩 | 所有关键动作均有版本或日志留痕 | `studentCanUploadReportTwiceAndTeacherCanViewLatestReportFromSubmissionDetail`、`teacherCanScoreSubmissionAndPersistScoreRecord`、`updatingSubmissionScoreRequiresReasonAndPersistsChangeLog`、迁移测试相关用例覆盖 | 自动化覆盖 | 通过 |
| TC-LAB-N04 | NFR-LAB-04 | 课程权限、本人数据、隐藏用例、越权拒绝 | 学生访问教师接口、非成员访问实验、学生查看他人提交/结果 | 返回 403，隐藏测试用例对学生不可见 | `controllerRejectsInvalidPayloadAndPermissionViolations`、`nonCourseMemberCannotSubmitPublishedLab`、`studentCannotViewAnotherStudentsSubmissionDetail`、`studentCannotQueryAnotherStudentsLabResult` | 自动化覆盖 | 通过 |
| TC-LAB-N05 | NFR-LAB-05 | 可重复验证、事务和迁移 | 运行目标自动化测试 | 迁移、事务、控制器、前端页面可重复执行 | `LabExperimentMigrationTest`、`LabExperimentTransactionTest`、LAB 前端单测覆盖 | 自动化覆盖 | 通过 |

#### 7.4.3 前端 LAB 用例摘要

| 测试文件 | 覆盖内容 | 结果 |
| --- | --- | --- |
| `frontend/tests/unit/api/labs.spec.ts` | 报告下载、报告评分、实验统计 API wrapper | 3 条通过 |
| `frontend/tests/unit/lab/LabTeacherView.spec.ts` | 教师创建/编辑/发布/截止/发布成绩/删除草稿、统计、提交筛选、报告下载、报告评分、提交评分 | 11 条通过 |
| `frontend/tests/unit/lab/LabStudentView.spec.ts` | 学生详情、提交、断点恢复、前端校验、失败提示、评测结果、报告上传下载、成绩展示 | 12 条通过 |
| `frontend/tests/unit/lab/LabSubmissionHistoryView.spec.ts` | 提交历史、空状态、失败提示 | 3 条通过 |

### 7.5 HWK 测试用例汇总

#### 7.5.1 自动化执行结果

| 测试类别 | 命令 | 执行结果 |
| --- | --- | --- |
| 后端 HWK 相关测试 | `mvn test "-Dtest=HomeworkControllerTest,HomeworkBearerAuthControllerTest,HomeworkMigrationTest,HomeworkSubmissionServiceTest"` | 44 条通过，0 失败，0 错误，0 跳过 |
| 前端 HWK 单元测试 | `node node_modules/vitest/vitest.mjs run tests/unit/hwk/homeworksApi.spec.ts tests/unit/hwk/HomeworkStudentListView.spec.ts tests/unit/hwk/HomeworkStudentView.spec.ts tests/unit/hwk/HomeworkSubmissionHistoryView.spec.ts tests/unit/hwk/HomeworkTeacherView.spec.ts --pool=threads` | 5 个测试文件通过，28 条测试通过 |

说明：早期记录中后端曾因 `backend/target/classes/schema.sql` 写入受限失败、前端曾因 esbuild 子进程 `spawn EPERM` 失败；2026-06-12 14:20 ~ 14:21 已在当前本机环境复跑同一组 HWK 目标命令，后端 44 条通过，前端 5 个测试文件 28 条通过。

#### 7.5.2 HWK 核心用例表

| 用例编号 | 对应需求 | 覆盖对象 | 前置条件/测试数据 | 操作步骤 | 预期结果 | 实际结果 | 通过状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TC-HWK-01 | FR-HWK-01 | UI-HWK-02；API-HWK-01、02、16；DB-HWK-01、02 | 教师/助教具备课程管理权限；准备客观题作业和题目数据 | 创建草稿，保存题目，读取详情 | 作业为 DRAFT，字段和题目正确落库 | `teacherCreatesObjectiveHomeworkDraftAndSavesQuestions`、前端教师创建/编辑用例通过 | 通过 |
| TC-HWK-02 | FR-HWK-01 | UI-HWK-03；API-HWK-03、05；LRN 事件 | 已有配置完整作业 | 教师发布作业，学生查询列表/详情 | 状态变为 PUBLISHED，学生可见，发送 HOMEWORK_PUBLISHED | `teacherPublishesConfiguredHomeworkAndNotificationIsEmitted`、前端发布用例通过 | 通过 |
| TC-HWK-03 | FR-HWK-01 | API-HWK-18；DB-HWK-03、07 | 代码题未配置测试用例 | 执行发布 | 返回 `HWK_4007`，状态不变 | `codeHomeworkWithoutTestCasesIsRejectedWhenPublishing`、前端代码题校验用例通过 | 通过 |
| TC-HWK-04 | FR-HWK-02 | UI-HWK-04；API-HWK-06、17；DB-HWK-02、03 | 已发布客观题/代码题，包含标准答案和隐藏用例 | 学生打开作业详情 | 显示说明和提交要求，不泄露答案、隐藏用例输出 | `studentPublishedHomeworkListAndDetailDoNotExposeAnswersOrHiddenTestCaseOutput` 通过 | 通过 |
| TC-HWK-05 | FR-HWK-02 | UI-HWK-05；API-HWK-07；DB-HWK-04 | 学生为课程成员；作业已发布且未截止 | 提交文本、客观题或代码答案 | 生成提交记录，返回提交编号、时间和初始评测/批阅状态 | `studentSubmitsPublishedTextHomeworkAndReceivesSubmissionReceipt`、前端学生提交用例通过 | 通过 |
| TC-HWK-06 | FR-HWK-02 | API-HWK-07；错误码 `HWK_4004` | 作业已超过截止时间且不允许迟交 | 学生提交作业 | 返回截止错误，不生成有效提交 | `studentCannotSubmitAfterDeadlineWhenLateSubmitIsDisabled` 通过 | 通过 |
| TC-HWK-07 | FR-HWK-03 | UI-HWK-06；API-HWK-08；DB-HWK-04 | 作业允许重复提交，学生提交多次 | 查询我的提交历史 | 历史完整，仅最新提交 `is_final=1` | `studentSubmissionHistoryKeepsPreviousVersionsAndMarksOnlyLatestFinal`、前端历史用例通过 | 通过 |
| TC-HWK-08 | FR-HWK-03 | UI-HWK-06；API-HWK-09、10；DB-HWK-04 | 教师/助教有课程管理权限；存在多名学生提交 | 查询提交列表，按学生和状态筛选，读取详情 | 支持分页、筛选、详情读取 | `courseManagerListsSubmissionsWithPaginationAndReadsSubmissionDetail`、`courseManagerFiltersSubmissionsByStudentAndStatuses` 通过 | 通过 |
| TC-HWK-09 | FR-HWK-04 | API-HWK-07、11；DB-HWK-05 | 客观题作业配置标准答案和分值 | 学生提交客观题答案，查询评测结果 | 自动计算分数，生成评测记录 | `objectiveHomeworkSubmissionCreatesEvaluationRecordAndResultView` 通过 | 通过 |
| TC-HWK-10 | FR-HWK-04 | UI-HWK-05、07；API-HWK-07、11；DB-HWK-03、05 | 代码题配置 IO 用例和语言白名单 | 学生提交代码，查询评测结果 | 返回评测状态、通过用例数和分数 | `codeHomeworkSubmissionEvaluatesIoCasesAndTeacherCanReevaluate`、前端代码评测展示用例通过 | 通过 |
| TC-HWK-11 | FR-HWK-04；NFR-HWK-01 | API-HWK-11；DB-HWK-04、05 | 代码提交触发错误结果 | 查询评测结果和提交详情 | 评测状态记录失败，提交记录不丢失 | `codeHomeworkEvaluationFailurePreservesSubmissionAndRecordsFailedStatus` 通过 | 通过 |
| TC-HWK-12 | FR-HWK-04、05 | UI-HWK-08；API-HWK-12；DB-HWK-05、06 | 已有提交和评测记录；教师提供重评理由 | 教师触发重评 | 新增评测记录，保留旧记录，写入重评日志 | `codeHomeworkSubmissionEvaluatesIoCasesAndTeacherCanReevaluate`、`objectiveReevaluationUpdatesSubmissionSummary` 通过 | 通过 |
| TC-HWK-13 | FR-HWK-05 | UI-HWK-08；API-HWK-13；DB-HWK-04、06 | 教师/助教有课程管理权限；存在待批阅提交 | 填写人工分数和评语 | 更新 manualScore、finalScore、comment，写入日志 | `courseManagerReviewsSubmissionAndReadsReviewAuditLogs`、前端教师批阅用例通过 | 通过 |
| TC-HWK-14 | FR-HWK-05 | API-HWK-13；错误码 `HWK_4008` | 作业总分 100，教师填写超出总分的分数 | 提交批阅 | 返回分数范围错误，不更新成绩 | `teacherReviewRejectsScoreOutsideHomeworkTotalScore` 通过 | 通过 |
| TC-HWK-15 | FR-HWK-05；NFR-HWK-03 | API-HWK-12、13、21；DB-HWK-06 | 存在批阅、重评和发布成绩操作 | 查询批阅日志 | 日志记录操作人、时间、原因和分数变化 | `courseManagerReviewsSubmissionAndReadsReviewAuditLogs`、`studentCannotReadPrivateReviewLogs` 通过 | 通过 |
| TC-HWK-16 | FR-HWK-06 | UI-HWK-07；API-HWK-10、11、14 | 学生成绩已发布 | 学生查询详情和反馈 | 展示允许公开的评测摘要、成绩和教师评语 | `scorePublishExposesStudentFeedbackAndHomeworkSourceGrades` 通过 | 通过 |
| TC-HWK-17 | FR-HWK-06；NFR-HWK-04 | API-HWK-08、10、11 | 学生成绩未发布 | 学生查询历史、详情和评测结果 | 不显示未公开最终分和教师评语 | `studentHistoryAndDetailHideUnpublishedScoresAndTeacherComment`、`objectiveHomeworkSubmissionShowsEvaluationButHidesUnpublishedFinalScore` 通过 | 通过 |
| TC-HWK-18 | FR-HWK-06；NFR-HWK-02 | UI-HWK-09；API-HWK-15；DB-HWK-04、05 | 多名学生提交和未提交 | 教师查询统计和未提交名单分页 | 展示提交数、未提交数、平均分等统计 | `teacherQueriesHomeworkStatisticsWithUnsubmittedStudentsAndScoreSummary`、`teacherQueriesHomeworkStatisticsWithPaginatedUnsubmittedStudentsForNfrPerformance` 通过 | 通过 |
| TC-HWK-19 | FR-HWK-01；NFR-HWK-01、03、04、05 | UI-HWK-01；API-HWK-22；DB-HWK-01~07；HWK_4001/HWK_4031/HWK_4095 | DRAFT/全部非 DRAFT、权限用户、已删除作业、完整子历史、删除前旧更新和末页唯一草稿 | 验证成功、取消无请求、403/404/409、并发防复活、父表软删/子历史保留和 UI 确认/pending/失败保留/末页回退 | 成功返回 `deleted=true`/删除时间；只删除父表；普通更新不能复活；仅 DRAFT 显示入口 | 后端 290 tests、前端 53 files/511 tests、typecheck/build 与 1440×900/390×844 浏览器真实 DELETE 通过 | 通过 |
| TC-HWK-N01 | NFR-HWK-01 | API-HWK-03、07、11、13 | 模拟通知投递失败、评测失败、重复提交冲突 | 执行发布、提交、查询和批阅 | 主数据保持一致，错误以受控响应返回 | `publishKeepsHomeworkPublishedWhenNotificationDeliveryFails`、`submitReturnsControlledConflictWhenSubmissionVersionIsAlreadyUsed` 通过 | 通过 |
| TC-HWK-N02 | NFR-HWK-02 | API-HWK-05、09、15；索引 | 作业列表、提交列表、统计使用分页参数和基础规模样本 | 查询列表和统计 | 返回分页结构，响应受控 | 后端分页统计用例、前端 API route 用例和 2026-06-12 本地压测批次通过 | 通过 |
| TC-HWK-N03 | NFR-HWK-03 | API-HWK-10、20、21；DB-HWK-04、05、06 | 存在多次提交、评测、重评、批阅 | 查询详情、评测日志、批阅日志 | 提交和日志可追溯 | 迁移测试和控制器日志用例通过 | 通过 |
| TC-HWK-N04 | NFR-HWK-04 | 全部 HWK 接口；DB-HWK-02、03、04、05 | 非成员、他人提交、隐藏用例、私有日志 | 越权访问或查询敏感数据 | 返回 `HWK_4031` 或隐藏敏感字段 | `studentCannotReadAnotherStudentsSubmission`、`nonMemberStudentCannotSubmitHomework`、隐藏用例/日志用例通过 | 通过 |
| TC-HWK-N05 | NFR-HWK-05 | 全部 HWK 流程 | 稳定测试数据、MockMvc、Vitest、H2 迁移 | 重复执行自动化测试 | 核心流程可重复验证 | 本文第 8 章命令已通过 | 通过 |

#### 7.5.3 前端 HWK 用例摘要

| 测试文件 | 覆盖内容 | 结果 |
| --- | --- | --- |
| `frontend/tests/unit/hwk/homeworksApi.spec.ts` | API-HWK-01 ~ 22 路由构造、请求方法、参数、ApiResponse 解包；API-HWK-22 DELETE | 所属前端全量 53 files/511 tests 通过 |
| `frontend/tests/unit/hwk/HomeworkStudentListView.spec.ts` | 学生作业列表、详情链接、空状态 | 2 条通过 |
| `frontend/tests/unit/hwk/HomeworkStudentView.spec.ts` | 学生详情、文本提交、空提交校验、代码语言选择、评测结果、学习进度记录、断点恢复 | 7 条通过 |
| `frontend/tests/unit/hwk/HomeworkSubmissionHistoryView.spec.ts` | 学生历史、教师分页列表、教师批阅、重评、筛选、空状态 | 6 条通过 |
| `frontend/tests/unit/hwk/HomeworkTeacherView.spec.ts` | 教师创建/编辑、发布/关闭、批阅/统计/成绩发布；DRAFT-only 删除、取消、pending、失败保留和末页回退 | 所属前端全量 53 files/511 tests 通过 |

### 7.6 GRD 测试用例汇总

#### 7.6.1 自动化执行结果

| 测试类别 | 命令 | 执行结果 |
| --- | --- | --- |
| 后端 GRD 相关测试 | `mvn test -Dtest=GradeItemControllerTest,GradeRecordControllerTest,GradeItemMigrationTest,GradeAnalysisServiceTest,GradeItemServiceTest,GradeRecordServiceTest,GradeReviewServiceTest` | 7 个测试类通过，49 条通过，0 失败，0 错误，0 跳过 |
| 前端 GRD 单元测试 | `node node_modules/vitest/vitest.mjs run tests/unit/grd/gradeItemsApi.spec.ts tests/unit/grd/gradeRecordsApi.spec.ts tests/unit/grd/GradeItemConfigView.spec.ts tests/unit/grd/TeacherGradeTableView.spec.ts tests/unit/grd/App.spec.ts src/views/grd/StudentGradeView.spec.ts --pool=threads` | 6 个测试文件通过，49 条测试通过 |

说明：前端测试运行时 Node 输出 `--localstorage-file` 未提供有效路径的警告，测试断言全部通过；该警告不影响 GRD 页面、路由和 API 用例结果。

#### 7.6.2 GRD 核心用例表

| 用例编号 | 对应需求 | 覆盖对象 | 前置条件/测试数据 | 操作步骤 | 预期结果 | 实际结果 | 通过状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TC-GR-01 | FR-GR-01 | UI-GRD-01；API-GRD-01 ~ 05、07；DB-GRD-01、05 | 教师具备课程管理权限；准备 LAB/HWK 来源任务、满分、权重、排序数据 | 查询、创建、修改、停用成绩项，执行规则校验 | 合法规则保存成功；非法权重、重复名称、非法来源被拒绝 | `GradeItemControllerTest`、`GradeItemServiceTest`、`GradeItemConfigView.spec.ts`、`gradeItemsApi.spec.ts` 通过 | 通过 |
| TC-GR-02 | FR-GR-02 | UI-GRD-02；API-GRD-06 ~ 09；DB-GRD-02、03、05 | 课程内有学生名单；LAB/HWK 来源成绩含已评分、缺失、未提交、未评分状态 | 教师同步来源成绩并查询成绩总表和学生明细 | 生成成绩记录、计算加权分和总评，缺失状态可见 | `teacherSyncsLabAndHomeworkSourceGradesThenCalculatesFinalScores`、`TeacherGradeTableView.spec.ts` 同步用例通过 | 通过 |
| TC-GR-03 | FR-GR-03 | UI-GRD-02、03、04、08；API-GRD-08 ~ 14；DB-GRD-02、03、07 | 已有成绩记录和课程总评；教师填写调整原因 | 查询总表、进入学生明细、调整单项成绩和总评、查询变更记录 | 分数更新，已发布成绩不回退未发布，变更记录保存旧值、新值、原因和操作人 | `teacherAdjustsGradeRecordWithReasonAndQueriesChangeLogsThroughApi`、`teacherAdjustsCourseFinalScoreWithReasonAndKeepsChangeLog`、前端明细调整用例通过 | 通过 |
| TC-GR-04 | FR-GR-04 | UI-GRD-05；API-GRD-12 ~ 14；DB-GRD-02、03、04、07 | 成绩已计算且可发布；存在选中学生范围 | 教师发布成绩，重复执行同一范围发布，查询发布记录 | 发布后学生可见，记录发布批次，重复发布幂等，不重复通知 | `teacherPublishesSelectedGradesAndEmitsGradePublishedEvent`、`repeatedPublishUsesRangeIdempotencyKeyAndDoesNotNotifyAgain`、前端发布记录用例通过 | 通过 |
| TC-GR-05 | FR-GR-05 | UI-GRD-06；API-GRD-15；DB-GRD-02、03 | 学生为课程成员；存在已发布和未发布成绩 | 学生查询我的课程成绩 | 只返回本人已发布成绩；未发布成绩不泄露分数字段 | `teacherPublishesSelectedStudentGradesThenStudentCanQueryPublishedResultThroughApi`、`StudentGradeView.spec.ts` 通过 | 通过 |
| TC-GR-06 | FR-GR-06 | UI-GRD-07；API-GRD-16、17；DB-GRD-08、02、03 | 成绩记录包含多分数段、缺失、未评分、未提交样本 | 教师查询课程总评分析和成绩项完成情况 | 返回均分、最高分、最低分、及格率、完成率、分布和来源时间点 | `GradeAnalysisServiceTest`、`teacherQueriesCourseGradeAnalysisThroughApi`、`teacherQueriesGradeItemCompletionThroughApi`、前端分析用例通过 | 通过 |
| TC-GR-07 | FR-GR-07 | UI-GRD-09、10；API-GRD-18 ~ 21；DB-GRD-06、07 | 学生已有已发布成绩；教师具备课程权限 | 学生提交异议，教师筛选并处理，同意修改或驳回 | 申请状态流转，重复 PENDING 申请被拒绝，同意修改写入变更记录并通知学生 | `GradeReviewServiceTest`、`studentSubmitsGradeReviewAndTeacherProcessesItThroughApi`、前端复核处理用例通过 | 通过 |
| TC-GR-08 | NFR-GR-01 | API-GRD-06、07、12；DB-GRD-02 ~ 05 | 模拟来源刷新、发布、重复发布和大班发布范围 | 同步、重算、发布、重复发布 | 数据事务边界稳定，发布幂等，发布范围摘要有长度边界 | `syncSourceGradesDeclaresTransactionalBoundaryForSyncAndRecalculation`、发布幂等和大班发布用例通过 | 通过 |
| TC-GR-09 | NFR-GR-02 | UI-GRD-02、06、07；API-GRD-08、15、16；DB-GRD-02、03、08 | 准备分页和基础统计样本 | 查询教师总表、学生个人成绩、教学分析 | 接口支持分页和筛选，基础统计可返回 | 后端查询用例、前端分页/分析用例和 2026-06-12 本地压测批次通过 | 通过 |
| TC-GR-10 | NFR-GR-03 | UI-GRD-08；API-GRD-06、12、13、14、21；DB-GRD-04 ~ 08 | 存在同步、发布、调整、复核、统计流程 | 查询批次、发布记录、变更记录、复核记录和快照 | 关键操作可追踪，统计快照记录来源时间点 | 迁移、服务和控制器日志/快照用例通过 | 通过 |
| TC-GR-11 | NFR-GR-04 | 全部 GRD 页面；全部 GRD API；DB-GRD-02、03、06 | 准备无权限教师、非成员学生、教师访问学生接口、未发布成绩 | 执行越权访问或敏感查询 | 返回受控错误，不泄露他人成绩、全班明细、未发布成绩或无权限复核 | 权限控制器/服务测试和前端未发布状态用例通过 | 通过 |
| TC-GR-12 | NFR-GR-05 | 全部 GRD 流程 | 稳定测试数据、MockMvc、Vitest、H2 迁移 | 重复执行自动化测试 | 核心流程、异常和状态流转可重复验证 | 本文第 8 章命令已通过 | 通过 |

#### 7.6.3 前端 GRD 用例摘要

| 测试文件 | 覆盖内容 | 结果 |
| --- | --- | --- |
| `frontend/tests/unit/grd/gradeItemsApi.spec.ts` | API-GRD-01 ~ 05 路由构造、请求方法、认证上下文缺失失败 | 2 条通过 |
| `frontend/tests/unit/grd/gradeRecordsApi.spec.ts` | API-GRD-06 ~ 21 同步、重算、表格、调整、发布、分析、完成情况、异议接口 | 6 条通过 |
| `frontend/tests/unit/grd/GradeItemConfigView.spec.ts` | 成绩项创建、列表刷新、规则校验、来源编号校验、修改、停用、规则验证 | 4 条通过 |
| `frontend/tests/unit/grd/TeacherGradeTableView.spec.ts` | 来源同步、总表筛选分页、学生明细、单项/总评调整、发布记录、教学分析、异议筛选和处理 | 9 条通过 |
| `frontend/src/views/grd/StudentGradeView.spec.ts` | 学生已发布成绩展示、未发布状态不泄露分数、提交总评异议并展示 PENDING 状态 | 3 条通过 |
| `frontend/tests/unit/grd/App.spec.ts` | GRD 课程导航、教师/学生成绩路由、课程上下文缺失提示、全局路由安全状态 | 25 条通过 |


## 8 测试执行日志

### 8.1 AUTH 测试执行日志

#### 8.1.1 后端 AUTH 执行日志

| 日志编号 | 时间 | 命令/测试类 | 执行内容 | 结果 |
| --- | --- | --- | --- | --- |
| AUTH-LOG-001 | 2026-06-12 14:20 | `mvn test -Dtest=AuthControllerTest,AuthAdminControllerTest,AuthMigrationScriptTest,HeaderCurrentUserProviderTest,AuthCrsIntegrationTest` | 使用本机 Maven 3.9.11 与 Java 25 复跑 AUTH 后端目标测试 | 构建成功，进入断言并全部通过 |
| AUTH-LOG-002 | 2026-06-12 14:20 | Maven + Java 25 当前环境 | 复核旧 JDK 版本阻塞项 | Java 运行时满足项目要求，未复现 class file 版本错误 |
| AUTH-LOG-003 | 2026-06-12 14:20 | `AuthAdminControllerTest` | 管理员用户、角色、权限、账号状态和审计日志管理 | 9 条通过 |
| AUTH-LOG-004 | 2026-06-12 14:20 | `AuthControllerTest` | 注册、登录、退出、当前用户、权限校验、异常、资料、密码、锁定 | 21 条通过 |
| AUTH-LOG-005 | 2026-06-12 14:20 | `AuthMigrationScriptTest` | AUTH 迁移 MySQL 兼容自增和时间戳语法 | 1 条通过 |
| AUTH-LOG-006 | 2026-06-12 14:20 | `HeaderCurrentUserProviderTest` | 兼容 Header 当前用户上下文解析和缺失鉴权失败 | 3 条通过 |
| AUTH-LOG-007 | 2026-06-12 14:20 | `AuthCrsIntegrationTest` | AUTH Bearer 登录态与 CRS 课程成员联动 | 2 条通过 |
| AUTH-LOG-008 | 2026-06-12 14:20 | Maven 汇总 | AUTH 目标测试 5 个测试类共 36 条通过；同批 AUTH/HWK 后端目标命令共 `Tests run: 80, Failures: 0, Errors: 0, Skipped: 0` | 构建成功 |

#### 8.1.2 前端 AUTH 执行日志

| 日志编号 | 时间 | 命令/测试文件 | 执行内容 | 结果 |
| --- | --- | --- | --- | --- |
| AUTH-LOG-009 | 2026-06-12 14:21 | `authApi.spec.ts` | AUTH API wrapper 路由、Bearer、存储、管理接口、审计日志、权限校验 | 7 条通过 |
| AUTH-LOG-010 | 2026-06-12 14:21 | `http.spec.ts` | 共享 HTTP 鉴权、401/403/账号异常跳转和敏感 header 策略 | 11 条通过 |
| AUTH-LOG-011 | 2026-06-12 14:21 | `AuthProfileView.spec.ts` | 资料加载、资料修改、密码修改和确认校验 | 3 条通过 |
| AUTH-LOG-012 | 2026-06-12 14:21 | `AuthView.spec.ts` | 登录、角色入口、注册和错误反馈 | 2 条通过 |
| AUTH-LOG-013 | 2026-06-12 14:21 | `AuthAdminView.spec.ts` | 管理员用户角色和角色权限管理页面状态 | 1 条通过 |
| AUTH-LOG-014 | 2026-06-12 14:21 | Vitest 汇总 | AUTH 目标测试 5 个文件共 24 条通过；同批 AUTH/HWK 前端目标命令共 `Test Files 10 passed (10)`、`Tests 52 passed (52)` | 构建成功 |

### 8.2 CRS 测试执行日志

#### 8.2.1 后端 CRS 执行日志

| 日志编号    | 时间             | 命令/测试类                        | 执行内容                                                     | 结果      |
| ----------- | ---------------- | ---------------------------------- | ------------------------------------------------------------ | --------- |
| CRS-LOG-001 | 2026-06-08 16:44 | `CourseControllerTest`             | CRS 控制器主流程：课程创建、选课、邀请码、审批、成员管理、章节、资源、公告、NFR 与异常矩阵 | 23 条通过 |
| CRS-LOG-002 | 2026-06-08 16:44 | `AuthCrsIntegrationTest`           | AUTH 与 CRS 登录态、权限上下文和课程访问协作                 | 2 条通过  |
| CRS-LOG-003 | 2026-06-08 16:44 | `HeaderCoursePermissionClientTest` | Header 课程权限客户端的成员/教师/角色判断                    | 6 条通过  |
| CRS-LOG-004 | 2026-06-08 16:44 | Maven 汇总                         | `Tests run: 31, Failures: 0, Errors: 0, Skipped: 0`          | 构建成功  |

#### 8.2.2 前端 CRS 执行日志

| 日志编号    | 时间             | 命令/测试文件                                      | 执行内容                                                     | 结果      |
| ----------- | ---------------- | -------------------------------------------------- | ------------------------------------------------------------ | --------- |
| CRS-LOG-005 | 2026-06-08 16:43 | `frontend/tests/unit/CourseManagementView.spec.ts` | CRS 页面交互、课程管理、章节、资源、公告、选课、成员审批与角色调整 | 18 条通过 |
| CRS-LOG-006 | 2026-06-08 16:43 | `frontend/tests/unit/app/courseMain.spec.ts`       | 课程入口路由挂载                                             | 1 条通过  |
| CRS-LOG-007 | 2026-06-08 16:43 | `frontend/tests/unit/api/http.spec.ts`             | CRS 资源下载依赖的共享 HTTP 鉴权、multipart 与错误处理       | 10 条通过 |
| CRS-LOG-008 | 2026-06-08 16:43 | Vitest 汇总                                        | `Test Files 32 passed (32)`、`Tests 170 passed (170)`        | 构建成功  |

#### 8.2.3 CRS 性能与规模样本

| 日志编号    | 场景                           | 样本规模               | 输出                                                         | 结果 |
| ----------- | ------------------------------ | ---------------------- | ------------------------------------------------------------ | ---- |
| CRS-LOG-009 | 课程列表与资源列表基础规模验证 | 105 门课程、105 个资源 | `CRS_PERF courseListMs=42 resourceListMs=20 courses=105 resources=105` | 通过 |

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

### 8.4 LAB 测试执行日志

#### 8.4.1 后端 LAB 执行日志

| 日志编号 | 时间 | 命令/测试类 | 执行内容 | 结果 |
| --- | --- | --- | --- | --- |
| LAB-LOG-001 | 2026-06-11 23:12 | `LabExperimentControllerTest` | 实验创建、发布、截止、成绩发布、统计、权限与异常矩阵 | 8 条通过 |
| LAB-LOG-002 | 2026-06-11 23:12 | `LabSubmissionControllerTest` | 提交、历史、评测、报告、评分、结果展示、GRD 来源成绩与权限边界 | 29 条通过 |
| LAB-LOG-003 | 2026-06-11 23:12 | `LabExperimentMigrationTest` | 迁移脚本、报告表、评分表、评分变更表与软删除约束 | 7 条通过 |
| LAB-LOG-004 | 2026-06-11 23:12 | `LabExperimentTransactionTest` | 实验与测试用例创建/更新的事务回滚 | 2 条通过 |
| LAB-LOG-005 | 2026-06-11 23:12 | `LabEvaluationServiceTest` | 评测器异常时状态落库与失败结果留痕 | 1 条通过 |
| LAB-LOG-006 | 2026-06-11 23:12 | Maven 汇总 | `Tests run: 47, Failures: 0, Errors: 0, Skipped: 0` | 构建成功 |

#### 8.4.2 前端 LAB 执行日志

| 日志编号 | 时间 | 命令/测试文件 | 执行内容 | 结果 |
| --- | --- | --- | --- | --- |
| LAB-LOG-007 | 2026-06-11 23:12 | `tests/unit/api/labs.spec.ts` | LAB API wrapper：报告下载、报告评分、统计接口 | 3 条通过 |
| LAB-LOG-008 | 2026-06-11 23:12 | `tests/unit/lab/LabTeacherView.spec.ts` | 教师端实验管理、统计、提交筛选、报告和评分交互 | 11 条通过 |
| LAB-LOG-009 | 2026-06-11 23:12 | `tests/unit/lab/LabStudentView.spec.ts` | 学生端实验详情、提交、评测结果、报告上传下载、成绩展示 | 12 条通过 |
| LAB-LOG-010 | 2026-06-11 23:12 | `tests/unit/lab/LabSubmissionHistoryView.spec.ts` | 提交历史、空态、失败提示 | 3 条通过 |
| LAB-LOG-011 | 2026-06-11 23:12 | Vitest 汇总 | `Test Files 4 passed (4)`、`Tests 29 passed (29)` | 构建成功 |

### 8.5 HWK 测试执行日志

#### 8.5.1 后端 HWK 执行日志

| 日志编号 | 时间 | 命令/测试类 | 执行内容 | 结果 |
| --- | --- | --- | --- | --- |
| HWK-LOG-001 | 2026-06-12 14:20 | `mvn test -Dtest=HomeworkControllerTest,HomeworkBearerAuthControllerTest,HomeworkMigrationTest,HomeworkSubmissionServiceTest` | 使用本机 Maven 3.9.11 与 Java 25 复跑 HWK 后端目标测试 | 构建成功，未复现 `schema.sql` 写入受限，进入断言并全部通过 |
| HWK-LOG-002 | 2026-06-12 14:20 | `HomeworkBearerAuthControllerTest` | Bearer 登录态、AUTH/CRS 成员联动、作业可见性、提交和批阅权限 | 2 条通过 |
| HWK-LOG-003 | 2026-06-12 14:20 | `HomeworkControllerTest` | HWK 控制器主流程、异常、权限、评测、批阅、统计、通知和成绩来源 | 35 条通过 |
| HWK-LOG-004 | 2026-06-12 14:20 | `HomeworkMigrationTest` | HWK 迁移语法、外键、唯一约束、提交/评测/批阅日志表契约 | 6 条通过 |
| HWK-LOG-005 | 2026-06-12 14:20 | `HomeworkSubmissionServiceTest` | 重复提交版本冲突返回受控业务错误 | 1 条通过 |
| HWK-LOG-006 | 2026-06-12 14:20 | Maven 汇总 | HWK 目标测试 4 个测试类共 `Tests run: 44, Failures: 0, Errors: 0, Skipped: 0`；同批 AUTH/HWK 后端目标命令共 `Tests run: 80, Failures: 0, Errors: 0, Skipped: 0` | 构建成功 |

#### 8.5.2 前端 HWK 执行日志

| 日志编号 | 时间 | 命令/测试文件 | 执行内容 | 结果 |
| --- | --- | --- | --- | --- |
| HWK-LOG-007 | 2026-06-12 14:21 | `node node_modules/vitest/vitest.mjs run ... --pool=threads` | 使用当前本机 Node/Vitest 复跑 HWK 前端目标单测 | 构建成功，未复现 esbuild `spawn EPERM`，进入断言并全部通过 |
| HWK-LOG-008 | 2026-06-12 14:21 | `homeworksApi.spec.ts` | HWK API wrapper 路由、方法、参数和响应处理 | 6 条通过 |
| HWK-LOG-009 | 2026-06-12 14:21 | `HomeworkStudentListView.spec.ts` | 学生作业列表和空状态 | 2 条通过 |
| HWK-LOG-010 | 2026-06-12 14:21 | `HomeworkStudentView.spec.ts` | 学生作业详情、提交、校验、代码评测、学习记录 | 7 条通过 |
| HWK-LOG-011 | 2026-06-12 14:21 | `HomeworkSubmissionHistoryView.spec.ts` | 提交历史、教师筛选、批阅、重评、日志刷新 | 6 条通过 |
| HWK-LOG-012 | 2026-06-12 14:21 | `HomeworkTeacherView.spec.ts` | 教师创建/编辑、发布/关闭、统计、成绩发布 | 7 条通过 |
| HWK-LOG-013 | 2026-06-12 14:21 | Vitest 汇总 | HWK 目标测试 5 个文件共 `Test Files 5 passed (5)`、`Tests 28 passed (28)`；同批 AUTH/HWK 前端目标命令共 `Test Files 10 passed (10)`、`Tests 52 passed (52)` | 构建成功 |

### 8.6 GRD 测试执行日志

#### 8.6.1 后端 GRD 执行日志

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

#### 8.6.2 前端 GRD 执行日志

| 日志编号 | 时间 | 命令/测试文件 | 执行内容 | 结果 |
| --- | --- | --- | --- | --- |
| GRD-LOG-009 | 2026-06-10 15:51 | `gradeItemsApi.spec.ts` | GRD 成绩项 API wrapper 路由、方法和认证上下文 | 2 条通过 |
| GRD-LOG-010 | 2026-06-10 15:51 | `gradeRecordsApi.spec.ts` | GRD 成绩同步、重算、表格、调整、发布、分析和异议 API wrapper | 6 条通过 |
| GRD-LOG-011 | 2026-06-10 15:51 | `StudentGradeView.spec.ts` | 学生已发布成绩、未发布提示、成绩异议申请状态 | 3 条通过 |
| GRD-LOG-012 | 2026-06-10 15:51 | `GradeItemConfigView.spec.ts` | 教师成绩项配置页创建、校验、更新和停用交互 | 4 条通过 |
| GRD-LOG-013 | 2026-06-10 15:51 | `TeacherGradeTableView.spec.ts` | 教师成绩总表、同步、分页、明细、调整、发布、分析、复核处理 | 9 条通过 |
| GRD-LOG-014 | 2026-06-10 15:51 | `App.spec.ts` | GRD 导航与路由、教师/学生成绩入口、课程上下文和权限状态 | 25 条通过 |
| GRD-LOG-015 | 2026-06-10 15:51 | Vitest 汇总 | `Test Files 6 passed (6)`、`Tests 49 passed (49)` | 构建成功 |


## 9 手工测试与联调确认

### 9.1 AUTH 手工测试与联调确认

| 手测编号 | 模块 | 场景 | 操作要点 | 预期结果 | 本次联调结果 |
| --- | --- | --- | --- | --- | --- |
| MAN-AUTH-001 | AUTH | 浏览器登录、刷新和退出 | 使用学生、教师、管理员账号登录，刷新页面，退出后访问 `/courses` 等需认证页面 | 登录态保持；退出后跳转登录或提示会话失效 | 通过；2026-06-12 本地 DEV 浏览器验证 `student001`、`teacher001`、`admin001` 真实表单登录成功，过期会话页可见 |
| MAN-AUTH-002 | AUTH | 浏览器注册和失败提示 | 切换注册模式，创建学生账号；提交重复邮箱、弱密码或缺失字段 | 成功后回到登录；失败提示清晰且不暴露内部细节 | 通过；2026-06-12 注册 `manual458863` 成功，重复注册同账号显示“账号已存在” |
| MAN-AUTH-003 | AUTH | 个人资料与修改密码 | 登录后进入个人资料页，修改昵称/联系方式/头像，修改密码后使用旧密码和旧会话访问 | 资料更新；旧密码和旧会话失效；提示明确 | 通过；2026-06-12 `manual458863` 资料保存成功，改密后旧密码登录失败、新密码登录成功 |
| MAN-AUTH-004 | AUTH | 管理员用户、角色、权限管理 | 管理员查询用户、创建用户、调整角色、调整角色权限、禁用账号 | 管理操作成功；目标用户权限刷新；禁用账号无法继续访问 | 通过；2026-06-12 管理员页面可见用户/角色/权限/审计，新增教师 `adminmade512881` 成功并出现在用户列表 |
| MAN-AUTH-005 | AUTH | 权限边界页面 | 学生访问教师入口和管理员后台；教师访问管理员后台；管理员访问审计日志 | 越权页面显示 403；接口返回 403；管理员可查看日志 | 有条件通过；2026-06-12 本地安全探针验证匿名 401、伪造 Header 401、伪造 Bearer 401、学生访问管理接口 403 |
| MAN-AUTH-006 | AUTH | 会话过期和账号异常状态 | 手工制造过期 token、撤销 token、锁定账号、禁用账号 | 前端清理登录态并跳转登录失效或账号状态页 | 通过；2026-06-12 浏览器确认过期会话页，改密后旧密码失败且新密码成功 |
| MAN-AUTH-007 | AUTH/CRS/HWK/LAB/GRD/LRN | 主流程登录态联调 | 登录后进入课程、作业/实验、成绩、通知页面，确认各模块使用后端当前用户上下文 | 不需要前端传操作者 `userId`；各模块继续执行业务归属校验 | 通过；2026-06-12 本地 DEV 浏览器以学生、教师、管理员三类 Bearer 登录态贯穿课程、学习、实验、作业、成绩、通知和平台管理页面 |
| MAN-AUTH-008 | AUTH | 专项安全扫描 | 使用 OWASP/ZAP 或等效工具扫描认证、管理和资料接口 | 无高危认证绕过、敏感信息泄露、明文密码或令牌泄露 | 待专项测试；需补 OWASP ZAP 或等效工具报告编号、扫描时间、目标环境和处置结论 |
| MAN-AUTH-009 | AUTH | 性能和并发 | 准备大量用户、会话和审计日志，压测登录、`/me`、权限校验、审计分页 | 常规认证和权限接口满足 3 秒响应要求，分页稳定 | 有条件通过；本地压测通过，生产实流量长稳待验收环境复核 |
| MAN-AUTH-010 | AUTH/全部模块 | 统一测试环境闭环 | 执行“登录 -> 课程 -> 作业/实验 -> 提交/评测 -> 成绩 -> 通知” | AUTH 登录态贯穿主流程，异常和权限边界一致 | 通过；2026-06-12 本地 DEV 执行登录、课程、学习任务、实验提交、作业提交、成绩同步、通知跳转和成绩复核闭环 |

### 9.2 CRS 手动测试

| 手测编号    | 模块 | 场景                           | 操作要点                                        | 预期结果                                             | 本次联调结果 |
| ----------- | ---- | ------------------------------ | ----------------------------------------------- | ---------------------------------------------------- | ------------ |
| MAN-CRS-001 | CRS  | 教师创建课程并查看详情         | 以教师身份登录，创建课程，进入课程详情          | 课程保存成功，教师可进入管理区                       | 通过 |
| MAN-CRS-002 | CRS  | 学生加入公开课/邀请码课/审批课 | 以学生身份加入不同选课模式课程                  | 公开课直接加入，邀请码错误提示明确，审批课显示待审批 | 通过 |
| MAN-CRS-003 | CRS  | 教师审批与成员管理             | 教师审批学生、调整角色、移除成员                | 成员状态刷新正确，越权操作被阻止                     | 通过 |
| MAN-CRS-004 | CRS  | 章节管理                       | 教师新增多级章节并调整顺序，学生查看章节树      | 教师可维护，学生只读，顺序正确                       | 通过 |
| MAN-CRS-005 | CRS  | 资源上传下载                   | 教师上传 PDF/文档，学生下载；尝试上传不支持文件 | 合法文件可下载，不支持文件被拒绝                     | 通过 |
| MAN-CRS-006 | CRS  | 公告管理与置顶                 | 教师发布两条公告并置顶其中一条，学生查看        | 置顶公告优先展示，编辑/删除后列表刷新                | 通过 |

#### 9.2.1 CRS 手工联调补测记录

| 项目 | 记录 |
| ---- | ---- |
| 执行时间 | 2026-06-11 22:48 |
| 执行环境 | 本地 DEV 环境，后端 `http://127.0.0.1:8080`，前端 `http://127.0.0.1:5173` |
| 登录账号 | 教师 `teacher001`，学生 `student001` |
| 执行方式 | 真实 Bearer 登录态接口联调；浏览器打开课程中心做页面渲染 smoke |
| 课程创建与详情 | 教师创建公开课 `9502`，详情接口返回 `manageable=true`，浏览器课程中心可见该课程 |
| 选课模式 | 学生加入公开课 `9502` 后为 `ACTIVE`；邀请码课 `9503` 错误邀请码返回受控错误，正确邀请码加入成功；审批课 `9504` 首次加入为 `PENDING` |
| 审批与成员管理 | 教师审批 `student001` 进入审批课后为 `ACTIVE`，角色调整为 `ASSISTANT` 后再恢复 `STUDENT`，移除后权限查询为 `REMOVED`，学生访问课程详情被拒绝 |
| 章节管理 | 教师在公开课创建父章节 `950102` 和子章节 `950103`，学生读取章节树可见父子结构，学生尝试创建章节被拒绝 |
| 资源上传下载 | 教师上传 PDF 资源 `950103`，学生下载内容与上传文件一致；上传 `.exe` 文件返回“不支持的文件类型” |
| 公告管理 | 教师创建普通公告 `3` 和置顶公告 `4`，学生列表置顶公告优先；编辑普通公告并置顶后，课程首页摘要可见置顶状态 |
| 浏览器 smoke | 教师登录页显示“登录成功”，进入 `/courses` 后可见 `CRS手工联调公开课-20260611224803`、邀请码课和审批课 |

### 9.3 LRN 手动测试

| 手工编号 | 关联用例 | 场景 | 前置条件 | 操作步骤 | 预期结果 | 当前状态 |
| --- | --- | --- | --- | --- | --- | --- |
| LRN-MAN-001 | TC-LN-01 | 首页进入学习任务中心并查看分页 | 本地前后端启动，学生账号已登录并加入课程 | 从首页点击学习任务入口，切换状态/类型筛选，点击上一页/下一页 | 页面毛玻璃风格一致；任务为当前学生可见课程；分页可切换；空态/失败态清晰 | 通过；2026-06-12 浏览器 smoke 可见学习任务中心、实验、作业和资源任务；补充接口筛选 `taskType=HOMEWORK&page=0&size=1` 返回 `total=1`、`size=1`、`taskType=HOMEWORK` |
| LRN-MAN-002 | TC-LN-02 | 课程资源断点续传 | 学生已打开课程资源并产生进度 | 从学习进度页点击继续学习 | 回到课程页对应章节/资源位置，不进入错误课程 | 通过；2026-06-12 浏览器从学习进度页点击“继续学习”跳转到 `/courses/9501/homeworks/950301?role=student&resume=homeworkId%3D950301`，页面显示“已恢复上次断点” |
| LRN-MAN-003 | TC-LN-02 | LAB/HWK 断点续传 | 学生在实验/作业中保存草稿或产生进度 | 从学习进度页点击继续学习到 LAB/HWK | 代码或作答上下文恢复，打开页面不会立即覆盖原断点 | 通过；2026-06-12 浏览器确认 HWK 断点参数恢复，LAB/HWK 入口在学习任务中心可见 |
| LRN-MAN-004 | TC-LN-03 | 仪表盘数据随真实学习动作变化 | 学生进入 CRS/LAB/HWK 并完成访问或提交 | 打开学习仪表盘，观察总时长、访问次数、趋势和最近记录 | 统计与真实操作一致，失败时展示重试或缓存提示 | 通过；2026-06-12 浏览器学习仪表盘显示 30 分钟学习时长、1 次访问和最近学习行为 |
| LRN-MAN-005 | TC-LN-04、TC-LN-05 | 通知中心分类、已读、删除、跳转 | 账号存在任务、成绩、公告、系统通知 | 切换类型/未读筛选，批量已读，删除单条，点击业务跳转 | 未读数变化正确；删除后列表隐藏；跳转到对应业务页；其他用户通知不受影响 | 通过；2026-06-12 浏览器点击通知详情跳转到课程页，全部标已读后未读数 4 -> 0，删除后通知总数 4 -> 3 |
| LRN-MAN-006 | TC-LN-04 | 跨模块真实事件生成通知 | CRS/LAB/HWK/GRD 模块在同一环境可用 | 发布公告、发布实验/作业、发布成绩或复核成绩 | LRN 收到事件并生成分类通知，重复操作不生成重复通知 | 通过；2026-06-12 本地 DEV 可见 LAB/HWK/GRD 通知，作业成绩发布和成绩复核处理后链路继续可用 |
| LRN-MAN-007 | TC-LN-06 | 提醒规则设置和截止提醒 | 学生有临近截止且未提交的 LAB/HWK | 修改提醒偏好，等待或触发提醒扫描 | 符合规则的未提交任务产生提醒；关闭非必要提醒后不再收到非必要提醒 | 通过；2026-06-12 浏览器提醒设置页保存后显示“提醒规则已保存” |
| LRN-MAN-008 | TC-LN-N01 | 权限边界手工核对 | 准备学生 A、学生 B、教师、非成员账号 | 分别访问学习任务、进度、仪表盘、通知和提醒页面 | A/B 互不泄露数据；非成员被拒绝；学生不能查看教师聚合 | 通过；2026-06-12 本地 DEV 注册非成员 `manualboundary1104`，其 `courseId=9501` 任务数为 0，学习进度和统计返回 403；学生访问教师聚合返回 403，教师访问聚合返回 200 且学生数为 1 |
| LRN-MAN-009 | TC-LN-N01、TC-LN-N04 | 会话过期和网络异常 | 删除 token 或关闭后端服务 | 打开 LRN 页面并执行刷新/保存/已读/删除操作 | 显示登录失效或网络失败提示，不展示其他用户缓存 | 通过；2026-06-12 浏览器覆盖过期会话页，通知已读/删除失败时不泄露其他用户数据由自动化覆盖 |
| LRN-MAN-010 | TC-LN-N02、TC-LN-N03 | 通知触达时延和可靠性 | 测试环境支持通知轮询或推送 | 触发任务/成绩/公告通知并计时 | 通知列表和未读数在设计阈值内刷新，断线恢复后不丢通知 | 有条件通过；2026-06-12 本地 DEV 验证列表刷新、已读、删除和业务跳转，真实推送/轮询时延需 FAT/UAT 计时 |

### 9.4 LAB 手工测试与联调确认

| 手测编号 | 模块 | 场景 | 操作要点 | 预期结果 | 当前结果 |
| --- | --- | --- | --- | --- | --- |
| MAN-LAB-001 | LAB/AUTH/CRS | 教师创建并发布实验 | 浏览器登录教师账号，进入课程，创建实验并发布 | 实验保存成功，课程学生可见，教师列表状态刷新 | 通过；2026-06-12 本地 DEV 创建并发布 `手工验收实验-栈操作`，实验 ID `950202`，教师实验列表显示 `PUBLISHED` |
| MAN-LAB-002 | LAB | 学生查看实验并提交代码 | 学生进入实验详情，选择语言并提交代码 | 提交成功，显示受理状态并最终刷新评测结果 | 通过；2026-06-12 学生浏览器提交 `950202`，生成提交 `950204`，页面显示“提交成功，版本 1”和评测结果 |
| MAN-LAB-003 | LAB | 学生上传实验报告 | 学生在已有提交基础上上传 PDF/DOCX/ZIP 报告 | 报告上传成功，最新版本正确，下载入口可用 | 通过；2026-06-12 干净 H2 `tst08_manual_followup` 创建需报告实验 `950202`，学生提交 `950204` 后上传 `tst08-report.pdf` 得到报告 `1`、版本 `1`，下载文件与上传内容一致，教师报告评分 28 分保存成功 |
| MAN-LAB-004 | LAB | 教师查看提交并评分 | 教师筛选学生提交，查看代码、报告、评测结果并评分 | 自动分、报告分、人工分、最终分和评语保存成功 | 通过；2026-06-12 教师评分提交 `950204`，人工分 86、最终分 86、评语“本地手工验收评分通过”保存成功 |
| MAN-LAB-005 | LAB | 学生查看发布前后结果差异 | 成绩发布前后分别进入结果页 | 发布前隐藏教师评分与报告评分，发布后展示完整反馈 | 通过；2026-06-12 学生实验详情可见提交 `950204`、最终得分 86、人工评分 86 和教师评语 |
| MAN-LAB-006 | LAB | 教师查看实验统计 | 打开统计页查看提交率、未提交名单和分数分布 | 统计数据与实际样本一致，页面可视化显示正常 | 通过；2026-06-12 教师实验列表可见新实验与提交入口，统计接口由自动化和本地性能批次覆盖 |
| MAN-LAB-007 | LAB | 权限与异常边界 | 非成员、学生访问教师入口、他人结果访问、实验已截止重提 | 页面提示明确，接口返回受控错误 | 通过；2026-06-12 已发布成绩的演示实验拒绝再次提交并显示“当前实验状态不允许提交”，越权边界由自动化覆盖 |
| MAN-LAB-008 | LAB | 真实 Docker 沙箱专项测试 | 提交 AC/WA/编译错误/运行错误/超时/内存超限样本，多语言并发执行 | 状态、日志、资源限制和超时控制符合设计 | 有条件通过；2026-06-12 `OJ_DOCKER_SANDBOX_TEST=true mvn -Dtest=DockerSandboxExecutorTest test` 复跑 2 条通过，当前 Docker 执行器实现仅支持 Python，Java 多语言端到端不属于本地 DEV 已实现能力，需在最终环境或设计调整中确认 |
| MAN-LAB-009 | LAB | 基础性能样本 | 准备更多课程学生和实验提交，观察提交受理与统计接口响应时间 | 提交快速受理，统计查询在设计阈值内返回 | 本地压测通过 |
| MAN-LAB-010 | LAB/LRN/GRD | 跨模块联调 | 发布实验、提交、评分、发布成绩后检查通知中心和成绩同步 | LRN 能看到实验发布/成绩发布通知，GRD 能读取 LAB 来源成绩 | 通过；2026-06-12 本地 DEV 评分 `950204` 后执行 GRD 同步，`syncedCount=2`、`affectedStudentCount=1` |

### 9.5 HWK 手工测试与联调确认

| 手测编号 | 模块 | 场景 | 操作要点 | 预期结果 | 当前结果 |
| --- | --- | --- | --- | --- | --- |
| MAN-HWK-001 | HWK/AUTH/CRS | 教师创建并发布作业 | 浏览器登录教师账号，进入课程，创建文本/客观题/代码题作业并发布 | 作业保存成功，学生可见，教师端状态刷新 | 通过；2026-06-12 本地 DEV 创建并发布 `手工验收作业-复杂度说明`，作业 ID `950302`，教师作业列表显示 `PUBLISHED` |
| MAN-HWK-002 | HWK | 学生提交作业 | 学生登录后进入作业详情，提交文本答案、客观题答案和代码答案 | 提交成功，显示提交时间和初始评测状态 | 通过；2026-06-12 学生提交 `950302`，生成提交 `950304`，页面显示 `SUBMITTED / NONE / UNREVIEWED` |
| MAN-HWK-003 | HWK | 教师批阅与重评 | 教师查看提交列表，筛选学生，录入人工分数/评语，触发重评 | 分数、评语、评测状态和日志刷新 | 通过；2026-06-12 教师批阅提交 `950304`，人工分 90、最终分 90、评语“本地手工验收批阅通过” |
| MAN-HWK-004 | HWK | 学生查看反馈 | 成绩发布前后分别查看详情、历史和评测结果 | 发布前隐藏最终分，发布后展示允许公开的反馈 | 通过；2026-06-12 作业 `950302` 成绩发布后学生页面显示 `SCORE_PUBLISHED` |
| MAN-HWK-005 | HWK | 权限边界 | 非成员、其他学生、无课程管理权限教师访问 HWK 页面和接口 | 页面提示权限不足，接口返回受控错误，不泄露敏感字段 | 通过；2026-06-12 学生/教师角色页面分流正常，越权边界由自动化和本地安全探针覆盖 |
| MAN-HWK-006 | HWK | 页面状态 | 人为制造加载中、空列表、接口失败、会话过期、发布配置不完整 | 页面有清晰提示，操作入口禁用或引导正确 | 通过；2026-06-12 浏览器覆盖作业列表、作业详情、提交成功、已发布成绩状态；空态/失败态由前端单测覆盖 |
| MAN-HWK-007 | HWK/LAB | 真实代码评测沙箱 | 使用真实 Docker 沙箱提交 Python/Java 代码，包含 AC、WA、编译错误、超时 | 状态、日志、资源限制和隐藏用例显示策略正确 | 有条件通过；真实 Docker Python 执行器测试通过，HWK 真实提交/批阅链路已在 `950302`/`950304` 验证；当前 Docker 执行器实现仅支持 Python，Java 样本需在最终环境或设计调整中确认 |
| MAN-HWK-008 | HWK | 基础性能 | 准备大批量作业、提交和未提交学生，查询列表/统计 | 分页正常，响应时间满足测试负责人设定阈值 | 本地压测通过 |
| MAN-HWK-009 | HWK/LRN/GRD | 跨模块联调 | 发布作业、完成评测/批阅、发布成绩，查看通知中心、学习任务、成绩同步 | LRN 通知/提醒生成，GRD 可同步 HWK 来源成绩 | 通过；2026-06-12 本地 DEV 批阅 `950304` 后 GRD 同步返回 `syncedCount=2`，作业成绩发布接口返回 `SCORE_PUBLISHED` |
| MAN-HWK-011 | HWK/CRS | #224 草稿逻辑删除与响应式教师入口 | 在 1440×900/390×844 验证仅 DRAFT 显示入口、取消无请求、真实 DELETE 和成功刷新；其余失败/pending/末页回退由单测覆盖 | 删除成功、无横向溢出、控制台干净，非 DRAFT 无入口 | 通过；DELETE 200/deleted=true，总数 3→2；390px documentWidth=innerWidth=390；0 error/0 warning；4 张截图见 `output/playwright/issue-224/README.md` |

### 9.6 GRD 手工测试与联调确认

| 手测编号 | 模块 | 场景 | 操作要点 | 预期结果 | 当前结果 |
| --- | --- | --- | --- | --- | --- |
| MAN-GRD-001 | GRD/AUTH/CRS | 教师配置成绩项 | 浏览器登录教师账号，进入课程成绩项配置页，创建 LAB/HWK 来源成绩项并校验权重 | 成绩项保存成功，非法权重和非法来源提示明确 | 通过；2026-06-12 教师成绩项配置页可见 LAB 权重 0.4、HWK 权重 0.6，规则校验由自动化覆盖 |
| MAN-GRD-002 | GRD/LAB/HWK | 同步来源成绩并计算总评 | 准备真实实验/作业评分，教师触发同步和重算 | 成绩记录、加权分、缺失状态、总评与 LAB/HWK 来源一致 | 通过；2026-06-12 教师执行同步和重算，`syncedCount=2`、`missingCount=0`、`ungradedCount=0`、`affectedCount=1` |
| MAN-GRD-003 | GRD | 教师成绩总表与学生明细 | 教师筛选分页查看成绩总表，打开学生明细，查看来源任务和状态 | 总表分页、筛选、明细、缺失状态和来源信息正确 | 通过；2026-06-12 教师成绩表显示 1 名学生、2 条明细、总评 89.6，复核后显示总评 90、状态 `ADJUSTED` |
| MAN-GRD-004 | GRD/LRN | 成绩发布与学生可见 | 教师发布成绩，学生刷新个人成绩页，通知中心查看成绩发布通知 | 发布记录保存，学生只能看到本人已发布成绩，LRN 通知可见 | 通过；2026-06-12 学生个人成绩页可见总评 90、状态 `ADJUSTED`，通知详情可跳转成绩相关课程页 |
| MAN-GRD-005 | GRD | 成绩调整与变更记录 | 教师对已发布单项成绩或总评进行带原因调整 | 分数更新，变更记录显示旧值、新值、原因、操作人和时间 | 通过；2026-06-12 学生提交课程总评异议，教师同意调整为 90，教师表和学生页均显示 `ADJUSTED` |
| MAN-GRD-006 | GRD | 教学分析 | 教师查看课程总评分析和单项成绩完成情况 | 均分、最高分、最低分、及格率、完成率、分布和来源时间点正确 | 通过；2026-06-12 教师成绩分析显示均分 89.6、及格率 100%、完成率 100%、数据时间点 `2026-06-12T13:55:04.797691` |
| MAN-GRD-007 | GRD/LRN | 成绩异议复核 | 学生对已发布成绩提交异议，教师处理同意或驳回，学生查看结果 | 申请状态流转正确，重复申请被拦截，处理结果通知可见 | 通过；2026-06-12 学生提交异议后显示 `PENDING`，教师处理后显示“复核已处理：APPROVED”，学生复核记录显示 `APPROVED` 和处理说明 |
| MAN-GRD-008 | GRD/AUTH/CRS | 权限边界 | 非课程教师、非成员学生、学生访问教师接口、教师访问学生个人接口 | 页面提示权限不足，接口返回受控错误，不泄露成绩数据 | 通过；2026-06-12 学生/教师成绩页按角色分流，接口权限边界由自动化和本地安全探针覆盖 |
| MAN-GRD-009 | GRD | 页面状态 | 制造加载中、空成绩项、无成绩记录、接口失败、会话过期 | 页面有清晰提示，按钮禁用或引导正确 | 通过；2026-06-12 浏览器覆盖教师成绩表、教学分析、复核列表、学生成绩页和复核记录；空态/失败态由前端单测覆盖 |
| MAN-GRD-010 | GRD | 基础性能 | 准备大批量学生、成绩项和成绩记录，查询总表、个人成绩和分析 | 分页正常，响应时间满足测试负责人设定阈值 | 本地压测通过 |


## 10 缺陷、风险与处理建议

| 风险编号 | 风险项 | 当前证据 | 处理建议 | 状态 |
| --- | --- | --- | --- | --- |
| TST08-RISK-001 | 生产级安全扫描报告未纳入底稿 | 后端、前端自动化和 2026-06-12 本地安全探针覆盖匿名、伪造 Header、伪造 Bearer、学生越权等核心边界，但未附 OWASP ZAP 或等效工具报告 | FAT/UAT 阶段补充扫描目标、时间、工具版本、报告编号和高危项处置结论 | 待专项测试 |
| TST08-RISK-002 | 生产实流量长稳和并发审批未形成验收环境记录 | 本地性能批次覆盖 154 条后端样本，课程/资源 105 条基础规模样本通过；未覆盖生产网络、真实并发和长稳日志 | 在验收环境补充登录、课程、选课/审批、作业/实验、成绩和通知的连续运行记录 | 待验收环境复核 |
| TST08-RISK-003 | FAT/UAT 浏览器矩阵未完整留痕 | 2026-06-12 已在本地 DEV 浏览器验证学生、教师、管理员三类角色矩阵，覆盖注册、资料、管理员、学习、通知、实验、作业、成绩和复核操作 | FAT/UAT 阶段补充正式验收账号、环境 URL、截图或测试记录编号 | 本地通过，待验收环境复核 |
| TST08-RISK-004 | 跨模块真实事件触发链仍需环境级复核 | 本地 DEV 已执行“登录 -> 课程 -> 作业/实验 -> 提交/评测 -> 成绩 -> 通知/复核”闭环；成绩同步返回 `syncedCount=2`、`affectedStudentCount=1` | 在统一验收环境复跑一次性闭环并保存操作记录 | 本地通过，待验收环境复核 |
| TST08-RISK-005 | 本地文件库可能残留旧演示数据 | 2026-06-12 本地 `backend/data/onlinejudge` 启动的 8080 服务曾在 `/api/v1/submissions/950303/evaluation` 返回 500；后续手工矩阵使用干净 H2 内存库 `tst08_manual` 在 8080 复跑通过 | 演示或验收前重置本地文件库，或以干净 H2/验收数据库作为验收基准 | 待验收环境复核 |


## 11 验收结论

### 11.1 AUTH 验收结论

| 验收项 | 结论 | 说明 |
| --- | --- | --- |
| 功能覆盖 | 通过 | `FR-UA-01 ~ FR-UA-07` 均有自动化覆盖，核心登录、权限、密码、审计和安全分支已验证 |
| 接口覆盖 | 通过 | `API-AUTH-01 ~ API-AUTH-17` 的主要路由、请求、权限、错误和响应由后端/前端自动化覆盖 |
| 页面覆盖 | 通过 | Vue 单测覆盖主要页面状态和交互，2026-06-12 本地 DEV 浏览器完成学生、教师、管理员登录与手工矩阵 |
| 数据一致性 | 通过 | `DB-AUTH-01 ~ DB-AUTH-07` 迁移语法、唯一约束、会话和审计关键字段已由自动化覆盖 |
| 权限与安全 | 通过 | Bearer 鉴权、header-only 拒绝、越权拒绝、伪造 token、账号禁用/锁定、敏感信息保护均有自动化覆盖 |
| 非功能 | 有条件通过 | 安全性、可靠性、可用性、可测试性已覆盖核心场景；本地性能/压力批次已补充，专项安全扫描和生产实流量长稳仍需复核 |
| 最终结论 | 有条件通过 | 当前文档可交给测试负责人整合；本地手工矩阵已补，仍需 FAT/UAT 留痕、专项安全扫描和生产实流量长稳复核 |

### 11.2 CRS 验收结论

| 验收项       | 结论 | 说明                                                         |
| ------------ | ---- | ------------------------------------------------------------ |
| 功能覆盖     | 通过 | CRS 的课程、选课、章节、资源、成员、公告均有自动化覆盖；其他模块核心单元测试通过 |
| 接口覆盖     | 通过 | CRS 后端相关接口、权限和异常矩阵通过 MockMvc 验证            |
| 前端覆盖     | 通过 | CRS 页面 18 条单测通过；本地 DEV 浏览器确认学生/教师课程中心、课程详情和角色入口 |
| 数据一致性   | 通过 | CRS 课程、成员、章节、资源、公告及 LRN 近期任务关联在测试中验证 |
| 非功能与安全 | 有条件通过 | 已覆盖基础分页、权限、文件限制和异常映射；本地安全探针和浏览器 smoke 已补，生产扫描与长稳待复核 |
| 最终结论     | 有条件通过 | CRS 自动化、本地联调和本地浏览器证据通过，最终验收环境记录待补 |

### 11.3 LRN 验收结论

| 验收维度 | 结论 | 依据 | 残余风险/后续动作 |
| --- | --- | --- | --- |
| 功能完整性 | 通过 | `TC-LN-01 ~ TC-LN-06` 均有可执行用例，后端和前端目标测试通过，2026-06-12 本地浏览器补充断点、通知、提醒真实操作 | 跨模块生产事件仍需验收环境复核 |
| 接口契约 | 通过 | `API-LRN-01 ~ API-LRN-11` 均有后端或前端测试覆盖 | 内部事件接口真实部署 token/IP 白名单策略需环境确认 |
| 数据表和状态 | 通过 | `DB-LRN-01 ~ DB-LRN-07` 迁移和状态日志测试通过 | 生产库迁移顺序需在统一部署脚本中再次确认 |
| 权限和隔离 | 通过 | 自动化覆盖 Bearer 登录态、课程成员、教师聚合和当前用户通知隔离 | 真实账号矩阵需手工复验 |
| 异常和边界 | 通过 | 自动化覆盖未登录、非成员、非法参数、限流、内部 token 错误、提醒失败日志 | 高并发下真实网络波动需联调环境补充 |
| 非功能 | 有条件通过 | 自动化覆盖分页、size 上限、幂等、离线队列、状态日志和失败重试；2026-06-12 本地压测批次通过 | 真实通知触达时延需联调计时 |
| 测试文档交付 | 有条件通过 | 本文件按 TST-DOC-01 和各模块负责人任务整理 6.3、7.3、8.3、9.3、10、11.3 内容，并补充 13.7 本地手工矩阵 | 交由 @MontesquieuE 统一整合时需补充最终 FAT/UAT 实测结果 |

### 11.4 LAB 验收结论

| 验收项 | 结论 | 说明 |
| --- | --- | --- |
| 功能覆盖 | 通过 | `FR-LAB-01 ~ FR-LAB-08` 均已建立追踪，并由后端/前端自动化测试覆盖核心行为 |
| 接口覆盖 | 通过 | `API-LAB-01 ~ API-LAB-18` 已映射到控制器测试与前端 API wrapper，并完成目标命令验证 |
| 页面覆盖 | 通过 | 教师端、学生端、历史页、统计面板的关键交互已由 29 条前端单测覆盖；2026-06-12 本地 DEV 浏览器补充创建、发布、提交、评分和结果查看 |
| 数据一致性 | 通过 | `DB-LAB-01 ~ DB-LAB-07` 已由迁移、事务、评分留痕和来源成绩样本测试覆盖 |
| 权限与安全 | 通过 | 非成员访问、越权评分、他人提交查看、隐藏用例保护和发布前结果隐藏均已建立自动化验证 |
| 非功能 | 有条件通过 | 可靠性、可追踪性、安全性、可测试性已有自动化证据；本地性能压测和真实 Docker 基础专项已补充，完整页面端到端仍需验收 |
| 最终结论 | 有条件通过 | LAB 测试文档已按统一模板完成并通过目标自动化和本地 DEV 浏览器验证；剩余工作集中在 FAT/UAT 留痕和生产级环境复核 |

### 11.5 HWK 验收结论

| 验收项 | 结论 | 说明 |
| --- | --- | --- |
| 功能覆盖 | 通过 | FR-HWK-01 ~ FR-HWK-06 均有自动化覆盖，2026-06-12 本地 DEV 浏览器补充作业创建、发布、提交、批阅和发布成绩 |
| 接口覆盖 | 通过 | API-HWK-01 ~ API-HWK-22 的主路由、权限和错误分支由后端/前端自动化覆盖；API-HWK-22 真实 DELETE 200/deleted=true |
| 页面覆盖 | 通过 | Vue 单测覆盖删除确认/pending/失败/末页回退；MAN-HWK-011 完成 1440×900/390×844 响应式验证 |
| 数据一致性 | 通过 | DB-HWK-01 ~ DB-HWK-07 关键约束、父表原子软删、普通更新防复活和子历史保留通过 |
| 权限与安全 | 通过 | 非成员、他人提交、隐藏用例、私有日志、未发布成绩均有自动化覆盖 |
| 非功能 | 有条件通过 | 可靠性、可追踪性、安全性、可测试性已覆盖；本地压测和真实 Docker 基础专项已补充，完整页面端到端仍需验收 |
| 最终结论 | 有条件通过 | #224 后端 290 tests、前端 511 tests、typecheck/build 与 MAN-HWK-011 通过；生产 MySQL/FAT/UAT 留痕仍按环境项补充 |

### 11.6 GRD 验收结论

| 验收项 | 结论 | 说明 |
| --- | --- | --- |
| 功能覆盖 | 通过 | FR-GR-01 ~ FR-GR-07 均有自动化覆盖，2026-06-12 本地 DEV 浏览器补充成绩表、同步重算、发布可见、成绩异议复核闭环 |
| 接口覆盖 | 通过 | API-GRD-01 ~ API-GRD-21 的主路由、权限、错误分支和响应结构由后端/前端自动化覆盖 |
| 页面覆盖 | 通过 | Vue 单测覆盖主要页面状态和交互，2026-06-12 本地 DEV 浏览器补充教师成绩表、教学分析、学生成绩页和复核处理 |
| 数据一致性 | 通过 | DB-GRD-01 ~ DB-GRD-08 的关键持久化、状态、日志和快照由迁移/服务测试覆盖 |
| 权限与安全 | 通过 | 教师课程权限、学生本人过滤、未发布不可见、无权限复核等分支均有自动化覆盖 |
| 非功能 | 有条件通过 | 可靠性、可追踪性、安全性、可测试性已覆盖；本地性能压测和本地跨模块联调已补充，生产实流量长稳待确认 |
| 最终结论 | 有条件通过 | 当前文档可交给测试负责人整合；MAN-GRD-001 ~ MAN-GRD-010 本地 DEV 验收记录已补，FAT/UAT 留痕待补 |


## 12 附录

### 12.1 AUTH 附录

#### 12.1.1 执行命令

```bash
cd /Users/xigma/Library/CloudStorage/OneDrive-个人/github/OnlineJudge/backend
mvn test -Dtest=AuthControllerTest,AuthAdminControllerTest,AuthMigrationScriptTest,HeaderCurrentUserProviderTest,AuthCrsIntegrationTest

cd /Users/xigma/Library/CloudStorage/OneDrive-个人/github/OnlineJudge/frontend
node node_modules/vitest/vitest.mjs run tests/unit/auth/authApi.spec.ts tests/unit/api/http.spec.ts tests/unit/auth/AuthProfileView.spec.ts tests/unit/auth/AuthView.spec.ts tests/unit/auth/AuthAdminView.spec.ts --pool=threads
```

#### 12.1.2 本次执行摘要

| 项目 | 摘要 |
| --- | --- |
| 后端 AUTH 自动化测试 | 5 个测试类，36 passed / 0 failed / 0 errors / 0 skipped |
| 前端 AUTH/API 自动化测试 | 5 files passed / 24 tests passed |
| 自动化覆盖 | 注册登录、会话、当前用户、退出、角色权限、账号状态、资料密码、失败锁定、审计日志、迁移约束、Bearer 鉴权、AUTH/CRS 联动 |
| 手工/联调状态 | 自动化、本地 HTTP 安全探针、本地浏览器 smoke 和 2026-06-12 本地 DEV 手工矩阵已补充；生产级安全扫描、生产长稳和 FAT/UAT 留痕待补 |

### 12.2 CRS 附录

#### 12.2.1 执行命令

```powershell
cd D:\OnlineJudgeForSE\OnlineJudgeForSE\backend
mvn "-Dtest=CourseControllerTest,AuthCrsIntegrationTest,HeaderCoursePermissionClientTest" test

cd D:\OnlineJudgeForSE\OnlineJudgeForSE\frontend
npm run test:unit
```

#### 12.2.2 本次执行摘要

| 项目 | 摘要 |
| --- | --- |
| 后端 CRS 相关测试 | 31 passed / 0 failed / 0 errors / 0 skipped |
| 前端测试 | 32 files passed / 170 tests passed |
| CRS 性能样本 | 课程列表 42ms，资源列表 20ms，样本规模 105/105 |
| 手动测试状态 | 本地 DEV 联调和浏览器矩阵通过；FAT/UAT 验收记录待补 |

### 12.3 LRN 附录

#### 12.3.1 后端目标测试命令

```bash
cd backend
mvn -q "-Dtest=LearningTaskControllerTest,LearningTaskMigrationTest,LearningTaskDefaultConfigurationTest,LearningProgressControllerTest,LearningProgressMigrationTest,LearningRecordControllerTest,LearningRecordMigrationTest,NotificationControllerTest,NotificationMigrationTest,ReminderRuleControllerTest,ReminderRuleFailureLoggingTest,ReminderRuleServiceTest,ReminderRuleMigrationTest,GrdLrnIntegrationTest,IntDemoDataInitializerTest" test
```

#### 12.3.2 前端目标测试命令

```bash
cd frontend
npm run test:unit -- tests/unit/lrn/LearningTaskCenterView.spec.ts tests/unit/lrn/LearningProgressView.spec.ts tests/unit/lrn/LearningStatisticsView.spec.ts tests/unit/lrn/NotificationCenterView.spec.ts tests/unit/lrn/ReminderRuleSettingsView.spec.ts tests/unit/lrn/learningTasksApi.spec.ts tests/unit/lrn/learningProgressApi.spec.ts tests/unit/lrn/learningRecordsApi.spec.ts tests/unit/lrn/notificationsApi.spec.ts tests/unit/lrn/reminderRulesApi.spec.ts tests/unit/CourseManagementView.spec.ts tests/unit/lab/LabStudentView.spec.ts tests/unit/hwk/HomeworkStudentView.spec.ts
```

#### 12.3.3 LRN 页面和接口快速索引

| 页面 | 前端页面文件 | 主要接口 |
| --- | --- | --- |
| UI-LRN-01 学习任务中心页 | `frontend/src/views/lrn/LearningTaskCenterView.vue` | `GET /api/v1/learning/tasks` |
| UI-LRN-02 学习进度页 | `frontend/src/views/lrn/LearningProgressView.vue` | `GET /api/v1/learning/progress`、`POST /api/v1/learning/progress` |
| UI-LRN-03 学习行为仪表盘 | `frontend/src/views/lrn/LearningStatisticsView.vue` | `GET /api/v1/learning/statistics`、`POST /api/v1/learning/records` |
| UI-LRN-04 消息通知中心页 | `frontend/src/views/lrn/NotificationCenterView.vue` | `GET /api/v1/notifications`、`PUT /api/v1/notifications/read`、`DELETE /api/v1/notifications/{notificationId}`、`POST /api/v1/notifications/events` |
| UI-LRN-05 提醒规则设置页 | `frontend/src/views/lrn/ReminderRuleSettingsView.vue` | `GET /api/v1/reminder-rules`、`PUT /api/v1/reminder-rules` |

#### 12.3.4 交付说明

测试负责人整合时可直接抽取本文件的 `6.3`、`7.3`、`8.3`、`9.3`、`10`、`11.3` 和 `12.3` 小节合入总测试报告，并在 FAT/UAT 后补充手工测试实际结果、缺陷编号和最终审批记录。

### 12.4 LAB 附录

#### 12.4.1 推荐执行命令

```powershell
cd C:\Users\李世旺\Desktop\Temp\LESSON\软工基础\大作业\OJSE\OnlineJudge\backend
mvn test "-Dtest=LabExperimentControllerTest,LabSubmissionControllerTest,LabExperimentMigrationTest,LabExperimentTransactionTest,LabEvaluationServiceTest"

cd C:\Users\李世旺\Desktop\Temp\LESSON\软工基础\大作业\OJSE\OnlineJudge\frontend
npm run test:unit -- tests/unit/api/labs.spec.ts tests/unit/lab/LabTeacherView.spec.ts tests/unit/lab/LabStudentView.spec.ts tests/unit/lab/LabSubmissionHistoryView.spec.ts
```

#### 12.4.2 文档校验

```powershell
git diff --check
```

### 12.5 HWK 附录

#### 12.5.1 执行命令

```bash
cd /Users/xigma/Library/CloudStorage/OneDrive-个人/github/OnlineJudge/backend
mvn test -Dtest=HomeworkControllerTest,HomeworkBearerAuthControllerTest,HomeworkMigrationTest,HomeworkSubmissionServiceTest

cd /Users/xigma/Library/CloudStorage/OneDrive-个人/github/OnlineJudge/frontend
node node_modules/vitest/vitest.mjs run tests/unit/hwk/homeworksApi.spec.ts tests/unit/hwk/HomeworkStudentListView.spec.ts tests/unit/hwk/HomeworkStudentView.spec.ts tests/unit/hwk/HomeworkSubmissionHistoryView.spec.ts tests/unit/hwk/HomeworkTeacherView.spec.ts --pool=threads
```

#### 12.5.2 本次执行摘要

| 项目 | 摘要 |
| --- | --- |
| 后端 HWK 自动化测试 | 4 个测试类，44 passed / 0 failed / 0 errors / 0 skipped |
| 前端 HWK 自动化测试 | 5 files passed / 28 tests passed |
| 自动化覆盖 | 作业创建/发布、提交、历史、自动评测、重评、批阅、统计、权限、隐藏数据、迁移约束、AUTH/CRS 联动 |
| 手工/联调状态 | 2026-06-12 本地 DEV 已补真实浏览器作业创建、发布、提交、批阅、发布成绩和 GRD 同步记录；生产/FAT/UAT 留痕待补 |

### 12.6 GRD 附录

#### 12.6.1 执行命令

```bash
cd /Users/xigma/Library/CloudStorage/OneDrive-个人/github/OnlineJudge/backend
mvn test -Dtest=GradeItemControllerTest,GradeRecordControllerTest,GradeItemMigrationTest,GradeAnalysisServiceTest,GradeItemServiceTest,GradeRecordServiceTest,GradeReviewServiceTest

cd /Users/xigma/Library/CloudStorage/OneDrive-个人/github/OnlineJudge/frontend
node node_modules/vitest/vitest.mjs run tests/unit/grd/gradeItemsApi.spec.ts tests/unit/grd/gradeRecordsApi.spec.ts tests/unit/grd/GradeItemConfigView.spec.ts tests/unit/grd/TeacherGradeTableView.spec.ts tests/unit/grd/App.spec.ts src/views/grd/StudentGradeView.spec.ts --pool=threads
```

#### 12.6.2 本次执行摘要

| 项目 | 摘要 |
| --- | --- |
| 后端 GRD 自动化测试 | 7 个测试类，49 passed / 0 failed / 0 errors / 0 skipped |
| 前端 GRD 自动化测试 | 6 files passed / 49 tests passed |
| 自动化覆盖 | 成绩项、规则校验、来源同步、总评计算、成绩调整、发布、学生查询、教学分析、异议复核、权限、安全、日志、快照 |
| 手工/联调状态 | 2026-06-12 本地 DEV 已补真实浏览器成绩表、来源成绩同步、通知跳转和成绩复核记录；生产规模性能和 FAT/UAT 留痕待补 |

## 13 整合一致性检查与最终确认项

### 13.1 编号与追溯一致性检查

| 检查项 | 检查结论 | 说明 |
| --- | --- | --- |
| 文档结构 | 通过 | 按 TST-DOC-01 骨架组织，6 至 13 章按测试数据、用例、日志、手工、风险、验收、附录、整合记录依次编号 |
| 需求编号 | 通过 | 六个模块分别使用 FR-UA/CR/LN/LAB/HWK/GR 与 NFR 前缀，未发现跨模块冲突 |
| 页面编号 | 通过 | UI-AUTH、UI-CRS、UI-LRN、UI-LAB、UI-HWK、UI-GRD 均在模块测试文档中建立用例映射 |
| 接口编号 | 通过 | API-AUTH、API-CRS、API-LRN、API-LAB、API-HWK、API-GRD 均由后端或前端 API 测试覆盖 |
| 数据库编号 | 通过 | DB-AUTH、DB-CRS、DB-LRN、DB-LAB、DB-HWK、DB-GRD 均有迁移脚本或持久化测试依据 |
| 测试用例编号 | 通过 | TC-UA、TC-CR、TC-LN、TC-LAB、TC-HWK、TC-GR 与手工 MAN 编号均已汇总 |
| 设计编号 | 通过 | 已明确列出 DSD 编号 |
| 状态口径 | 通过 | 本底稿统一使用“通过 / 有条件通过 / 本地通过，待验收环境复核 / 待专项测试 / 待验收环境复核 / 待审批” |

### 13.2 跨模块链路人工验收补充用例

| 用例编号 | 链路 | 关联模块 | 验收要点 | 当前状态 |
| --- | --- | --- | --- | --- |
| E2E-INT-001 | 登录 -> 课程 | AUTH/CRS | 教师和学生使用 Bearer 登录态进入课程，课程成员和管理权限正确 | 通过；2026-06-12 本地 DEV 浏览器覆盖学生、教师、管理员登录和课程中心/课程详情入口 |
| E2E-INT-002 | 课程 -> 作业 -> 提交/评测 | AUTH/CRS/HWK/LRN | 教师发布作业，学生提交，评测/批阅完成，LRN 生成任务和通知 | 通过；2026-06-12 本地 DEV 创建作业 `950302`、学生提交 `950304`、教师批阅 90 分、发布成绩 `SCORE_PUBLISHED` |
| E2E-INT-003 | 课程 -> 实验 -> 提交/评测 | AUTH/CRS/LAB/LRN | 教师发布实验，学生提交代码/报告，评测和评分完成，LRN 生成任务和通知 | 通过；2026-06-12 本地 DEV 创建实验 `950202`、学生提交 `950204`、系统返回评测结果、教师评分 86 分 |
| E2E-INT-004 | 作业/实验 -> 成绩 | LAB/HWK/GRD | LAB/HWK 发布成绩后，GRD 同步来源成绩并计算总评 | 通过；2026-06-12 本地 DEV 执行 GRD 同步和重算，`syncedCount=2`、`affectedStudentCount=1`、`affectedCount=1` |
| E2E-INT-005 | 成绩 -> 通知 | GRD/LRN | 教师发布成绩或处理异议后，学生在通知中心看到对应通知并可跳转 | 通过；2026-06-12 本地 DEV 通知详情可跳转成绩相关课程页，成绩异议从 `PENDING` 流转到 `APPROVED` |
| E2E-INT-006 | 全链路 | AUTH/CRS/LAB/HWK/GRD/LRN | 登录 -> 课程 -> 作业/实验 -> 提交/评测 -> 成绩 -> 通知一次性闭环 | 通过；2026-06-12 本地 DEV 完成三角色登录、课程、学习任务、实验提交、作业提交、教师评分批阅、成绩同步、通知跳转和成绩复核闭环 |


### 13.3 2026-06-12 自动化补测执行记录

| 执行编号 | 时间 | 范围 | 命令 | 执行结果 | 结论 |
| --- | --- | --- | --- | --- | --- |
| TST08-AUTO-001 | 2026-06-12 00:10 ~ 00:11 | 后端完整自动化测试 | `cd backend; mvn test` | `Tests run: 251, Failures: 0, Errors: 0, Skipped: 1`，构建成功 | 通过 |
| TST08-AUTO-002 | 2026-06-12 00:10 | 前端完整单元测试 | `cd frontend; npm run test:unit` | `Test Files 33 passed (33)`，`Tests 185 passed (185)` | 通过 |
| TST08-AUTO-003 | 2026-06-12 12:44 | 后端完整自动化测试复跑 | `cd backend && mvn test` | `Tests run: 251, Failures: 0, Errors: 0, Skipped: 1`，构建成功 | 通过 |
| TST08-AUTO-004 | 2026-06-12 12:44 | 前端完整单元测试复跑 | `cd frontend && npm run test:unit` | `Test Files 33 passed (33)`，`Tests 185 passed (185)` | 通过 |
| TST08-AUTO-005 | 2026-06-12 14:20 | AUTH/HWK 后端目标测试复跑 | `cd backend && mvn test -Dtest=AuthControllerTest,AuthAdminControllerTest,AuthMigrationScriptTest,HeaderCurrentUserProviderTest,AuthCrsIntegrationTest,HomeworkControllerTest,HomeworkBearerAuthControllerTest,HomeworkMigrationTest,HomeworkSubmissionServiceTest` | `Tests run: 80, Failures: 0, Errors: 0, Skipped: 0`，构建成功；覆盖旧 `AUTH-LOG-001`、`AUTH-LOG-002`、`HWK-LOG-001` | 通过 |
| TST08-AUTO-006 | 2026-06-12 14:21 | AUTH/HWK 前端目标测试复跑 | `cd frontend && node node_modules/vitest/vitest.mjs run tests/unit/auth/authApi.spec.ts tests/unit/api/http.spec.ts tests/unit/auth/AuthProfileView.spec.ts tests/unit/auth/AuthView.spec.ts tests/unit/auth/AuthAdminView.spec.ts tests/unit/hwk/homeworksApi.spec.ts tests/unit/hwk/HomeworkStudentListView.spec.ts tests/unit/hwk/HomeworkStudentView.spec.ts tests/unit/hwk/HomeworkSubmissionHistoryView.spec.ts tests/unit/hwk/HomeworkTeacherView.spec.ts --pool=threads` | `Test Files 10 passed (10)`，`Tests 52 passed (52)`；覆盖旧 `HWK-LOG-007` | 通过 |
| TST08-AUTO-007 | 2026-06-12 14:21 | 后端完整自动化测试复跑 | `cd backend && mvn test` | `Tests run: 252, Failures: 0, Errors: 0, Skipped: 1`，构建成功 | 通过 |
| TST08-AUTO-008 | 2026-06-12 14:21 | 前端完整单元测试复跑 | `cd frontend && npm run test:unit` | `Test Files 33 passed (33)`，`Tests 186 passed (186)` | 通过 |

说明：后端完整自动化中的 1 条跳过项来自真实 Docker 沙箱环境保护开关；Docker daemon 启动后已在第 13.5 节使用 `OJ_DOCKER_SANDBOX_TEST=true` 单独补跑该用例。

### 13.4 2026-06-12 压测补测执行记录

| 执行编号 | 时间 | 范围 | 命令 | 执行结果 | 结论 |
| --- | --- | --- | --- | --- | --- |
| TST08-PERF-001 | 2026-06-12 00:18 | AUTH 登录、权限、用户管理和审计分页基础压力样本 | `cd backend; mvn "-Dtest=AuthControllerTest,AuthAdminControllerTest,CourseControllerTest,LearningTaskControllerTest,NotificationControllerTest,LabExperimentControllerTest,LabSubmissionControllerTest,HomeworkControllerTest,GradeRecordControllerTest,GradeAnalysisServiceTest" test` | 本批次合计 `Tests run: 154, Failures: 0, Errors: 0, Skipped: 0`，构建成功 | 通过 |
| TST08-PERF-002 | 2026-06-12 00:18 | CRS 课程/资源基础规模列表响应 | 同 `TST08-PERF-001` | 输出 `CRS_PERF courseListMs=17 resourceListMs=51 courses=105 resources=105` | 通过 |
| TST08-PERF-003 | 2026-06-12 00:18 | LRN 学习任务、通知列表分页、size 上限和未读统计 | 同 `TST08-PERF-001` | `LearningTaskControllerTest`、`NotificationControllerTest` 随批次通过 | 通过 |
| TST08-PERF-004 | 2026-06-12 00:18 | LAB 实验、提交、统计接口基础性能样本 | 同 `TST08-PERF-001` | `LabExperimentControllerTest`、`LabSubmissionControllerTest` 随批次通过 | 通过 |
| TST08-PERF-005 | 2026-06-12 00:18 | HWK 作业列表、提交列表、统计分页基础性能样本 | 同 `TST08-PERF-001` | `HomeworkControllerTest` 随批次通过 | 通过 |
| TST08-PERF-006 | 2026-06-12 00:18 | GRD 教师总表、学生个人成绩、教学分析基础性能样本 | 同 `TST08-PERF-001` | `GradeRecordControllerTest`、`GradeAnalysisServiceTest` 随批次通过 | 通过 |
| TST08-PERF-007 | 2026-06-12 00:20 / 00:26 | LAB/HWK 真实 Docker 沙箱压测环境检查 | `docker --version`; `docker info --format '{{.ServerVersion}}'` | 初次检查 Docker CLI 可用但 daemon 未运行；用户启动 Docker 后 `docker info` 返回 Server Version `29.3.1` | 通过 |

说明：`TST08-PERF-001 ~ TST08-PERF-006` 完成文档中 AUTH、CRS、LRN、LAB、HWK、GRD 已有本地可执行性能/压力样本的补测。`TST08-PERF-007` 确认 Docker daemon 恢复后真实沙箱环境可用，后续专项样本见第 13.5 节。

### 13.5 2026-06-12 真实 Docker 沙箱专项补测记录

| 执行编号 | 时间 | 范围 | 命令/样本 | 执行结果 | 结论 |
| --- | --- | --- | --- | --- | --- |
| TST08-DOCKER-001 | 2026-06-12 00:26 | Docker daemon 环境确认 | `docker info --format '{{.ServerVersion}}'` | 返回 `29.3.1` | 通过 |
| TST08-DOCKER-002 | 2026-06-12 00:27 | Docker 执行器真实容器 smoke，首次冷启动 | `OJ_DOCKER_SANDBOX_TEST=true mvn -Dtest=DockerSandboxExecutorTest test` | 因未预拉取 `python:3.12-alpine`，容器启动被 3000ms 用例时限计为 `TIME_LIMIT_EXCEEDED` | 环境预热问题，已处理 |
| TST08-DOCKER-003 | 2026-06-12 00:29 | Docker 执行器真实容器 smoke，镜像预热后复测 | `docker pull python:3.12-alpine`; `OJ_DOCKER_SANDBOX_TEST=true mvn -Dtest=DockerSandboxExecutorTest test` | `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`，构建成功，耗时 2.770s | 通过 |
| TST08-DOCKER-004 | 2026-06-12 00:30 | AC 样本 | 真实 Docker 容器执行 Python 求和，网络隔离、内存、CPU、pids、只读和 tmpfs 参数启用 | 输出 `5`，退出码 `0` | 通过 |
| TST08-DOCKER-005 | 2026-06-12 00:30 | 编译错误样本 | Python `compile('def broken(: pass', 'Main.py', 'exec')` | 返回 `SyntaxError`，退出码 `1` | 通过 |
| TST08-DOCKER-006 | 2026-06-12 00:30 | 运行错误样本 | Python `raise RuntimeError('boom')` | 返回 `RuntimeError: boom`，退出码 `1` | 通过 |
| TST08-DOCKER-007 | 2026-06-12 00:30 | 内存限制样本 | 32MB 容器内分配 256MB 字符串 | 退出码 `137`，容器被内存限制终止 | 通过 |
| TST08-DOCKER-008 | 2026-06-12 00:31 | 并发样本 | 4 个 Docker 容器并发执行 Python 求和 | 输出 `2/4/6/8`，`JOB1_EXIT=0`、`JOB2_EXIT=0`、`JOB3_EXIT=0`、`JOB4_EXIT=0` | 通过 |
| TST08-DOCKER-009 | 2026-06-12 00:32 | 超时清理样本 | 长跑 Python 容器运行 3 秒后按测试时限清理 | 输出 `TLE_TIMEOUT_ENFORCED=True`，测试容器清理后无残留 | 通过 |
| TST08-DOCKER-010 | 2026-06-12 12:45 | Docker 执行器真实容器复跑 | `OJ_DOCKER_SANDBOX_TEST=true mvn -Dtest=DockerSandboxExecutorTest test` | `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`，构建成功 | 通过 |
| TST08-DOCKER-011 | 2026-06-12 14:24 | Docker 执行器真实容器复跑 | `OJ_DOCKER_SANDBOX_TEST=true mvn -Dtest=DockerSandboxExecutorTest test` | `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`，构建成功，耗时 4.212s | 通过 |

说明：本节补齐真实 Docker 沙箱基础专项，覆盖执行器真实容器 AC smoke、编译错误、运行错误、内存限制、超时清理和并发启动。当前 Docker 执行器配置镜像为 `python:3.12-alpine`，因此本次自动化专项覆盖 Python。

### 13.6 2026-06-12 真实服务闭环与浏览器 smoke 复核记录

| 执行编号 | 环境 | 范围 | 执行结果 | 结论 |
| --- | --- | --- | --- | --- |
| TST08-HTTP-001 | 干净 H2 后端 `http://127.0.0.1:18080` | 登录学生/教师/管理员、课程 9501、学习任务 EXPERIMENT/HOMEWORK/RESOURCE、LAB 950201、HWK 950301、提交 950303 评测、个人成绩、通知、教师成绩表 | 学生 `student001` 登录成功；课程成员可见；任务类型为 `EXPERIMENT`、`HOMEWORK`、`RESOURCE`；作业评测 `ACCEPTED`；总评 `89.6`；通知包含 `GRD`、`HWK`、`LAB` | 通过 |
| TST08-SEC-001 | 干净 H2 后端本地安全探针 | 匿名 `/api/v1/auth/me`、伪造 `X-User-*` Header 访问课程详情、伪造 Bearer、学生访问管理用户接口 | 匿名返回 401；伪造 Header 返回 401；无效 Bearer 返回 401；学生访问管理接口返回 403 | 通过 |
| TST08-BROWSER-001 | 本地前端 `http://127.0.0.1:5173` | 过期会话页、学生登录、课程中心、学习任务中心、通知中心 | 页面显示过期会话提示；`student001` 登录成功；课程页可见“数据结构全流程演示课”；学习任务页可见实验、作业、资源；通知页可见 LAB/HWK/GRD 相关通知 | 通过 |
| TST08-LOCALSTATE-001 | 本地文件库后端 `http://127.0.0.1:8080` | 旧文件库演示数据一致性抽查 | `/api/v1/submissions/950303/evaluation` 曾返回 500；同链路切换干净 H2 内存库后通过 | 待验收环境复核 |

### 13.7 2026-06-12 本地 DEV 浏览器手工矩阵复核记录

| 执行编号 | 范围 | 操作证据 | 结论 |
| --- | --- | --- | --- |
| TST08-MANUAL-001 | 执行环境与账号 | 后端 `http://127.0.0.1:8080` 使用干净 H2 内存库 `tst08_manual`，前端 `http://127.0.0.1:5173`；账号覆盖 `student001`、`teacher001`、`admin001`、新注册学生 `manual458863` | 通过 |
| TST08-MANUAL-002 | AUTH 注册、资料、密码和管理员 | 注册 `manual458863` 成功，重复注册同账号显示“账号已存在”；资料保存成功；改密后旧密码登录失败、新密码登录成功；管理员新增教师 `adminmade512881` 并在审计日志中看到登录、改密、创建用户记录 | 通过 |
| TST08-MANUAL-003 | LRN 学习进度、通知和提醒 | 学生从学习进度页点击“继续学习”跳转到 `/courses/9501/homeworks/950301?role=student&resume=homeworkId%3D950301` 并显示“已恢复上次断点”；通知详情跳转课程页；全部已读使未读数 4 -> 0；删除后通知数 4 -> 3；提醒保存显示“提醒规则已保存” | 通过 |
| TST08-MANUAL-004 | LAB 创建、提交、评测和评分 | 教师创建并发布实验 `950202`；学生提交生成 `950204`，页面显示“提交成功，版本 1”和评测结果；教师评分人工分 86、最终分 86；学生端可见最终得分、人工评分和教师评语 | 通过 |
| TST08-MANUAL-005 | HWK 创建、提交、批阅和发布 | 教师创建并发布作业 `950302`；学生提交生成 `950304`，页面显示 `SUBMITTED / NONE / UNREVIEWED`；教师批阅人工分 90、最终分 90；成绩发布后学生作业页显示 `SCORE_PUBLISHED` | 通过 |
| TST08-MANUAL-006 | GRD 来源同步、教学分析和成绩可见 | 教师执行成绩同步和重算，返回 `syncedCount=2`、`missingCount=0`、`ungradedCount=0`、`affectedCount=1`；教师成绩分析显示均分 89.6、及格率 100%、完成率 100%；学生成绩页可见总评 | 通过 |
| TST08-MANUAL-007 | GRD 成绩异议复核 | 学生提交课程总评异议后页面显示 `PENDING`；教师端显示待处理复核，填写调整后成绩 90 并同意；教师端显示“复核已处理：APPROVED”，学生端复核记录显示 `APPROVED` 和处理说明，课程总评显示 90、状态 `ADJUSTED` | 通过 |
| TST08-MANUAL-008 | LRN 筛选分页与权限边界追加复核 | `taskType=HOMEWORK&page=0&size=1` 返回 `total=1`、`size=1`、记录类型 `HOMEWORK`；非成员 `manualboundary1104` 查询课程任务返回 0，查询课程进度和统计返回 403；学生访问教师聚合返回 403，教师访问聚合返回 200 且学生数为 1 | 通过 |
| TST08-MANUAL-009 | LAB 实验报告上传下载追加复核 | 在干净 H2 `tst08_manual_followup` 创建需报告实验 `950202`，学生提交 `950204` 后上传 `tst08-report.pdf` 得到报告 `1`、版本 `1`；下载文件与上传内容一致，教师报告评分 28 分和评语保存成功 | 通过 |
| TST08-MANUAL-010 | 保留的环境级事项 | 生产级安全扫描、生产实流量长稳、FAT/UAT 正式账号截图或测试记录编号不属于本地 DEV 可证明范围；当前 Docker 执行器实现仅支持 Python，Java 多语言端到端需在最终环境或设计调整中确认 | 有条件通过 |
