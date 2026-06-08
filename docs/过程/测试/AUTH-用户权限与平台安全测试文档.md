# AUTH 用户权限与平台安全测试文档

## 1. 测试范围

本文档对应 GitHub Issue #153 `TST-DOC-02 AUTH 用户权限与平台安全测试文档编写`，覆盖 AUTH 用户权限与平台安全模块的功能、接口、页面、数据库、异常、权限和非功能测试点。本 issue 仅交付测试文档，不修改业务代码。

引用依据：

- `docs/开发/AUTH-用户权限与平台安全模块开发流程.md`
- `docs/最终提交/软件需求规格说明书.md`
- `docs/最终提交/软件概要设计说明书.md`
- `docs/最终提交/软件详细设计说明书.md`
- `docs/过程/需求/用户权限与平台安全模块（后端负责）.md`
- `docs/过程/概要/用户权限与平台安全模块概要设计提交稿（AUTH）.md`
- `docs/过程/详细设计/AUTH-用户权限与平台安全-详细设计提交稿.md`

覆盖编号：

| 类型 | 编号范围 | 覆盖说明 |
| --- | --- | --- |
| 功能需求 | `FR-UA-01` ~ `FR-UA-07` | 注册登录、角色权限、统一鉴权、账号密码安全、异常提示、审计日志、安全防护 |
| 非功能需求 | `NFR-UA-01` ~ `NFR-UA-05` | 安全性、可靠性、可用性、性能、可测试性 |
| 页面编号 | `UI-AUTH-01` ~ `UI-AUTH-11` | 登录、注册、个人资料、修改密码、管理员用户/角色/权限/审计页面、403 与登录失效提示 |
| 接口编号 | `API-AUTH-01` ~ `API-AUTH-17` | `/api/v1/auth/*`、`/api/v1/users/me*`、`/api/v1/admin/*` |
| 数据表编号 | `DB-AUTH-01` ~ `DB-AUTH-07` | 用户、角色、权限、用户角色、角色权限、会话、审计日志 |
| 测试编号 | `TC-UA-01` ~ `TC-UA-07`、`TC-UA-N01` ~ `TC-UA-N05` | 与详细设计追踪矩阵保持一致 |

不在本文档范围内：

- CRS 课程成员关系、课程权限和资源归属的详细测试。
- LAB/HWK/GRD/LRN 业务数据归属测试，仅在 AUTH 跨模块鉴权风险中标注联调要求。
- 第三方 OAuth、短信、邮件、MFA、SSO 等未纳入首版设计的能力。

## 2. 测试环境

| 环境项 | 建议配置 | 验证用途 |
| --- | --- | --- |
| 后端 | JDK 21、Spring Boot、Maven、H2 MySQL mode 或 MySQL 8 | Controller、Service、迁移脚本、会话与审计验证 |
| 前端 | Node.js、npm、Vue 3、TypeScript、Vite、Vitest | API 客户端、页面表单、权限状态、路由状态验证 |
| 数据库 | H2 MySQL mode 用于自动化测试；MySQL 8 用于联调验收 | 验证 `t_auth_*` 表结构、唯一约束、状态字段和索引 |
| 认证方式 | 正式 Bearer Token；测试中关闭 header-only 身份模拟 | 验证 `Authorization: Bearer <token>` 是运行时身份来源 |
| 浏览器 | Chrome/Edge 最新稳定版 | 手工验收 AUTH 页面交互、403、登录失效和管理员页面 |

推荐验证命令：

```bash
cd backend
mvn test "-Dtest=AuthMigrationScriptTest,AuthControllerTest,AuthAdminControllerTest"
```

```bash
cd frontend
npm run test:unit -- tests/unit/auth/authApi.spec.ts tests/unit/auth/AuthView.spec.ts tests/unit/auth/AuthProfileView.spec.ts tests/unit/auth/AuthAdminView.spec.ts tests/unit/api/http.spec.ts tests/unit/grd/App.spec.ts
```

文档类变更最低验证：

```bash
git diff --check
```

## 3. 测试数据

