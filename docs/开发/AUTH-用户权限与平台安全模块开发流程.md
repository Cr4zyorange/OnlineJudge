# AUTH 用户权限与平台安全模块开发流程

## 1. 开发定位

AUTH 是全系统前置依赖，必须优先交付可用的登录态、当前用户上下文、角色权限和基础测试账号。其他模块依赖 AUTH 提供当前用户身份、平台角色、权限码和用户基础信息，但课程内成员关系、实验/作业/成绩归属仍由业务模块结合 CRS 或自身数据校验。

本模块负责人必须完成从数据表、后端接口、服务逻辑到前端页面、权限异常、测试数据和联调说明的完整闭环，不允许只交付后端。

## 2. 详细设计阅读入口

开发前先阅读以下章节，并把实现对象与 DSD 编号保持一致：

- `docs/最终提交/软件详细设计说明书.md` 的 `3.1 用户权限与平台安全模块（AUTH）`
- `docs/过程/详细设计/AUTH-用户权限与平台安全-详细设计提交稿.md`
- 涉及前端和接口汇总时，对照主 DSD 第 4 章界面设计、第 5 章接口设计、第 6 章数据库设计、第 10 章追踪矩阵

## 3. 统一开发顺序

```text
1. 读 AUTH 详细设计章节，确认 UI-AUTH / API-AUTH / SVC-AUTH / DB-AUTH / TC-UA 编号
2. 建 AUTH 数据表和实体
3. 写登录、注册、退出、当前用户基础 API
4. 写认证、令牌、密码、权限和审计服务
5. 写前端登录注册、个人中心和管理页面
6. 接入后端鉴权拦截器与前端 Axios Token 拦截器
7. 补权限不足、登录失效、账号禁用等异常处理
8. 准备管理员、教师、学生三类测试账号
9. 自测 AUTH 模块闭环
10. 给其他模块交付鉴权使用说明并参加联调
```

## 4. P0 最短交付

AUTH 第一优先级不是一次做完全部页面，而是先交付其他模块能依赖的最小身份能力：

1. `POST /api/v1/auth/login`
2. `GET /api/v1/auth/me`
3. 后端登录态/JWT 校验拦截器
4. 前端 Token 保存和 Axios 请求拦截器
5. 学生、教师、管理员三类测试账号
6. 登录后按角色进入对应首页或菜单

完成这一步后，CRS、LAB、HWK、GRD、LRN 才能基于当前用户继续开发。

## 5. 数据库与实体

按 DSD 建立以下表和实体，字段命名、主键、状态枚举和审计字段要与详细设计一致：

| 编号 | 表 | 用途 |
| --- | --- | --- |
| DB-AUTH-01 | `t_auth_user` | 用户基础信息、账号状态、密码哈希 |
| DB-AUTH-02 | `t_auth_role` | 学生、教师、管理员等平台角色 |
| DB-AUTH-03 | `t_auth_permission` | 菜单、接口、资源和操作权限点 |
| DB-AUTH-04 | `t_auth_user_role` | 用户角色关联 |
| DB-AUTH-05 | `t_auth_role_permission` | 角色权限关联 |
| DB-AUTH-06 | `t_auth_session` | 会话或 Token 状态 |
| DB-AUTH-07 | `t_auth_audit_log` | 登录、权限变更、账号变更等关键操作审计 |

建表后立即准备三类账号种子数据，保证其他模块能在本地直接调试。

## 6. 后端 API 与 Service

先实现基础认证 API：

| API | 方法与路径 | 前端页面 | 关键服务 |
| --- | --- | --- | --- |
| API-AUTH-01 | `POST /api/v1/auth/login` | UI-AUTH-01 登录页 | `AuthService`、`PasswordSecurityService`、`SessionTokenService` |
| API-AUTH-02 | `POST /api/v1/auth/register` | UI-AUTH-02 注册页 | `UserService`、`PasswordSecurityService` |
| API-AUTH-03 | `POST /api/v1/auth/logout` | 全局退出入口 | `AuthService`、`SessionTokenService` |
| API-AUTH-04 | `GET /api/v1/auth/me` | 全局当前用户初始化 | `AuthService`、`PermissionService` |

