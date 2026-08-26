# TST-DOC-02 AUTH 用户权限与平台安全测试文档

| 文档编号 | TST-DOC-02 |
| --- | --- |
| 文档名称 | AUTH 用户权限与平台安全测试文档 |
| 项目名称 | 在线教学与实训平台 |
| 所属阶段 | 系统测试与验收测试 |
| 报告版本 | V1.0 |
| 编写日期 | 2026-06-10 |
| 编写人 | AUTH 模块负责人 |
| 对应 issue | #153 TST-DOC-02 AUTH 用户权限与平台安全测试文档编写 |
| 测试范围 | AUTH 用户注册登录、会话、当前用户、角色权限、账号资料、密码安全、账号状态、审计日志、平台安全拦截、跨模块鉴权契约 |
| 测试结论 | 自动化测试通过；真实浏览器端到端、专项安全扫描和全模块联调仍需测试负责人整合确认 |

## 1 文档控制

### 1.1 修订记录

| 版本 | 日期 | 修订人 | 修订说明 |
| --- | --- | --- | --- |
| V1.0 | 2026-06-10 | AUTH 模块负责人 | 按 #152 统一结构整理 AUTH 测试依据、范围、用例、自动化覆盖、执行日志、手工验收点和残余风险 |

### 1.2 审批记录

| 角色 | 姓名/负责人 | 审批意见 | 日期 |
| --- | --- | --- | --- |
| 项目负责人 | 待填写 | 待审批 | 2026-06-10 |
| 测试负责人 | @MontesquieuE | 待整合确认 | 2026-06-10 |
| AUTH 模块负责人 | AUTH 负责人 | 待确认 | 2026-06-10 |

## 2 测试概述

本文件用于记录 AUTH 用户权限与平台安全模块在当前版本下的测试依据、测试环境、测试数据、测试用例、执行结果、手工验收清单、缺陷风险和验收结论。覆盖范围对齐 `FR-UA-01 ~ FR-UA-07`、`NFR-UA-01 ~ NFR-UA-05`、`UI-AUTH-01 ~ UI-AUTH-11`、`API-AUTH-01 ~ API-AUTH-17`、`DB-AUTH-01 ~ DB-AUTH-07`、`TC-UA-01 ~ TC-UA-07` 与 `TC-UA-N01 ~ TC-UA-N05`。

当前已执行 AUTH 后端 Spring Boot 自动化测试和前端 Vue/Vitest 单元测试。自动化覆盖了公开注册、登录、退出、当前用户、Bearer 会话、令牌摘要存储、登录失败安全提示、过期/撤销/伪造令牌、通用权限校验、个人资料、修改密码、失败登录锁定、管理员用户角色和角色权限维护、账号禁用会话失效、审计日志查询、数据库迁移约束、共享 HTTP 鉴权拦截以及 AUTH/CRS 当前用户联动。真实浏览器完整端到端、专项漏洞扫描、生产规模并发和所有业务模块统一环境联调仍列为手工或专项验收项。

## 3 测试依据

