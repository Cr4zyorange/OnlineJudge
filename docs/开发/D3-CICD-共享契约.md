# D3 CI/CD 共享契约

> **唯一正本。** #289、#290、#288 与 #292 在 D3 中只能消费本文件定义的服务名、镜像引用、变量、Secret 键和健康断言；不得在各自的 Compose、workflow、Kind 脚本或 Kubernetes 清单中另行发明同义名称。
>
> **事实基线：** `origin/dev@50a5dccd35ddc6b0c8936df20217575f18303a4f`（2026-08-27）。除本文件明确标记的“D3 统一值”外，下列值均是该基线的现有事实。本任务只冻结接口，不修改部署实现。

## 1. 使用规则

| 规则 | 规范 |
| --- | --- |
| 服务范围 | 仅 `mysql`、`backend`、`frontend` 三项服务；Redis、生产域名、TLS、云资源和长期集群不属于 D3。 |
| 网络边界 | `mysql` 不发布宿主端口；`backend` 仅在部署内部网络监听 `8080`；只有 `frontend` 发布 HTTP 入口。 |
| 启动顺序 | `mysql` 健康后启动 `backend`，`backend` 健康后启动 `frontend`。应用访问方向为 `frontend -> backend -> mysql`；反向方向不得建立业务依赖。 |
| 配置承载 | 非敏感配置使用环境变量；Kubernetes 由 ConfigMap 注入，Compose/Actions 使用同名环境变量。敏感值只由 Secret 或 GitHub Secrets 注入。 |
| 版本追溯 | 两个自建镜像必须以一次构建的完整 Git SHA 标记，且写入同一个 OCI revision label；不得构建、部署或验收仅带 `latest` 的自建镜像。 |
| 存活与就绪 | `health` 只证明后端进程可响应，不能代替数据库检查；`readiness` 必须实际执行受控的数据库 `SELECT 1`。Compose 的 backend healthcheck 和 Kubernetes 的 startup/readiness 只使用 readiness，Kubernetes liveness 只使用 health。 |
| 数据库正本 | schema/migration/seed 继续由 #287 维护的现有正本提供；#289、#288、#292 只能引用，不能复制第二份 SQL。 |

## 2. 三服务与网络契约

| 逻辑服务名 | 实现/镜像 | 容器端口 | 宿主端口 | 启动依赖 | 允许的出站依赖 | 规范用途 |
| --- | --- | ---: | --- | --- | --- | --- |
| `mysql` | 官方 `mysql:8.4` | `3306` | 不发布 | 无 | 无 | 关系数据持久化；Compose 初始化继续挂载 `database/mysql/compose-schema.sql`。 |
| `backend` | 自建 `onlinejudge/backend:${GIT_SHA}` | `8080` | 不发布 | `mysql` Healthy/Ready | `mysql:3306` | Spring Boot API；只接受内部网络和 `frontend` 的反向代理流量。 |
| `frontend` | 自建 `onlinejudge/frontend:${GIT_SHA}` | `80` | `${OJ_HTTP_PORT:-8088}:80`（Compose）；Kind 由临时端口转发或 ClusterIP 访问 | `backend` Healthy/Ready | `backend:8080`，仅经 Nginx `/api/` 代理 | Vue 静态资源和唯一 HTTP 入口。 |

### 2.1 名称与路由不变量

