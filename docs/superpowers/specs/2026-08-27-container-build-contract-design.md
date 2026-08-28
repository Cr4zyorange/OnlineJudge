# D3-CONTAINER 三服务容器与版本化镜像契约设计

## 1. 背景与边界

本设计对应 GitHub Issue #289，以通过父任务 #286 门禁后的最新 `origin/dev` 为最终实施基线。目标是让前端、后端和官方 MySQL 8.4 镜像分别运行在独立容器中，并让前后端镜像可被本地 Compose、后续 GitHub Actions 和 Kind/Kubernetes 通过同一组参数复用。

本任务只修改容器构建、Compose 参数、验证脚本、契约测试和必要说明。不实现 GitHub Actions，不新增 Kubernetes 资源，不复制或改变数据库 schema，也不修改业务 API、DTO、权限或数据库语义。增强评测镜像 `backend.eval.Dockerfile` 保留既有职责；除非共享契约测试证明必须同步，否则不扩展本 Issue 的自建镜像范围。

## 2. 方案比较

### 方案 A：固定镜像名、统一 `GIT_SHA` 与脚本入口（采用）

按已合并的 `docs/开发/D3-CICD-共享契约.md`，Compose、构建脚本和烟测脚本只消费 `GIT_SHA`。`GIT_SHA` 必须是当前 checkout 的完整 40 位 Git SHA；镜像名固定为 `onlinejudge/backend:${GIT_SHA}` 与 `onlinejudge/frontend:${GIT_SHA}`，OCI `org.opencontainers.image.revision` 保存同一完整 SHA。

优点是 Compose、Actions 与 Kubernetes 只有一个版本事实且不存在同义变量。缺点是本地启动必须显式提供版本值，但这正好满足“缺失版本号必须失败”的验收要求。

### 方案 B：构建时生成带固定标签的 Compose 文件

脚本把 SHA 写入临时或提交后的 Compose 文件。优点是最终文件直观，缺点是容易产生漂移和无关 diff，也会让 CI、Kubernetes 和本地脚本维护多份来源，因此不采用。

### 方案 C：只通过镜像 digest 运行

Digest 能提供最强的不可变引用，但本地构建尚未推送仓库时不便跨环境复用，也不满足本 Issue 对 Git SHA 标签的直接要求。后续交付流水线可以在推送镜像后额外记录 digest，本任务不以 digest 取代 SHA 标签。

## 3. 镜像与参数契约

统一变量如下：

| 参数 | 规则 | 默认值 |
| --- | --- | --- |
| `GIT_SHA` | 必填，当前源码完整 40 位十六进制 Git SHA | 无 |
| `OJ_HTTP_PORT` | 本地前端暴露端口 | `8088` |

构建脚本对缺失、`latest`、非 40 位或非当前 checkout 的 `GIT_SHA` 返回非零，避免错误 revision 被写入镜像。每个自建镜像只按共享契约生成固定仓库名的完整 SHA 标签；验证和 Compose 主流程不依赖 `latest`，也不发明短 SHA 或可覆盖仓库名。

Dockerfile 通过构建参数接收完整 SHA，并写入以下 OCI 标签：

- `org.opencontainers.image.revision`
- `org.opencontainers.image.version`
- `org.opencontainers.image.source`

版本与 revision 均来自已校验的完整 SHA；不写入随每次构建变化的时间戳，避免无意义地扩大构建差异。

## 4. Dockerfile 与构建上下文

后端继续使用 Java 21 多阶段构建和 Maven 缓存挂载，依赖解析只复制 `pom.xml`，业务源码变化不会无条件破坏依赖缓存。运行阶段创建专用非 root 用户，应用 JAR、上传目录和健康探针工具均对该用户可用。

前端继续使用 Node 22、`npm ci` 和 Nginx 多阶段构建，依赖层只复制 package 清单与 lockfile，并使用 BuildKit npm 缓存。运行阶段使用非 root Nginx 用户，保持共享契约规定的容器端口 `80`；Nginx pid、缓存和临时目录显式设为该用户可写，不改变 `/api/ -> backend:8080` 路由。

`.dockerignore` 排除 Git 元数据、IDE 文件、依赖目录、构建产物、测试输出、临时目录、环境文件、密钥/证书常见扩展名和不参与镜像构建的提交物。Dockerfile 仍只复制自身构建所需目录，降低误带本地文件和缓存失效的风险。