| 序号 | 文档/代码依据 | 用途 |
| --- | --- | --- |
| 1 | `docs/开发/AUTH-用户权限与平台安全模块开发流程.md` | AUTH 主流程、P0 最短交付、页面/API/服务/数据表和自测清单依据 |
| 2 | `docs/最终提交/软件需求规格说明书.md` | FR-UA、NFR-UA 需求和验收来源 |
| 3 | `docs/最终提交/软件概要设计说明书.md` | 模块边界、跨模块协作、非功能约束和接口契约来源 |
| 4 | `docs/最终提交/软件详细设计说明书.md` | UI、API、数据库、异常、安全策略、测试编号和追踪矩阵来源 |
| 5 | `docs/过程/需求/用户权限与平台安全模块（后端负责）.md` | AUTH 过程需求、异常流程、验收标准和需求追踪补充 |
| 6 | `docs/过程/概要/用户权限与平台安全模块概要设计提交稿（AUTH）.md` | AUTH 概要设计、模块边界、组件划分、非功能和协作关系补充 |
| 7 | `docs/过程/详细设计/AUTH-用户权限与平台安全-详细设计提交稿.md` | AUTH 页面、API、服务、数据表、状态机、异常和测试关注点补充 |
| 8 | `backend/src/test/java/com/onlinejudge/auth` | AUTH 后端控制器、管理接口和迁移自动化测试实现 |
| 9 | `backend/src/test/java/com/onlinejudge/common/security`、`backend/src/test/java/com/onlinejudge/integration/AuthCrsIntegrationTest.java` | 当前用户上下文和 AUTH/CRS 跨模块鉴权自动化测试 |
| 10 | `frontend/tests/unit/auth`、`frontend/tests/unit/api/http.spec.ts` | AUTH 前端页面、API wrapper、共享 HTTP 鉴权和异常跳转自动化测试 |
| 11 | `database/migrations/DB-AUTH-01-auth-user-session.sql` | AUTH 七张数据表、索引、唯一约束和外键约束依据 |

## 4 测试范围

### 4.1 功能与非功能范围

| 编号 | 测试对象 | 主要验证点 | 当前覆盖状态 |
| --- | --- | --- | --- |
| FR-UA-01 | 用户注册与登录 | 注册学生账号、登录成功、登录失败安全提示、退出后令牌失效、获取当前用户 | 后端和前端自动化已覆盖 |
| FR-UA-02 | 角色管理与权限分配 | 管理员查询/创建用户、调整用户角色、创建/更新角色、调整角色权限、普通用户越权拒绝 | 后端和前端自动化已覆盖 |
| FR-UA-03 | 身份认证与访问控制 | Bearer 鉴权、`/me` 当前用户、通用权限校验、业务接口拒绝仅 header 伪造身份、AUTH/CRS 联动 | 后端和前端自动化已覆盖；全模块联调待确认 |
| FR-UA-04 | 账号信息与密码安全 | 个人资料读取/修改、联系方式校验、原密码校验、新密码哈希、旧会话撤销、登录失败锁定 | 后端和前端自动化已覆盖 |
| FR-UA-05 | 权限异常处理与安全提示 | 未登录、会话过期、会话撤销、伪造令牌、账号禁用/锁定、403 无权限、前端状态页跳转 | 后端和前端自动化已覆盖 |
| FR-UA-06 | 关键操作审计 | 登录成功/失败、越权访问、角色权限调整、账号状态变更、审计日志筛选查询、日志字段长度边界 | 后端自动化已覆盖；审计日志页面真实浏览器待确认 |
| FR-UA-07 | 平台基础安全防护 | 输入校验、敏感错误包装、不暴露密码/令牌、参数篡改拦截、业务资源归属二次校验契约 | 自动化覆盖核心安全分支；专项安全扫描待补充 |
| NFR-UA-01 | 安全性 | 密码非明文、令牌摘要存储、Bearer 认证、接口不信任前端身份、敏感信息不返回 | 自动化已覆盖 |
| NFR-UA-02 | 可靠性 | 登录、退出、禁用账号、密码修改、权限调整等关键操作保持主数据和会话状态一致 | 自动化已覆盖核心分支 |
| NFR-UA-03 | 可用性 | 登录/注册/资料/密码/管理页面状态、会话过期、账号异常和 403 提示 | 前端单测覆盖；真实浏览器待手工验收 |
| NFR-UA-04 | 性能 | 登录、鉴权、权限查询和审计日志分页基础响应 | 自动化覆盖分页和索引约束；生产规模压测待补充 |
| NFR-UA-05 | 可测试性 | 三类角色边界、登录成功失败、会话失效、越权、密码、角色调整、审计日志可重复验证 | 自动化已覆盖 |