| 数据编号 | 数据内容 | 用途 | 约束 |
| --- | --- | --- | --- |
| TD-AUTH-01 | 学生账号 `student45` / `Student45@pass` | 注册、登录、当前用户、学生权限边界 | `userType=STUDENT`，默认角色 `STUDENT` |
| TD-AUTH-02 | 教师账号 `teacher46` / `Teacher46@pass` | 管理员创建教师、角色分配、教师入口展示 | 需由管理员或可信注册流程创建 |
| TD-AUTH-03 | 管理员账号 `admin46` / `Admin46@pass` | 用户管理、角色管理、权限分配、审计查询 | 具备 `ADMIN` 角色和 `auth:manage` 权限 |
| TD-AUTH-04 | 禁用或锁定账号 `target-status49` | 账号状态异常、会话吊销、`ERR-AUTH-03` | 禁用后原有会话应失效 |
| TD-AUTH-05 | 重复邮箱 `student47@example.com`、手机号 `13900000047` | 注册唯一约束和冲突提示 | 返回 `AUTH_409` |
| TD-AUTH-06 | 错误密码、伪造 token、空权限码、畸形 JSON | 异常、安全提示和参数校验 | 不暴露堆栈、明文 token、账号存在性 |

## 4. 自动化覆盖现状

| 覆盖对象 | 自动化测试文件 | 已覆盖要点 |
| --- | --- | --- |
| 数据库迁移 | `backend/src/test/java/com/onlinejudge/auth/AuthMigrationScriptTest.java` | AUTH 迁移脚本 MySQL 兼容性、时间字段和自增语法 |
| 登录、注册、会话、权限校验、个人资料、密码安全 | `backend/src/test/java/com/onlinejudge/auth/AuthControllerTest.java` | `API-AUTH-01` ~ `API-AUTH-07`、`API-AUTH-16`、会话吊销、token 摘要存储、登录失败、锁定、输入校验、安全错误包装 |
| 管理员用户、角色、权限、审计 | `backend/src/test/java/com/onlinejudge/auth/AuthAdminControllerTest.java` | `API-AUTH-08` ~ `API-AUTH-15`、`API-AUTH-17`、普通用户拒绝、管理员禁用账号、审计日志筛选、敏感字段不泄露 |
| 前端 AUTH API 客户端 | `frontend/tests/unit/auth/authApi.spec.ts` | 注册、登录状态保存、Bearer 调用、管理员端点、审计查询、权限校验、个人资料和密码接口 |
| 登录/注册页面 | `frontend/tests/unit/auth/AuthView.spec.ts` | `UI-AUTH-01`、`UI-AUTH-02` 登录成功反馈、注册失败提示、公开注册不展示教师/管理员选项 |
| 个人资料/修改密码页面 | `frontend/tests/unit/auth/AuthProfileView.spec.ts` | `UI-AUTH-03`、`UI-AUTH-04` 资料加载与更新、密码修改、前端确认密码校验 |
| 管理员 AUTH 页面 | `frontend/tests/unit/auth/AuthAdminView.spec.ts` | `UI-AUTH-05` ~ `UI-AUTH-09` 用户、角色、权限、用户角色、审计日志交互 |
| 统一请求层与会话异常 | `frontend/tests/unit/api/http.spec.ts` | Bearer 注入、401 清理登录态并跳转登录失效页、403 保留登录态并提示无权限、禁用账号跳转账号状态页 |
| 应用级路由权限 | `frontend/tests/unit/grd/App.spec.ts` | `/admin/auth` 访问前调用 `/api/v1/auth/me`，非管理员显示 403，登录失效/账号异常清理本地登录态 |

## 5. 测试用例表

### 5.1 功能、接口、页面与数据库测试