| 项目 | 唯一规范值 | 基线核对 |
| --- | --- | --- |
| Compose/Kubernetes 服务 DNS 名 | `mysql`、`backend`、`frontend` | `deploy/docker/compose.yml` 已采用三项服务名。 |
| 后端地址 | `http://backend:8080` | `deploy/nginx/default.conf` 的 `proxy_pass http://backend:8080;`。 |
| 反向代理前缀 | `/api/` | Nginx 将整个 `/api/` 前缀转发到后端；健康接口因此经前端访问为 `/api/v1/system/health`。 |
| 后端探针路径 | liveness 为 `/api/v1/system/health`；数据库 readiness 为 `/api/v1/system/readiness`；两者都允许无 `Authorization` 请求 | 基线只有前者被鉴权白名单排除；后者是本契约新增的 D3 实现项，语义见第 5 节。 |
| 公共 HTTP 端口输入 | `OJ_HTTP_PORT` | Compose 默认值为 `8088`；这是唯一可改变本地前端宿主端口的输入，不用于数据库或后端端口。 |
| 数据库连接 | `MYSQL_HOST=mysql`、`MYSQL_PORT=3306` | `application-compose.properties` 与 Compose 后端环境变量逐项一致。 |

## 3. 镜像、标签和源码修订契约

| 项目 | 唯一规范 | 生产者 | 消费者/验证方式 |
| --- | --- | --- | --- |
| 后端镜像仓库名 | `onlinejudge/backend` | #289 构建脚本与 Dockerfile | Compose、Kind/Kubernetes、#292 流水线引用 `onlinejudge/backend:${GIT_SHA}`。 |
| 前端镜像仓库名 | `onlinejudge/frontend` | #289 构建脚本与 Dockerfile | Compose、Kind/Kubernetes、#292 流水线引用 `onlinejudge/frontend:${GIT_SHA}`。 |
| 版本输入 | `GIT_SHA`，必须是本次 checkout 的完整 40 位提交 SHA | 本地脚本通过 `git rev-parse HEAD` 取得；Actions 使用 `${{ github.sha }}` 后以同名环境变量传递 | #289 拒绝空值、非当前 checkout 或 `latest`；#288、#292 只部署该值对应的镜像。 |
| 自建镜像标签 | `${GIT_SHA}`，即 `onlinejudge/<component>:${GIT_SHA}` | #289 | `docker image inspect`、Compose image 引用和 Kubernetes Pod 实际 image 必须全量匹配。 |
| 源码 revision label | `org.opencontainers.image.revision=${GIT_SHA}` | #289 在两个 Dockerfile 以 build arg 写入 | `docker image inspect` 或 OCI manifest 读取该 label，值必须等于 image tag 的 `GIT_SHA`。 |
| MySQL 镜像 | `mysql:8.4` | #289 只引用官方镜像 | #288、#292 原样消费；不要求也不伪造本仓库源码 revision label。 |

`GIT_SHA` 是版本事实，不是 Secret；镜像仓库名和标签按上表写死以保证本地 Docker、Kind 和 GitHub-hosted Runner 使用完全相同的引用。若以后引入远程 registry，必须另开 Issue 修改本契约，并同时更新四个消费者；不得通过为每个环境新增不同变量绕过本表。

## 4. 环境变量与敏感配置边界

### 4.1 共享非敏感变量

除 `GIT_SHA`（构建/部署输入）与 `OJ_HTTP_PORT`（Compose 宿主端口输入）外，这些键名在 Compose 的 `environment`、Actions 的 job/step `env` 与 Kubernetes ConfigMap `data` 中保持完全一致。表中的“允许默认值”仅指 D3 的 DEV/FAT 演示环境；脚本仍必须记录运行时解析后的值（但不得记录 Secret 值）。

