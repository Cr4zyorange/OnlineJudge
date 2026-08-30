# AUTH 独立身份服务设计

## 1. 背景与目标

本设计对应 Issue #311：将现有模块化单体中的 AUTH 抽取为可独立构建、测试、制作镜像和部署的身份服务。服务负责登录、会话、当前用户、账号状态、角色、权限和安全审计，并保持现有 AUTH 公共 API、响应结构和错误语义兼容。

设计基线为 `origin/dev@2a3d355`。基线完整后端测试共 408 项，其中 1 项既有 GRD/LRN 异步通知排序测试失败、7 项跳过；AUTH 的 31 项测试全部通过。该既有失败与 #311 无关，不在本分支修改。

## 2. 范围

### 2.1 范围内

- 独立 Spring Boot 启动入口、Maven 构建、运行配置和测试入口。
- 现有 AUTH Controller、Service、Repository、领域对象和安全逻辑。
- `t_auth_user`、`t_auth_role`、`t_auth_permission`、`t_auth_user_role`、`t_auth_role_permission`、`t_auth_session`、`t_auth_audit_log` 的独立迁移与种子数据。
- 现有登录、注册、退出、当前用户、个人资料、密码、用户、角色、权限和审计 API。
- 健康、就绪和版本接口。
- 独立运行镜像及本地 Compose 服务定义。
- 服务级安全、依赖失败、会话并发和 API 兼容测试。
- AUTH Schema 最小权限检查及禁止跨业务表访问的自动化契约测试。

### 2.2 范围外

- API 网关路由、切流和回滚实现，由 #317 负责。
- CRS 课程成员关系和课程权限。
- LAB、HWK、GRD、LRN 的业务表和业务逻辑。
- 新增 OAuth、单点登录、多因素认证或刷新令牌机制。
- 修改现有前端页面和 AUTH 公共 API。
- 修复基线中的 GRD/LRN 通知排序问题。

## 3. 方案选择

采用仓库内独立 `services/auth-service` 运行单元，并在服务拆分阶段保留现有模块化单体作为可追溯回滚基线。

不采用“通过 Spring Profile 裁剪现有单体”的方案，因为该方式仍会编译和携带其他业务模块，不能证明独立构建和数据边界。不采用让新服务依赖整个 `onlinejudge-backend` JAR 的方案，因为这会把其他模块及其 Repository 带入身份服务。

拆分期间允许单体与独立服务保留等价 AUTH 实现，但二者不能在同一部署模式中同时作为 AUTH 数据写入方。独立服务模式只连接 AUTH Schema；单体只作为 D7 切流前的兼容和回滚版本。API 兼容测试防止两份实现静默分叉。

## 4. 代码与构建边界

新增目录：

```text
services/auth-service/
├── pom.xml
├── src/main/java/com/onlinejudge/authservice/
├── src/main/java/com/onlinejudge/auth/
├── src/main/java/com/onlinejudge/common/
├── src/main/resources/
└── src/test/
```

`com.onlinejudge.authservice` 只包含独立启动、配置、探针和版本信息。`com.onlinejudge.auth` 承载现有 AUTH 业务实现。服务内的 `common` 只保留 API 响应、异常映射和当前用户接口等最小基础设施，不包含 CRS、LAB、HWK、GRD、LRN 代码。

`services/auth-service/pom.xml` 可直接执行 `mvn test` 和 `mvn package`，不依赖先构建模块化单体。构建产物使用独立 artifactId 和版本：`onlinejudge-auth-service`。

为控制拆分风险，本 Issue 不重构既有 AUTH 业务算法；迁移以保持行为和测试兼容为主。服务独立后再在测试保持通过的前提下消除明显的重复配置。

## 5. 数据所有权与迁移

AUTH 服务只加载 AUTH 迁移清单。迁移清单必须完整覆盖七张 `t_auth_*` 表及三类测试账号种子，且可在空 Schema 上重复执行。

运行配置使用独立变量：

- `AUTH_DB_HOST`
- `AUTH_DB_PORT`
- `AUTH_DB_NAME`
- `AUTH_DB_USER`
- `AUTH_DB_PASSWORD`

Compose 中为 AUTH 创建独立数据库或 Schema 和最小权限账号。服务级契约测试扫描 SQL、Repository 和迁移清单，若出现 `crs_*`、`lab_*`、`t_hwk_*`、`lrn_*`、`t_grade_*` 等非 AUTH 表名则失败。

跨服务用户 ID 只是逻辑引用；其他服务不能通过外键或 SQL 回查 AUTH 表。用户展示信息由冻结契约或调用方已持有的主体快照取得。

## 6. API 与认证主体契约