| 用例编号 | 追踪编号 | 测试目标 | 前置条件 | 测试数据 | 操作步骤 | 预期结果 | 实际结果/通过状态 | 自动化覆盖 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TC-UA-01-01 | `FR-UA-01`、`UI-AUTH-02`、`API-AUTH-02`、`DB-AUTH-01` | 学生公开注册成功，且响应不暴露密码字段 | AUTH 服务可用，用户名/邮箱/手机号未被占用 | TD-AUTH-01 | 1. 调用 `POST /api/v1/auth/register`；2. 提交 `username/password/userType/displayName/email`；3. 检查响应和用户表 | 返回 `code=0`；用户角色包含 `STUDENT`；响应不存在 `passwordHash/passwordSalt`；`t_auth_user` 写入用户 | 已由自动化覆盖；通过状态以本次验证命令为准 | `AuthControllerTest.userRegistersLogsInReadsCurrentUserAndLogoutRevokesToken`、`authApi.spec.ts` |
| TC-UA-01-02 | `FR-UA-01`、`UI-AUTH-01`、`API-AUTH-01/03/04`、`DB-AUTH-06/07` | 登录后可读取当前用户，退出后 token 被吊销 | 学生账号已注册且状态正常 | TD-AUTH-01 | 1. 调用登录接口；2. 使用 Bearer token 调用 `/auth/me`；3. 调用退出接口；4. 再次调用 `/auth/me` | 登录返回 token 和过期时间；`/auth/me` 返回用户、角色和权限；退出成功写入 `LOGOUT` 审计；吊销后返回 `ERR-AUTH-04` | 已由自动化覆盖；通过状态以本次验证命令为准 | `AuthControllerTest.userRegistersLogsInReadsCurrentUserAndLogoutRevokesToken`、`authApi.spec.ts` |
| TC-UA-01-03 | `FR-UA-01`、`FR-UA-05`、`API-AUTH-01/02` | 登录失败和重复注册返回安全提示 | 已存在学生账号；错误密码、重复邮箱或手机号 | TD-AUTH-05、TD-AUTH-06 | 1. 使用错误密码登录；2. 重复提交邮箱或手机号；3. 尝试公开注册教师/管理员 | 错误密码返回 `ERR-AUTH-01` 且不暴露账号是否存在；重复字段返回 `AUTH_409`；公开注册教师/管理员返回 `AUTH_400` | 已由自动化覆盖；通过状态以本次验证命令为准 | `AuthControllerTest.loginFailureUsesSafeMessageAndDoesNotCreateSession`、`registrationRejectsDuplicateEmailAndPhoneUsedForLogin`、`publicRegistrationRejectsTeacherAndAdminRoles` |
| TC-UA-02-01 | `FR-UA-02`、`UI-AUTH-05/08`、`API-AUTH-08/09/10/15`、`DB-AUTH-01/02/04/07` | 管理员能创建用户、禁用账号、调整用户角色并写入审计 | 管理员登录，目标用户存在或可创建 | TD-AUTH-02、TD-AUTH-03、TD-AUTH-04 | 1. 管理员查询用户；2. 创建教师账号；3. 调整目标用户角色；4. 禁用目标账号；5. 查询会话状态与审计日志 | 管理员操作成功；目标用户角色更新；禁用后目标账号会话变为 `REVOKED`；关键操作写入审计 | 已由自动化覆盖；通过状态以本次验证命令为准 | `AuthAdminControllerTest.adminAssignsUserRolesAndRolePermissionsWithAuditLogs`、`adminDisablingAccountRevokesExistingSessionsAndReportsAccountStatusError`、`AuthAdminView.spec.ts` |
| TC-UA-02-02 | `FR-UA-02`、`UI-AUTH-06/07`、`API-AUTH-11/12/13/14`、`DB-AUTH-02/03/05/07` | 管理员能维护角色和角色权限，普通用户不能访问 | 管理员和学生均已登录 | TD-AUTH-01、TD-AUTH-03 | 1. 管理员查询角色和权限；2. 创建/更新角色；3. 调整角色权限；4. 学生调用角色管理接口和权限调整接口 | 管理员操作成功并写入审计；学生请求返回 `ERR-AUTH-05` | 已由自动化覆盖；通过状态以本次验证命令为准 | `AuthAdminControllerTest.adminAssignsUserRolesAndRolePermissionsWithAuditLogs`、`studentCannotAccessRoleManagementApi`、`studentCannotChangeRolePermissions`、`authApi.spec.ts` |
| TC-UA-03-01 | `FR-UA-03`、`UI-AUTH-10/11`、`API-AUTH-04/16`、`DB-AUTH-02~06` | 后端只信任 Bearer 会话，不信任前端传入身份头 | 无有效 token；请求携带 `X-User-Id` 等 header | TD-AUTH-06 | 1. 不带 Bearer 访问 `/auth/me`；2. 只带 `X-User-Id/X-User-Role` 访问 `/auth/me` 和业务接口；3. 使用有效 token 调用权限校验接口 | 未认证请求返回 `ERR-AUTH-04`；header-only 身份不被接受；有效权限返回 allowed；缺失权限返回 `ERR-AUTH-05` 并写入拒绝审计 | 已由自动化覆盖；通过状态以本次验证命令为准 | `AuthControllerTest.currentUserRequiresBearerSessionInsteadOfHeaderOnlyIdentity`、`businessApiRejectsHeaderOnlyIdentityWhenSessionTokenIsMissing`、`checkPermissionAllowsCurrentUserPermission`、`checkPermissionRejectsMissingPermissionAndAuditsDeniedAccess` |
| TC-UA-03-02 | `FR-UA-03`、`UI-AUTH-10/11` | 前端管理员 AUTH 页面按当前用户身份控制访问 | 浏览器已有 token；`/auth/me` 返回管理员或非管理员 | TD-AUTH-01、TD-AUTH-03 | 1. 访问 `/admin/auth`；2. 前端先调用 `/api/v1/auth/me`；3. 分别模拟管理员、教师、过期会话和禁用账号 | 管理员渲染 AUTH 管理页；非管理员显示 403；过期或禁用账号清理登录态并显示对应状态页 | 已由自动化覆盖；通过状态以本次验证命令为准 | `frontend/tests/unit/grd/App.spec.ts` |
| TC-UA-04-01 | `FR-UA-04`、`UI-AUTH-03`、`API-AUTH-05/06`、`DB-AUTH-01` | 用户可查看和修改个人资料，非法联系方式不落库 | 用户已登录 | TD-AUTH-01 | 1. 调用 `GET /api/v1/users/me`；2. 修改昵称、手机号、邮箱、头像；3. 分别提交非法手机号、邮箱、超长昵称、超长头像 URL | 合法修改返回更新后的资料；响应不含密码字段；非法输入返回 `AUTH_400`，数据库保持原值 | 已由自动化覆盖；通过状态以本次验证命令为准 | `AuthControllerTest.currentUserProfileCanBeReadAndUpdatedWithoutSensitiveFields`、`profileUpdateRejectsInvalidContactAndDisplayNameBeforeSaving`、`AuthProfileView.spec.ts` |
| TC-UA-04-02 | `FR-UA-04`、`UI-AUTH-04`、`API-AUTH-07`、`DB-AUTH-01/06/07` | 修改密码需校验原密码，保存哈希并吊销旧会话 | 用户已登录且有旧 token | TD-AUTH-01 | 1. 使用错误原密码改密；2. 使用正确原密码改密；3. 检查新旧密码登录结果；4. 检查旧 token 和审计日志 | 错误原密码返回 `AUTH_401`；成功后哈希变化且不含明文；旧 token 返回 `ERR-AUTH-04`；旧密码不可登录，新密码可登录；写入 `PASSWORD_CHANGED` 审计 | 已由自动化覆盖；通过状态以本次验证命令为准 | `AuthControllerTest.passwordChangeRequiresOldPasswordRehashesAndRevokesExistingSessions`、`AuthProfileView.spec.ts` |
| TC-UA-05-01 | `FR-UA-05`、`UI-AUTH-10/11`、`API-AUTH-01/04/16`、`DB-AUTH-01/06/07` | 未登录、过期/吊销会话、账号禁用和无权限访问返回统一错误码 | 存在正常、吊销、禁用、缺权用户 | TD-AUTH-04、TD-AUTH-06 | 1. 未登录调用需认证接口；2. 使用已退出 token 调用；3. 禁用账号后使用旧 token 调用；4. 缺权访问管理员接口 | 未登录或失效返回 `ERR-AUTH-04`；账号异常返回 `ERR-AUTH-03`；无权限返回 `ERR-AUTH-05`；前端展示登录失效、账号状态或 403 状态 | 已由自动化覆盖；通过状态以本次验证命令为准 | `AuthControllerTest.expiredOrRevokedSessionUsesDocumentedSessionExpiredErrorCode`、`AuthAdminControllerTest.adminDisablingAccountRevokesExistingSessionsAndReportsAccountStatusError`、`http.spec.ts` |
| TC-UA-06-01 | `FR-UA-06`、`UI-AUTH-09`、`API-AUTH-17`、`DB-AUTH-07` | 关键安全操作可被管理员按条件查询 | 已产生登录成功、登录失败、角色调整、权限调整审计 | TD-AUTH-03、TD-AUTH-06 | 1. 管理员调用 `/api/v1/admin/audit-logs`；2. 按操作者、类型、结果、时间分页筛选；3. 检查 IP、User-Agent 和失败原因 | 返回分页审计记录；字段包含 `operatorId/operationType/targetType/resultStatus/clientIp/userAgent/createdAt`；敏感字段不出现 | 已由自动化覆盖；通过状态以本次验证命令为准 | `AuthAdminControllerTest.adminQueriesAuditLogsWithOperationResultOperatorAndTimeFilters`、`loginAuditBoundsClientIpAndUserAgentToColumnLimits`、`authApi.spec.ts`、`AuthAdminView.spec.ts` |
| TC-UA-07-01 | `FR-UA-07`、全部 AUTH 页面和接口、`DB-AUTH-01~07` | 平台基础安全防护拦截非法输入、伪造 token 和敏感错误信息 | AUTH 服务可用 | TD-AUTH-06 | 1. 提交畸形 JSON；2. 使用伪造 Bearer token；3. 提交空权限码；4. 创建用户时检查响应和审计文本 | 返回安全错误码和通用提示；不暴露异常类、堆栈、token、明文密码；空权限码返回 `AUTH_400`；创建用户响应和审计不包含密码 | 已由自动化覆盖；通过状态以本次验证命令为准 | `AuthControllerTest.malformedAuthJsonReturnsSafeValidationErrorWithoutInternalDetails`、`forgedBearerTokenUsesSafeAuthenticationFailureMessage`、`checkPermissionRejectsBlankPermissionCode`、`AuthAdminControllerTest.adminCreateUserDoesNotExposePasswordInResponseOrAuditLogs` |