| 变量 | D3 统一值/语义 | 来源 | 允许默认值 | 消费方 |
| --- | --- | --- | --- | --- |
| `GIT_SHA` | 完整 40 位提交 SHA；镜像 tag 与 OCI revision label 的唯一输入 | 本地 `git rev-parse HEAD` 或 Actions `${{ github.sha }}` | 否 | #289、#288、#290、#292。 |
| `OJ_HTTP_PORT` | Compose 前端宿主端口，默认 `8088`；不进入 Kubernetes Pod 配置 | Compose/本地 shell | 是，`8088` | #289 本地烟测、#292 本地复现；浏览器访问入口。 |
| `MYSQL_HOST` | 数据库服务 DNS 名；Compose 与 Kubernetes 都为 `mysql` | Compose environment / Kubernetes ConfigMap | 是，`mysql` | `backend`、#289、#288、#292。 |
| `MYSQL_PORT` | 数据库容器端口 | Compose environment / Kubernetes ConfigMap | 是，`3306` | `backend`、#289、#288、#292。 |
| `MYSQL_DATABASE` | 业务数据库名 | Compose environment / Kubernetes ConfigMap | 是，`onlinejudge` | `mysql` 初始化和 `backend` JDBC。 |
| `MYSQL_USER` | 应用数据库用户名；用户名不是口令，但不得在日志中与 Secret 拼接输出 | Compose environment / Kubernetes ConfigMap | 是，`onlinejudge` | `mysql` 初始化和 `backend` JDBC。 |
| `ONLINEJUDGE_DEMO_DATA_ENABLED` | 是否加载演示数据 | Compose environment / Kubernetes ConfigMap | 是，`true` | `backend`。 |
| `ONLINEJUDGE_EVALUATION_SANDBOX_MODE` | 评测模式：`fake` 或 `docker` | Compose environment / Kubernetes ConfigMap | 是，`fake` | `backend`、#289 增强评测 Compose 覆盖层。 |
| `IDENTITY_JWKS_URI` | Identity JWKS 远程刷新地址；D3 三服务基线未部署独立 Identity，必须显式为空以只使用离线信任包 | Kubernetes ConfigMap | 是，空字符串 | `backend`、#288、#292。 |
| `ONLINEJUDGE_EVALUATION_DOCKER_COMMAND` | Docker 评测 CLI 命令 | Compose 增强评测覆盖层 / Kubernetes ConfigMap（仅 docker 模式） | 是，`docker` | `backend` 的 Docker sandbox。 |
| `ONLINEJUDGE_EVALUATION_DOCKER_PYTHON_IMAGE` | Docker 评测 Python 基础镜像 | Compose 本地增强评测覆盖层 / Kubernetes ConfigMap（仅 docker 模式） | 是，`python:3.12-alpine` | `backend` 的 Docker sandbox。 |

`SANDBOX_WORKDIR`、`JAVA_TOOL_OPTIONS`、CPU/PID/tmpfs 限制是 #289 的本地增强评测覆盖层实现细节，不是三服务跨环境接口；它们不得替代或改名本表中的任一配置键。

### 4.2 Secret 键（只列键名，绝不提交值）

| Secret 键 | D3 边界 | 注入位置 | 允许默认值 | 消费方 |
| --- | --- | --- | --- | --- |
| `MYSQL_PASSWORD` | 应用数据库用户口令 | Compose 本地 `.env`/受控环境、GitHub Secrets、Kubernetes Secret -> `backend` 与 `mysql` | 否 | `mysql` 初始化、`backend` JDBC、#289/#288/#292。 |
| `MYSQL_ROOT_PASSWORD` | MySQL 管理员口令，仅用于 MySQL 初始化和 `mysqladmin` 健康探测 | Compose 本地 `.env`/受控环境、GitHub Secrets、Kubernetes Secret -> `mysql` | 否 | `mysql`、#289/#288/#292。 |
| `ONLINEJUDGE_NOTIFICATIONS_INTERNAL_TOKEN` | 内部通知调用鉴别令牌 | GitHub Secrets、Kubernetes Secret -> `backend`；本地受控环境变量 | 否；功能未启用时可不注入，但不得以仓库内空字符串冒充 Secret | `backend`、#290/#292。 |
| `IDENTITY_JWKS_TRUST_BUNDLE` | 后端离线 JWT 校验使用的公开 JWKS；D3 部署脚本每次运行生成临时 RSA 私钥并只注入派生的公钥包，私钥不得落盘、输出或归档 | Kubernetes Secret -> `backend` | 否；由 #288 部署入口在触碰集群前生成 | `backend`、#288/#292。 |