所有现有公共路径、HTTP 方法、请求字段、`ApiResponse` 包装和 `ERR-AUTH-*` 错误码保持不变。至少覆盖：

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/check-permission`
- `GET/PUT /api/v1/users/me`
- `PUT /api/v1/users/me/password`
- 现有 `/api/v1/admin/**` AUTH 管理接口

在 #310 完成前，不新增未经冻结的内部身份接口。网关和内部服务消费的最小主体契约复用 `GET /api/v1/auth/me`：调用方转发原始 `Authorization: Bearer <token>`，AUTH 校验会话、账号状态、角色和权限后返回当前用户 DTO。

可信边界如下：

- AUTH 只信任 Bearer 会话，不信任 `X-User-Id`、`X-User-Role` 或类似身份头。
- 缺失、伪造、过期、撤销令牌统一返回 `401 / ERR-AUTH-04`。
- 禁用或冻结账号不能登录，已有会话在解析时失败关闭。
- 权限不足返回 `403 / ERR-AUTH-05` 并记录审计。
- AUTH 不可用时调用方不得回退到 Header 或本地伪造身份；具体网关映射由 #310/#317 消费并保持失败关闭。

#310 后续如新增内部路径或服务凭据，必须使用新版本并保持上述公共契约兼容。

## 7. 探针、版本与依赖失败

身份服务提供：

- `/api/v1/system/health`：进程存活，不执行外部依赖查询。
- `/api/v1/system/readiness`：验证 AUTH 数据源可用；依赖失败时返回非就绪结果，不泄露连接信息。
- `/api/v1/system/version`：返回服务名、应用版本和构建 revision；revision 由构建参数注入。

探针必须是公开端点且响应最小化。业务 API 在数据库不可用时返回标准化服务失败，不得默认放行或返回包含 JDBC、密码、主机名的内部异常。

## 8. 容器与部署

新增独立多阶段 Dockerfile，仅复制 AUTH 服务 POM 和源码，运行时使用 Java 21 JRE。镜像暴露 AUTH 服务端口，并用就绪接口作为 Compose/Kubernetes 就绪检查。

Compose 增加 `auth-service` 和 AUTH 数据库账号配置。现有 `backend` 保留到 #317 切流完成，前端入口和公开路径不在本 Issue 改写。

镜像标签和 OCI revision 使用被测 Git SHA。不得把密码、Token、真实 Secret 或私有凭据写入镜像、Compose 文件或测试日志。

## 9. 测试策略

所有生产行为遵循 RED → GREEN → REFACTOR。

### 9.1 独立边界 RED

先建立会失败的契约测试，证明当前仓库缺少：

- 独立 AUTH Maven 构建和启动入口。
- 独立配置、迁移清单、Dockerfile 和 Compose 服务。
- 健康、就绪、版本接口。
- 非 AUTH 包和表访问扫描。

### 9.2 API 与安全 RED

迁移现有 AUTH 测试并新增：

- 登录、当前用户、退出和管理 API 兼容。
- Header-only 身份、伪造 Bearer 和敏感信息泄露拒绝。
- 过期、撤销会话和禁用账号失败关闭。
- 学生、教师、管理员越权边界。
- 同一账号并发会话、单会话退出和密码修改后的会话失效。
- 数据库不可用时就绪探针和业务 API 的安全错误。

### 9.3 验证层级

1. AUTH 服务单元和 Controller 测试。
2. AUTH 服务完整 `mvn test`、`mvn package`。
3. 模块化单体 AUTH 回归测试，证明公开行为未变。
4. 静态边界和迁移检查。
5. 镜像构建、Compose 启动、探针、三类账号登录和 `/me` 冒烟。
6. 完整后端回归；既有 GRD/LRN 基线缺陷单独记录，不把它误归因于 #311。

## 10. 错误处理与审计

认证失败响应不区分账号不存在和密码错误。日志不得输出明文密码、Bearer Token、数据库密码或完整敏感请求体。令牌只以不可逆摘要保存和审计。

登录成功/失败、退出、密码修改、账号状态变更、角色权限变更和越权拒绝继续写入 AUTH 审计表。审计写入失败按现有设计语义处理，不能通过吞掉认证或权限异常来维持表面成功。

## 11. 交付与验收证据

提交证据记录：运行环境、基线 SHA、被测 SHA、命令、测试总数/通过/失败/跳过、退出码、镜像 digest、OCI revision、探针响应和原始日志位置。

完成条件：

- AUTH 可独立启动、构建、测试、制作镜像和部署。
- `UC-AUTH-01` 及现有 AUTH API 的成功、异常、权限和状态边界通过。
- AUTH 服务不包含其他业务模块，不访问非 AUTH 表。
- Header/Bearer 伪造、失效会话、禁用账号、越权和并发会话测试通过。
- AUTH 不可用时就绪和调用失败语义明确且失败关闭。
- 目标为 `dev` 的非草稿 PR 描述包含 `closes #311`。

## 12. 已知依赖与风险

- #310 尚未关闭。本设计只冻结现有 `/api/v1/auth/me` + Bearer 最小主体契约，避免抢先定义冲突的新内部 API。
- #317 尚未实现网关切流，因此本 Issue 只能交付可部署服务和兼容契约，不能把网关完成声明为 #311 结果。
- Docker Desktop 当前服务未运行且本会话无权限启动；单元和构建测试可使用 IntelliJ 的 Maven 3.9.9/Java 21，镜像验证必须在 Docker Engine 可用后执行并记录。
- 基线存在一个 GRD/LRN 异步通知排序失败；该失败必须单独记录，不能在 AUTH PR 中混入修复。