### 4.2 页面、接口、数据表覆盖

| 类别 | 编号范围 | 覆盖说明 |
| --- | --- | --- |
| 页面 | `UI-AUTH-01 ~ UI-AUTH-11` | 前端单测覆盖登录、注册、个人资料、修改密码、管理员用户角色与角色权限管理、账号异常、会话过期和 403 跳转；安全审计日志页面真实浏览器筛选待手工确认 |
| 接口 | `API-AUTH-01 ~ API-AUTH-17` | 后端 MockMvc 和前端 API wrapper 覆盖主要路由、请求体、分页、权限、错误码、响应数据和 Bearer 认证 |
| 服务 | `SVC-AUTH-01 ~ SVC-AUTH-08` | 控制器测试通过登录、会话、密码、角色权限、审计、访问控制和当前用户上下文间接覆盖核心服务 |
| 数据表 | `DB-AUTH-01 ~ DB-AUTH-07` | 迁移测试覆盖 MySQL 兼容自增、时间戳语法；控制器测试覆盖用户、角色、权限、会话、审计日志关键约束 |
| 跨模块 | AUTH、CRS、HWK、LAB、GRD、LRN | AUTH/CRS 已有自动化联动；HWK Bearer 鉴权已在 HWK 文档覆盖；LAB/GRD/LRN 统一环境联调需测试负责人整合确认 |

### 4.3 不在本次自动化确认范围

| 范围项 | 说明 | 处理方式 |
| --- | --- | --- |
| 真实浏览器端到端验收 | 当前未执行从浏览器登录、切换角色、访问菜单、刷新会话、退出再访问的完整流程 | 作为手工验收用例 `MAN-AUTH-001 ~ MAN-AUTH-006` |
| 专项安全扫描 | 自动化覆盖认证、越权和敏感信息保护，但未执行 OWASP/ZAP 等漏洞扫描 | 作为专项测试 `MAN-AUTH-008` |
| 生产规模并发与压测 | 自动化覆盖分页和基础索引，未执行大量用户、会话、审计日志的并发压测 | 作为专项性能测试 `MAN-AUTH-009` |
| 全模块统一环境联调 | AUTH/CRS 自动化已通过，但 LAB/HWK/GRD/LRN 统一登录态闭环仍需整合验证 | 作为跨模块联调用例 `MAN-AUTH-010` |

## 5 测试环境

| 环境项 | 内容 |
| --- | --- |
| 操作系统 | Windows |
| 后端运行环境 | Java 25，Spring Boot 3.4.5，Maven 3.9.9，JUnit 5，MockMvc，H2 |
| 前端运行环境 | Node.js，Vue 3.5，Vite 6.3，Vitest 3.2，jsdom |
| 数据库 | 自动化测试使用 H2 内存库；迁移脚本按 MySQL 8.0 兼容约束编写 |
| 鉴权方式 | 后端测试使用 Bearer Token 与 AUTH 会话；共享安全测试覆盖 `X-User-Id`/`X-User-Role` 兼容上下文；前端测试 mock API wrapper 和浏览器存储 |
| 执行日期 | 2026-06-10 |

## 6 测试数据

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

## 7 测试用例汇总

### 7.1 自动化执行结果

| 测试类别 | 命令 | 执行结果 |
| --- | --- | --- |
| 后端 AUTH 相关测试 | `$env:JAVA_HOME='C:\Program Files\Java\jdk-25'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; & 'C:\Code\SE\.codex-tools\apache-maven-3.9.9\bin\mvn.cmd' "-Dtest=AuthControllerTest,AuthAdminControllerTest,AuthMigrationScriptTest,HeaderCurrentUserProviderTest,AuthCrsIntegrationTest" test` | 5 个测试类，34 条通过，0 失败，0 错误，0 跳过 |
| 前端 AUTH/API 单元测试 | `npm run test:unit -- tests/unit/auth/AuthView.spec.ts tests/unit/auth/AuthProfileView.spec.ts tests/unit/auth/AuthAdminView.spec.ts tests/unit/auth/authApi.spec.ts tests/unit/api/http.spec.ts --pool=threads` | 5 个测试文件，22 条通过 |