ConfigMap 只保存 4.1 的非敏感键；Secret 只保存本节键名及由平台在运行时提供或生成的值。Actions 日志、`kubectl describe`、Compose 输出和归档证据必须遮蔽 Secret 值，且不能把 `.env`、Secret YAML 实值、临时私钥或私有 registry 凭据加入镜像构建上下文。

### 4.3 现值到 D3 统一值的显式差异

| 基线现值 | D3 统一值 | 受影响消费者 |
| --- | --- | --- |
| Compose 只 `build` 自建服务；没有固定 image 名、`GIT_SHA` 输入或 OCI revision label。 | 两个自建镜像固定为 `onlinejudge/backend:${GIT_SHA}`、`onlinejudge/frontend:${GIT_SHA}`，并都具有 `org.opencontainers.image.revision=${GIT_SHA}`。 | #289 负责实现；#288、#290、#292 只按该引用消费。 |
| `deploy/docker/compose.yml` 给 `MYSQL_PASSWORD` 默认 `onlinejudge`、给 `MYSQL_ROOT_PASSWORD` 默认 `root`；`deploy/docker/.env.example` 也提供演示口令。 | 两项改为只从 Secret/受控环境注入；Compose 用必填插值拒绝缺失值，`.env.example` 只保留键名与“由操作者设置”说明，不提供默认口令。 | #289 同步 Compose、`.env.example`、容器烟测和权威部署文档；#288 建 Secret 引用；#290/#292 从 GitHub Secrets 注入并遮蔽。 |
| `backend/src/main/resources/application-compose.properties` 含 `spring.datasource.password=${MYSQL_PASSWORD:onlinejudge}` fallback。 | 改为无 fallback 的 `${MYSQL_PASSWORD}`；缺失键必须使 Compose profile 启动失败，不能回退到仓库中的口令。 | #289 同步该配置、其自动化测试和部署文档；#288/#290/#292 只注入 Secret。 |
| `ONLINEJUDGE_NOTIFICATIONS_INTERNAL_TOKEN` 在 Compose 与 Spring 配置中可为空。 | 键名不变，作为 Secret；未启用内部通知可缺省，启用时必须注入且不得记录值。 | #289、#288、#290、#292。 |
| `SystemHealthController.health()` 不访问 datasource，Compose、前端代理验收和后续 Kind 设计若只调用 `/api/v1/system/health`，数据库不可用仍可能通过。 | 保留 `/api/v1/system/health` 作为 liveness；新增数据库感知的 `/api/v1/system/readiness`。后者必须执行 `SELECT 1`，不可连接时返回 HTTP `503` 且不返回 `UP`。 | #289 实现与测试并切换 Compose；#288 设置 probes；#292 执行端到端 readiness 断言。 |
| `WebMvcConfig` 的 `AuthRequiredInterceptor` 仅排除 `/api/v1/system/health`，新增 readiness 路由会先返回 `401`。 | 同样排除 `/api/v1/system/readiness`；探针请求不得携带业务用户凭据。 | #289 修改 `WebMvcConfig` 并以无 `Authorization` 的 `200/UP` 和 `503` 测试证明；#288/#292 按无凭据探针调用。 |
| `mysql:8.4`、`3306`，后端 `8080`，前端 `80`、`${OJ_HTTP_PORT:-8088}` 及 Nginx `/api/ -> backend:8080` 已存在。 | 保持原值；不得以新同义名替换。 | #289、#288、#290、#292。 |

## 5. 健康、就绪与可脚本化断言

所有健康检查都必须在设定的 timeout/retry 范围内执行；禁止用固定 `sleep` 代替。下面的命令表达成功条件，具体脚本可在容器内、端口转发后或临时测试 Pod 内执行，但必须保留原始退出码和输出。