MySQL 固定引用官方 `mysql:8.4`，不创建数据库 Dockerfile。Compose 继续挂载 `database/mysql/compose-schema.sql` 作为当前唯一初始化正本；#287 若替换初始化入口，本任务只重接该入口，不复制 SQL。

## 5. Compose 与运行流程

`deploy/docker/compose.yml` 为前后端同时声明固定仓库名的 `${GIT_SHA}` 精确 `image` 引用与已有 `build` 配置。直接解析 Compose 时缺失 `GIT_SHA`、`MYSQL_PASSWORD` 或 `MYSQL_ROOT_PASSWORD` 即失败；构建脚本负责构建，烟测脚本使用 `--no-build` 启动已构建的完整 SHA 镜像，防止烟测期间隐式产生其他内容。`.env.example` 只保留 Secret 键名，不提供演示口令；Compose profile 的 datasource password 也不再回退到仓库口令。

后端保留 `/api/v1/system/health` 作为不访问数据库的 liveness，并新增匿名可访问的 `/api/v1/system/readiness`。Readiness 必须通过配置的数据源执行 `SELECT 1`；成功返回 HTTP 200 与 `data.status="UP"`，数据库不可连接或查询失败时返回 HTTP 503，响应不得含 `status:"UP"`、连接串、用户名或口令。Compose backend healthcheck 切换到 readiness，前端代理烟测也调用 readiness，从而证明 `frontend -> backend -> mysql` 完整可用。

烟测使用独立 Compose project name，并在退出时只清理该 project 的容器、网络和卷。启动采用 Compose 健康检查和有界等待，不使用固定 sleep。通过条件包括：

1. MySQL、后端、前端三个服务均为 healthy；
2. MySQL 实际镜像为 `mysql:8.4`；
3. 前后端实际镜像引用完整 SHA 标签；
4. 两个镜像的 OCI revision 等于输入 `GIT_SHA`；
5. 两个应用容器的运行用户不是 root；
6. 前端静态入口和后端健康接口可访问；
7. 后端 readiness 与经前端代理的 readiness 均成功，证明后端连接 MySQL及三服务链路可用；现有登录/核心只读验收继续作为业务闭环证据。

构建、启动或健康检查任一失败时，脚本保留非零退出码，输出 Compose 状态与相关日志，然后执行精确清理。

## 6. 测试与证据

测试按 Red-Green-Refactor 执行：

1. 扩展 Java Compose/Dockerfile 静态契约测试，先证明当前配置缺少必填 SHA、OCI revision、非 root 用户和精确镜像引用；确认按预期失败。
2. 扩展 `SystemHealthControllerTest` 与 Compose profile 测试，先证明匿名 readiness、数据库失败 503 和无密码 fallback 尚未满足；确认按预期失败。
3. 在 `scripts/docker/**` 新增 Shell 脚本测试，用可控的假 Docker 命令证明缺失/错误 `GIT_SHA`、构建失败和健康失败均返回非零；确认按预期失败。
4. 写最小 readiness、Dockerfile、Compose 和脚本实现使定向测试通过。
5. 重构共享参数校验，复跑全部定向测试。
6. 在 Docker Desktop Linux Engine 上执行真实前后端构建和三容器烟测；记录 Docker/Compose 版本、完整 Git SHA、镜像数量、healthy 服务数量、命令退出码和原始日志。
7. 最后运行后端相关测试、Shell 测试、前端类型检查/构建、Compose 配置解析和 `git diff --check`。

脚本以 Linux、GitHub-hosted Runner 和后续 Kind 环境为目标；Windows Git worktree 可通过 Git Bash 执行，原生 WSL checkout 可直接使用 Linux Shell。

## 7. 门禁、协作与交付

2026-08-27 复核确认 #272、#275、#276 均已合入，当前分支已重放到 `origin/dev@54e47e90e0c8f7c8031dcd0f760d6756ce8f8dfe`。实施继续执行：

1. 获取最新远端并把本分支重放到最新 `origin/dev`；
2. 复查 #287 的数据库初始化入口以及 #288/#290 使用的镜像参数名；
3. 完成 GREEN、真实容器烟测和全套验证；
4. 按类型拆分测试、实现和文档提交；
5. 推送分支，创建目标为 `dev` 的非草稿 PR，描述包含真实验证结果、风险、AI 使用说明和 `Closes #289`；
6. 等待自动评审并由项目负责人终审，不自行合并。

Project 状态若因当前 GitHub Token 缺少 `read:project` 无法通过 CLI 更新，将在 Issue/PR 中明确记录，不伪造 Project 更新证据。