说明：后端目标测试首次直接执行 `mvn` 时本机未配置全局 Maven；改用仓库工具目录 Maven 后，因默认 `JAVA_HOME` 指向 JDK 15，无法运行 Java 21 class file。临时切换到 `C:\Program Files\Java\jdk-25` 后同一后端目标测试通过。

### 7.2 AUTH 核心用例表

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
| TC-UA-N03 | NFR-UA-03 | 页面提示和状态 | 登录/注册、资料/密码、管理页、账号异常、403、会话过期 | 触发页面成功、失败和异常状态 | 用户看到清晰反馈，不展示无权限管理入口 | 前端 22 条目标单测通过；真实浏览器待补充 | 有条件通过 |
| TC-UA-N04 | NFR-UA-04 | 高频接口和分页 | 用户列表、审计日志、权限查询、索引 | 查询分页列表和权限校验；检查迁移索引 | 分页参数可用，关键字段有索引，响应受控 | API 分页与迁移索引已覆盖；生产规模压测待补充 | 有条件通过 |
| TC-UA-N05 | NFR-UA-05 | 可测试性 | 稳定种子数据、H2、MockMvc、Vitest、jsdom | 重复执行后端和前端目标测试 | 核心安全场景可重复验证 | 本文第 8 章命令已通过 | 通过 |

### 7.3 前端 AUTH 用例摘要

| 测试文件 | 覆盖内容 | 结果 |
| --- | --- | --- |
| `frontend/tests/unit/auth/authApi.spec.ts` | `API-AUTH-01 ~ 17` 路由、方法、参数、Bearer 调用、登录态写入、审计日志筛选、权限校验、资料和密码接口 | 7 条通过 |
| `frontend/tests/unit/auth/AuthView.spec.ts` | 登录成功、角色入口展示、注册模式、后端校验失败反馈 | 2 条通过 |
| `frontend/tests/unit/auth/AuthProfileView.spec.ts` | 当前用户资料加载、资料修改、密码修改、密码确认前端校验 | 2 条通过 |
| `frontend/tests/unit/auth/AuthAdminView.spec.ts` | 管理员用户角色、角色权限和页面状态渲染 | 1 条通过 |
| `frontend/tests/unit/api/http.spec.ts` | Bearer Token 注入、禁止用户自控 header 鉴权、multipart/binary、401 登录失效、403 无权限、账号禁用/锁定跳转 | 10 条通过 |

## 8 测试执行日志

### 8.1 后端 AUTH 执行日志

| 日志编号 | 时间 | 命令/测试类 | 执行内容 | 结果 |
| --- | --- | --- | --- | --- |
| AUTH-LOG-001 | 2026-06-10 16:35 | 直接执行 `mvn` | 尝试运行 AUTH 后端目标测试 | 本机未配置全局 Maven，未进入测试断言 |
| AUTH-LOG-002 | 2026-06-10 16:37 | 仓库 Maven + 默认 JDK 15 | 尝试运行 AUTH 后端目标测试 | Java 运行时版本过低，无法运行 Java 21 class file，未进入测试断言 |
| AUTH-LOG-003 | 2026-06-10 16:38 | `AuthAdminControllerTest` | 管理员用户、角色、权限、账号状态和审计日志管理 | 9 条通过 |
| AUTH-LOG-004 | 2026-06-10 16:38 | `AuthControllerTest` | 注册、登录、退出、当前用户、权限校验、异常、资料、密码、锁定 | 19 条通过 |
| AUTH-LOG-005 | 2026-06-10 16:38 | `AuthMigrationScriptTest` | AUTH 迁移 MySQL 兼容自增和时间戳语法 | 1 条通过 |
| AUTH-LOG-006 | 2026-06-10 16:38 | `HeaderCurrentUserProviderTest` | 兼容 Header 当前用户上下文解析和缺失鉴权失败 | 3 条通过 |
| AUTH-LOG-007 | 2026-06-10 16:38 | `AuthCrsIntegrationTest` | AUTH Bearer 登录态与 CRS 课程成员联动 | 2 条通过 |
| AUTH-LOG-008 | 2026-06-10 16:38 | Maven 汇总 | `Tests run: 34, Failures: 0, Errors: 0, Skipped: 0` | 构建成功 |