### 5.2 非功能测试

| 用例编号 | 追踪编号 | 测试目标 | 操作步骤 | 预期结果 | 实际结果/通过状态 | 覆盖方式 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-UA-N01 | `NFR-UA-01` 安全性 | 验证认证、权限、密码和 token 安全策略完整 | 执行 TC-UA-03、TC-UA-04、TC-UA-05、TC-UA-07；检查响应、数据库和审计 | 未授权默认拒绝；密码不明文存储/返回；token 仅摘要存储；错误信息不暴露内部细节 | 已由自动化覆盖；需补充浏览器手工确认 403/登录失效页面文案 | 自动化 + 手工 |
| TC-UA-N02 | `NFR-UA-02` 可靠性 | 验证关键安全功能稳定且认证异常不默认放行 | 执行注册、登录、退出、改密、禁用账号、角色调整、权限调整；模拟无 token/吊销 token | 正常流程稳定返回；异常流程均按 `ERR-AUTH-*` 拒绝；关键权限变更失败时不产生半成品数据 | 已由自动化覆盖；真实 MySQL 联调需复测 | 自动化 + 联调 |
| TC-UA-N03 | `NFR-UA-03` 可用性 | 验证不同角色入口、错误提示、空/失败状态清晰 | 使用学生、教师、管理员登录并访问 AUTH 相关页面；触发登录失败、注册冲突、无权限、会话失效 | 页面显示与角色匹配的入口；失败提示可理解；无权限和登录失效有明确引导 | 前端组件已覆盖核心状态；需浏览器验收真实页面 | 自动化 + 手工 |
| TC-UA-N04 | `NFR-UA-04` 性能 | 验证登录认证、当前用户、权限校验和审计查询常规响应不超过 3 秒 | 在本地或 DEV 环境分别调用 `/auth/login`、`/auth/me`、`/auth/check-permission`、`/admin/audit-logs`，记录响应时间 | 常规数据量下单次请求响应时间不超过 3 秒；审计分页查询不一次性拉全量数据 | 尚需手工或压测脚本验证；当前自动化仅证明接口可用和分页参数 | 手工/联调 |
| TC-UA-N05 | `NFR-UA-05` 可测试性 | 验证学生、教师、管理员访问边界和关键安全场景均可重复执行 | 执行本表所有 P0/P1 用例，并确认测试数据可重建 | 自动化用例可重复运行；手工验收项有清晰前置条件、步骤和预期 | 已建立后端和前端自动化入口；跨模块联调项需在 #159 汇总 | 自动化 + 整合 |