| 目标 | Liveness/Ready 地址或命令 | 成功条件 | 当前基线核对与实现责任 |
| --- | --- | --- | --- |
| `mysql` | `mysqladmin ping -h 127.0.0.1 -uroot -p$MYSQL_ROOT_PASSWORD --silent` | 退出码 `0`；命令必须使用 Secret 注入的 root 口令。 | `deploy/docker/compose.yml` 已以该命令为 healthcheck。 |
| `backend` liveness | `GET http://127.0.0.1:8080/api/v1/system/health` | HTTP `200`，JSON 包含 `"status":"UP"`；基线响应的完整断言为 `code == "0"`、`message == "success"`、`data.status == "UP"`。此探针不访问 datasource，数据库不可用不应触发 liveness 重启。 | 基线 `SystemHealthController` 与 `SystemHealthControllerTest` 已满足该语义。 |
| `backend` readiness | 无 `Authorization` 的 `GET http://127.0.0.1:8080/api/v1/system/readiness` | 成功时必须通过配置的数据源执行 `SELECT 1`，返回 HTTP `200` 与 `data.status == "UP"`；数据库不可连、账号口令无效或查询失败时返回 HTTP `503` 且响应不得含 `"status":"UP"`，也不得泄露连接串或口令。 | 基线没有此接口且 `WebMvcConfig` 未放行该路径；#289 负责实现和 RED/GREEN 测试：无凭据的受控 datasource 失败先断言 `503`，可用 datasource 再断言 `200/UP`，不得返回 `401`。 |
| `frontend` 静态服务 | `GET http://127.0.0.1/` | HTTP `200`，响应包含 `<!doctype html>`。 | `deploy/docker/compose.yml` 的 frontend healthcheck。 |
| `frontend -> backend` 代理连通性 | `GET http://frontend/api/v1/system/readiness`（或将 frontend 临时端口转发后访问同一路径） | HTTP `200`，JSON 包含 `"status":"UP"`；证明 Nginx `/api/` 代理、backend 与 MySQL 均可用，而不是只证明静态页或后端进程存在。 | Nginx 已代理至 `backend:8080`；#289 补 Compose 验收，#288/#292 必须补这条跨服务断言。 |

Kubernetes 的 MySQL probes 使用 `mysqladmin ping`；backend 的 `startupProbe` 与 `readinessProbe` 使用 `/api/v1/system/readiness`，`livenessProbe` 使用 `/api/v1/system/health`；frontend probes 使用 `/`。Compose 的 backend healthcheck 必须切换到 readiness，`depends_on.condition: service_healthy`、Kubernetes rollout/Ready 等待和 #292 的交付放行都以 readiness 成功为条件；端到端代理断言作为部署验收脚本单独执行。

## 6. Issue 文件责任与接口边界