### 8.2 前端 AUTH 执行日志

| 日志编号 | 时间 | 命令/测试文件 | 执行内容 | 结果 |
| --- | --- | --- | --- | --- |
| AUTH-LOG-009 | 2026-06-10 16:35 | `authApi.spec.ts` | AUTH API wrapper 路由、Bearer、存储、管理接口、审计日志、权限校验 | 7 条通过 |
| AUTH-LOG-010 | 2026-06-10 16:35 | `http.spec.ts` | 共享 HTTP 鉴权、401/403/账号异常跳转和敏感 header 策略 | 10 条通过 |
| AUTH-LOG-011 | 2026-06-10 16:35 | `AuthProfileView.spec.ts` | 资料加载、资料修改、密码修改和确认校验 | 2 条通过 |
| AUTH-LOG-012 | 2026-06-10 16:35 | `AuthView.spec.ts` | 登录、角色入口、注册和错误反馈 | 2 条通过 |
| AUTH-LOG-013 | 2026-06-10 16:35 | `AuthAdminView.spec.ts` | 管理员用户角色和角色权限管理页面状态 | 1 条通过 |
| AUTH-LOG-014 | 2026-06-10 16:35 | Vitest 汇总 | `Test Files 5 passed (5)`、`Tests 22 passed (22)` | 构建成功 |

## 9 手工测试与联调确认

| 手测编号 | 模块 | 场景 | 操作要点 | 预期结果 | 当前结果 |
| --- | --- | --- | --- | --- | --- |
| MAN-AUTH-001 | AUTH | 浏览器登录、刷新和退出 | 使用学生、教师、管理员账号登录，刷新页面，退出后访问 `/courses` 等需认证页面 | 登录态保持；退出后跳转登录或提示会话失效 | 待手工验收 |
| MAN-AUTH-002 | AUTH | 浏览器注册和失败提示 | 切换注册模式，创建学生账号；提交重复邮箱、弱密码或缺失字段 | 成功后回到登录；失败提示清晰且不暴露内部细节 | 待手工验收 |
| MAN-AUTH-003 | AUTH | 个人资料与修改密码 | 登录后进入个人资料页，修改昵称/联系方式/头像，修改密码后使用旧密码和旧会话访问 | 资料更新；旧密码和旧会话失效；提示明确 | 待手工验收 |
| MAN-AUTH-004 | AUTH | 管理员用户、角色、权限管理 | 管理员查询用户、创建用户、调整角色、调整角色权限、禁用账号 | 管理操作成功；目标用户权限刷新；禁用账号无法继续访问 | 待手工验收 |
| MAN-AUTH-005 | AUTH | 权限边界页面 | 学生访问教师入口和管理员后台；教师访问管理员后台；管理员访问审计日志 | 越权页面显示 403；接口返回 403；管理员可查看日志 | 待手工验收 |
| MAN-AUTH-006 | AUTH | 会话过期和账号异常状态 | 手工制造过期 token、撤销 token、锁定账号、禁用账号 | 前端清理登录态并跳转登录失效或账号状态页 | 待手工验收 |
| MAN-AUTH-007 | AUTH/CRS/HWK/LAB/GRD/LRN | 主流程登录态联调 | 登录后进入课程、作业/实验、成绩、通知页面，确认各模块使用后端当前用户上下文 | 不需要前端传操作者 `userId`；各模块继续执行业务归属校验 | 待联调确认 |
| MAN-AUTH-008 | AUTH | 专项安全扫描 | 使用 OWASP/ZAP 或等效工具扫描认证、管理和资料接口 | 无高危认证绕过、敏感信息泄露、明文密码或令牌泄露 | 待专项测试 |
| MAN-AUTH-009 | AUTH | 性能和并发 | 准备大量用户、会话和审计日志，压测登录、`/me`、权限校验、审计分页 | 常规认证和权限接口满足 3 秒响应要求，分页稳定 | 待专项测试 |
| MAN-AUTH-010 | AUTH/全部模块 | 统一测试环境闭环 | 执行“登录 -> 课程 -> 作业/实验 -> 提交/评测 -> 成绩 -> 通知” | AUTH 登录态贯穿主流程，异常和权限边界一致 | 待测试负责人整合 |