随后补齐用户、角色、权限和审计接口：

- 个人资料：`GET/PUT /api/v1/users/me`
- 修改密码：`PUT /api/v1/users/me/password`
- 用户管理：`GET/POST /api/v1/admin/users`、`PUT /api/v1/admin/users/{userId}/status`
- 角色管理：`GET /api/v1/admin/roles`、`POST/PUT /api/v1/admin/roles`
- 权限分配：`GET /api/v1/admin/permissions`、`PUT /api/v1/admin/roles/{roleId}/permissions`
- 用户角色分配：`PUT /api/v1/admin/users/{userId}/roles`
- 通用权限校验：`POST /api/v1/auth/check-permission`
- 审计日志：`GET /api/v1/admin/audit-logs`

Service 层不要把认证、密码、权限、审计混在一个类里。`AuthService` 只做登录退出编排，密码哈希和失败锁定交给 `PasswordSecurityService`，权限判断交给 `PermissionService` 或 `AccessControlService`，审计写入交给 `AuditLogService`。

## 7. 前端页面与交互

AUTH 前端必须包含：

| 页面编号 | 页面 | 完成标准 |
| --- | --- | --- |
| UI-AUTH-01 | 登录页 | 可登录、显示错误、保存 Token、跳转角色首页 |
| UI-AUTH-02 | 注册页 | 可创建账号或提示注册限制 |
| UI-AUTH-03 | 个人资料页 | 可查看并修改个人基础信息 |
| UI-AUTH-04 | 修改密码页 | 校验原密码、新密码并提示结果 |
| UI-AUTH-05 | 用户管理页 | 管理员查询、创建、启用、禁用用户 |
| UI-AUTH-06 | 角色管理页 | 管理员查询和维护角色 |
| UI-AUTH-07 | 权限分配页 | 管理员调整角色权限点 |
| UI-AUTH-08 | 用户角色分配页 | 管理员为用户分配或移除角色 |
| UI-AUTH-09 | 审计日志页 | 管理员按操作人、类型、时间、结果筛选 |
| UI-AUTH-10 | 403 页面 | 无权限时清晰反馈并可返回 |
| UI-AUTH-11 | 登录失效页 | 会话过期后跳转登录 |

每个页面至少要有加载、成功、失败、空状态反馈。前端路由守卫必须基于 `me` 接口结果判断登录态和权限菜单，不允许只靠本地写死角色。

## 8. 权限、异常与日志

必须覆盖以下边界：

- 未登录访问业务接口返回认证失败，并由前端跳转登录
- 学生不能进入教师页面
- 教师不能进入管理员页面
- 禁用账号不能登录，已有会话应失效
- Token 过期、伪造或被撤销时返回统一错误码
- 关键操作写入审计日志，包括登录失败、权限变更、账号状态变更、密码修改

异常提示要同时满足后端错误码清晰和前端用户提示明确，不暴露密码、Token、账号存在性等敏感细节。

## 9. 测试与自测清单

| 测试点 | 验收标准 |
| --- | --- |
| 登录成功 | 三类测试账号均可登录并获取 `me` |
| 登录失败 | 错误密码、禁用账号、过期 Token 均有明确错误码 |
| 角色权限 | 学生、教师、管理员菜单和路由边界正确 |
| 管理功能 | 管理员可维护用户、角色、权限 |
| 审计日志 | 关键安全操作有记录并可查询 |
| 前端状态 | 加载、失败、空状态、403、登录失效均可演示 |

## 10. 对其他模块交付物

AUTH 完成后需要向全组提供：

- 当前用户 DTO 字段说明
- Token 请求头规范
- 权限码命名规范
- 后端权限注解或拦截器使用方式
- 前端路由守卫和权限菜单判断方式
- 三类测试账号清单

完成标准是其他模块可以不传 `userId`，直接通过 AUTH 当前用户上下文开发自己的权限和数据归属校验。