| Issue | 唯一负责的文件/目录 | 读取的契约输入 | 对下游的稳定输出 | 明确不负责 |
| --- | --- | --- | --- | --- |
| #293 `D3-CONTRACT` | `docs/开发/D3-CICD-共享契约.md` | 基线的 Compose、Dockerfile、Nginx、后端配置与本表事实 | 本共享契约 | 不修改任何 Dockerfile、Compose、workflow、Kubernetes 清单或业务代码。 |
| #289 `D3-CONTAINER` | `deploy/docker/**`、`.dockerignore`、`scripts/docker/**`（如需新增）；`backend/src/main/java/com/onlinejudge/common/controller/SystemHealthController.java`、`backend/src/main/java/com/onlinejudge/common/config/WebMvcConfig.java`、`backend/src/main/resources/application-compose.properties`、`backend/src/test/java/com/onlinejudge/common/SystemHealthControllerTest.java`；`docs/最终提交/部署文档.md`、`docs/最终提交/软件实现说明书.md`、`docs/最终提交/测试文档.md` | 服务名、`GIT_SHA`、镜像名、变量、Secret 边界和第 5 节断言 | 可构建的两个带 tag/OCI label 的镜像；无 password fallback 的 Compose profile；匿名可访问的 liveness/readiness 实现和 RED/GREEN 测试；同步后的权威部署/实现/测试文档；Compose 与容器烟测入口 | 不创建 GitHub Actions 或 Kubernetes 资源，也不改变业务接口语义。 |
| #290 `D3-CI` | `.github/workflows/ci.yml`、`scripts/ci/**`（如需新增） | 仓库测试正本、`GIT_SHA`、Secret 键名和 #289 的构建入口 | PR/dev push 质量门禁、测试报告和可审计环境/SHA 清单 | 不创建 `deploy/k8s/**`，不实现端到端 Kind 部署工作流。 |
| #288 `D3-K8S` | `deploy/k8s/**`、`scripts/kind/**`（如需新增） | 三服务名、镜像精确引用、ConfigMap/Secret 边界、健康断言和 #287 schema 正本 | 可重复部署/精确清理的 Kind 基线，以及 Ready/连通性诊断入口 | 不修改 Dockerfile、Compose、质量门禁 workflow 或 schema 正本。 |
| #292 `D3-DELIVERY` | `.github/workflows/d3-delivery.yml`、`scripts/delivery/**`（如需新增） | #290 质量门禁结果、#289 镜像构建输出、#288 Kind 部署/清理入口、`GIT_SHA` 和第 5 节断言 | 串联质量门禁、版本化镜像、Kind 验收、诊断归档和清理的端到端流水线 | 不复制 #289 的构建实现、不创建第二套 Kubernetes 清单、不弱化 #290 门禁。 |

`#290` 与 `#292` 都可位于 `.github/workflows/`，但文件名按上表严格分离：前者只做质量门禁，后者只编排已完成的质量门禁、镜像与 Kind 交付。`#289`、`#288`、`#292` 都可引用 `database/mysql/compose-schema.sql` 或 #287 后续确定的同一 schema 正本，但任何一方都不得复制 SQL。

## 7. 消费前检查清单

- [ ] 所有引用仅使用 `mysql`、`backend`、`frontend` 三个服务名，端口与方向符合第 2 节。
- [ ] 两个自建 image 的 tag 和 `org.opencontainers.image.revision` 都等于同一个完整 `GIT_SHA`，且没有 `latest` 作为唯一 tag。
- [ ] 非敏感键来自同名 environment/ConfigMap，敏感键只来自 Secret、GitHub Secrets 或部署时生成的公钥信任包，证据中没有值；临时私钥不落盘、不输出、不归档；Compose、`.env.example` 和 `application-compose.properties` 没有密码 fallback，三份权威文档同步说明必填 Secret 输入。
- [ ] MySQL、backend liveness、backend readiness、frontend 静态服务和 frontend-to-backend readiness 均按第 5 节断言；无 `Authorization` 的 readiness 在数据库可用时必须成功、数据库不可用时必须失败，且失败时保留原始输出并返回非零。
- [ ] 变更只触及责任表中本 Issue 的路径；若需要改变本契约，先更新本文件并在相关 Issue/PR 中说明消费者影响。

## 8. 本文件核对记录

| 项目 | 环境 | 基线 SHA | 命令 | 原始结果 |
| --- | --- | --- | --- | --- |
| 文档空白与格式检查 | macOS 本地工作树 | `50a5dccd35ddc6b0c8936df20217575f18303a4f` | `git diff --check` | `exit 0`；stdout 为空。 |
| 服务/变量/健康事实检查 | macOS 本地工作树 | `50a5dccd35ddc6b0c8936df20217575f18303a4f` | `sed -n` 检查 `deploy/docker/compose.yml`、`deploy/nginx/default.conf`、三个 Dockerfile、`application-compose.properties`、`SystemHealthController` 和 `SystemHealthControllerTest` | 所列文件均读取成功；第 2～5 节已逐项冻结。实现 Issue 必须在其自身 PR 重跑可执行验证。 |