## 10 缺陷、风险与处理建议

| 风险编号 | 风险说明 | 影响范围 | 建议处理 |
| --- | --- | --- | --- |
| R-AUTH-001 | 当前未执行真实浏览器端到端验收 | `UI-AUTH-01 ~ UI-AUTH-11`、`NFR-UA-03` | 测试负责人整合后按 `MAN-AUTH-001 ~ MAN-AUTH-006` 补跑 |
| R-AUTH-002 | 当前未执行专项漏洞扫描和渗透测试 | `FR-UA-07`、`NFR-UA-01` | 使用安全扫描工具覆盖认证绕过、越权、敏感信息泄露和弱口令策略 |
| R-AUTH-003 | LAB/HWK/GRD/LRN 统一环境登录态联调尚未完整记录 | `FR-UA-03`、`NFR-UA-05` | 在统一测试环境执行主流程，确认所有模块只信任 AUTH 当前用户上下文 |
| R-AUTH-004 | 当前性能验证主要来自分页、索引和基础自动化样本，未做生产规模压测 | `NFR-UA-04` | 准备大批量用户、会话、审计日志，补充登录、`/me`、权限校验、审计查询压测 |
| R-AUTH-005 | 本机默认 Java/Maven 环境未直接满足后端测试要求 | 本地验证流程 | 后端测试需明确使用 Maven 3.9.9 和 JDK 21+；本次使用 JDK 25 验证通过 |

## 11 验收结论

| 验收项 | 结论 | 说明 |
| --- | --- | --- |
| 功能覆盖 | 通过 | `FR-UA-01 ~ FR-UA-07` 均有自动化覆盖，核心登录、权限、密码、审计和安全分支已验证 |
| 接口覆盖 | 通过 | `API-AUTH-01 ~ API-AUTH-17` 的主要路由、请求、权限、错误和响应由后端/前端自动化覆盖 |
| 页面覆盖 | 有条件通过 | Vue 单测覆盖主要页面状态和交互，真实浏览器端到端流程待手工确认 |
| 数据一致性 | 通过 | `DB-AUTH-01 ~ DB-AUTH-07` 迁移语法、唯一约束、会话和审计关键字段已由自动化覆盖 |
| 权限与安全 | 通过 | Bearer 鉴权、header-only 拒绝、越权拒绝、伪造 token、账号禁用/锁定、敏感信息保护均有自动化覆盖 |
| 非功能 | 有条件通过 | 安全性、可靠性、可用性、可测试性已覆盖核心场景；专项扫描和生产规模压测待补充 |
| 最终结论 | 有条件通过 | 当前文档可交给测试负责人整合；需补充真实浏览器、专项安全扫描、生产规模压测和全模块联调记录 |

## 12 附录

### 12.1 执行命令