## 6. 执行结果记录

| 执行批次 | 执行日期 | 执行范围 | 执行命令/方式 | 结果 | 备注 |
| --- | --- | --- | --- | --- | --- |
| AUTH-DOC-153-01 | 2026-06-08 | 文档格式检查 | `git diff --check` | 通过 | 本 issue 为纯文档改动，最低验证项；提交前使用暂存区 diff 检查 |
| AUTH-DOC-153-02 | 2026-06-08 | 后端 AUTH 自动化测试 | `cd backend && mvn test "-Dtest=AuthMigrationScriptTest,AuthControllerTest,AuthAdminControllerTest"` | 阻塞 | 本机 PATH 中没有 `mvn`，仓库未提供 Maven Wrapper，无法执行后端测试；已在风险中记录 |
| AUTH-DOC-153-03 | 2026-06-08 | 前端 AUTH 自动化测试 | `cd frontend && npm run test:unit -- tests/unit/auth/authApi.spec.ts tests/unit/auth/AuthView.spec.ts tests/unit/auth/AuthProfileView.spec.ts tests/unit/auth/AuthAdminView.spec.ts tests/unit/api/http.spec.ts tests/unit/grd/App.spec.ts` | 通过 | 6 个测试文件通过，40 个测试通过 |

## 7. 缺陷、风险与残余验证

| 风险编号 | 风险内容 | 影响范围 | 处理建议 |
| --- | --- | --- | --- |
| R-AUTH-01 | AUTH 设计文档中部分过程文档存在编码显示异常，但最终提交文档可正常读取，本文档以最终提交文档为主。 | 文档引用一致性 | #159 整合时以最终提交三份文档为基线，过程文档仅作补充来源。 |
| R-AUTH-02 | `TC-UA-N04` 性能目标需要真实运行环境或压测脚本记录响应时间，现有单元/接口测试不直接给出 3 秒指标。 | 非功能性能验收 | 在 DEV/FAT 环境补充登录、`/auth/me`、`/auth/check-permission`、审计分页的响应时间记录。 |
| R-AUTH-03 | CRS/LAB/HWK/GRD/LRN 的业务数据归属由对应模块二次校验，AUTH 只能证明当前用户上下文可信。 | 跨模块权限闭环 | #159 整合测试需覆盖“登录 -> 课程 -> 作业/实验 -> 提交/评测 -> 成绩 -> 通知”链路，检查业务模块未信任前端 `userId`。 |
| R-AUTH-04 | 403 页面、登录失效页、账号状态页的浏览器真实交互需要手工验收，组件测试不能完全替代视觉和导航检查。 | 前端可用性验收 | 使用三类账号在浏览器打开登录页、管理员页、无权限路由和会话失效场景，记录截图或验收结论。 |
| R-AUTH-05 | 审计日志是否覆盖所有业务模块的越权访问，需要业务模块接入后确认。 | 安全审计完整性 | AUTH 先覆盖自身登录、改密、角色权限和权限校验拒绝；跨模块越权日志在各模块测试文档补充。 |
| R-AUTH-06 | 当前本地环境缺少 `mvn`，且仓库没有 `mvnw`，后端 AUTH 自动化测试无法在本次文档提交时复跑。 | 后端覆盖状态确认 | 在配置 Maven 的环境中补跑 `AuthMigrationScriptTest`、`AuthControllerTest`、`AuthAdminControllerTest`；本文件已列明这些用例的覆盖关系。 |

## 8. 验收结论

AUTH 模块测试文档已按 #152 模板覆盖测试范围、测试环境、测试数据、测试用例表、执行结果记录、缺陷与风险、验收结论。用例编号已与 `FR-UA-01 ~ FR-UA-07`、`NFR-UA-01 ~ NFR-UA-05`、`UI-AUTH-*`、`API-AUTH-*`、`DB-AUTH-*` 和 `TC-UA-*` 建立追踪关系。

当前交付可供测试负责人整合。自动化已覆盖 AUTH 主流程、异常流程、权限边界、审计日志和大部分安全防护测试点；仍需在统一测试文档中补充性能响应时间记录、浏览器手工验收结果和跨模块业务归属联调结果。