```powershell
cd C:\Code\SE\OnlineJudge\backend
$env:JAVA_HOME='C:\Program Files\Java\jdk-25'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Code\SE\.codex-tools\apache-maven-3.9.9\bin\mvn.cmd' "-Dtest=AuthControllerTest,AuthAdminControllerTest,AuthMigrationScriptTest,HeaderCurrentUserProviderTest,AuthCrsIntegrationTest" test

cd C:\Code\SE\OnlineJudge\frontend
npm run test:unit -- tests/unit/auth/AuthView.spec.ts tests/unit/auth/AuthProfileView.spec.ts tests/unit/auth/AuthAdminView.spec.ts tests/unit/auth/authApi.spec.ts tests/unit/api/http.spec.ts --pool=threads
```

### 12.2 本次执行摘要

| 项目 | 摘要 |
| --- | --- |
| 后端 AUTH 自动化测试 | 5 个测试类，34 passed / 0 failed / 0 errors / 0 skipped |
| 前端 AUTH/API 自动化测试 | 5 files passed / 22 tests passed |
| 自动化覆盖 | 注册登录、会话、当前用户、退出、角色权限、账号状态、资料密码、失败锁定、审计日志、迁移约束、Bearer 鉴权、AUTH/CRS 联动 |
| 手工/联调状态 | 待测试负责人整合后补充真实浏览器、专项安全扫描、生产规模压测和全模块联调记录 |

## 13 D2 补充执行记录（Issue #261）

### 13.1 场景清单与三层图

AUTH 业务场景清单、`include` 公共子流程、备选/异常路径及需求层/概要层/详细层图组映射见 `TST-DOC-02-AUTH-业务场景清单与测试闭环.md`，三份最终提交文档 AUTH 章节已同步补齐图组。

### 13.2 D2 执行证据

| 项目 | 结果 |
| --- | --- |
| `dev` 基线 SHA | `a11f025ce96f5bf26dff07c54bcb9728abcd2abf`（PR #274 合并提交，包含 #271 修复） |
| 精确测试提交 SHA | `250a320b2eb7a88d1e24f7a166bbb175a802b8f0` |
| 环境 | Windows 11（10.0.26200）；JDK 25；Maven 3.9.9；Node v22.19.0；npm 10.9.3；Playwright 1.62.1；Chrome（`E2E_BROWSER_CHANNEL=chrome`） |
| 应用入口 | 本地真实服务 Spring Boot :8080 + Vite :5173，`E2E_BASE_URL=http://127.0.0.1:5173` |
| 后端 AUTH 目标测试 | PASS：36 / FAIL：0 / ERROR：0 / SKIP：0（5 个测试类） |
| 前端 AUTH/API/根导航单元测试 | PASS：34 / FAIL：0（6 个文件） |
| 类型检查 / 构建 | `npm run typecheck` PASS；`npm run build` PASS |
| 共享 E2E 契约 | `npm run test:e2e:contract` PASS（3/3） |
| 完整 E2E 套件 | 共享 smoke 2 条 + AUTH 9 条，PASS：11 / FAIL：0 / SKIP：0；AUTH-E2E-05/06 直接断言真实跳转 |
| Mermaid 图源 | 概要/详细设计 102 个内嵌图块全部渲染；新增需求层 `fig_4_36`、`fig_4_37` 生成 SVG 成功 |
| `verify-e2e-failure` | BLOCKED（共享框架 Windows 平台缺陷 DEF-001，等效手工验证 PASS） |
| `git diff --check` | PASS |

### 13.3 测试发现缺陷

- DEF-003：登录页触发 `ERR-AUTH-03`（禁用/锁定）后 URL 被 pushState 到 `/account-disabled`，但视图不切换（导航监听器仅挂在已登录外壳）；#271 / PR #274 已合入 `dev`，并在精确测试提交 `250a320` 上通过 AUTH-E2E-05/06 真实跳转复测。其余详见 `TST-DOC-02-AUTH-业务场景清单与测试闭环.md` 第 6.1 节。
