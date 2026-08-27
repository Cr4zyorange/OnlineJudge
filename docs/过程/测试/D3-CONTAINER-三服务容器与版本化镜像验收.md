# D3-CONTAINER 三服务容器与版本化镜像验收

## 1. 验收范围

本文对应 Issue #289，验证前端、后端和官方 MySQL 8.4 三服务容器、完整 `GIT_SHA` 镜像契约、OCI 追溯标签、非 root 运行用户、数据库 Secret 边界、数据库感知 readiness 以及失败码传播。不包含 GitHub Actions、Kubernetes 资源、自制 MySQL 镜像或数据库 schema 复制。

共享契约以 `docs/开发/D3-CICD-共享契约.md` 为准：版本变量只有当前 checkout 的完整 40 位 `GIT_SHA`，镜像名固定为 `onlinejudge/backend:${GIT_SHA}` 和 `onlinejudge/frontend:${GIT_SHA}`，不生成 `latest` 或短 SHA 镜像别名。

## 2. Red / Green 记录

| 阶段 | 命令或用例 | 实际结果 |
| --- | --- | --- |
| RED：静态与 readiness 契约 | Maven 定向执行 `DockerComposeContractTest`、`SystemHealthControllerTest`、`SystemReadinessControllerFailureTest`、`ComposeProfilePropertiesTest` | 15 项中 6 failures、1 context fixture error；失败点包含旧密码 fallback、缺少 readiness、固定 SHA 镜像、OCI/non-root 与 `.dockerignore`。将故障夹具改为应用启动后注入后，503 用例稳定表现为预期 RED（401 而非 503）。 |
| RED：构建入口 | `scripts/docker/tests/build-images.test.sh` | 失败，标准构建入口尚不存在，缺失 `GIT_SHA` 诊断无法满足。 |
| RED：烟测入口 | `scripts/docker/tests/smoke-images.test.sh` | 失败，标准烟测入口尚不存在，缺失 `GIT_SHA` 诊断无法满足。 |
| GREEN：静态与 readiness 契约 | 同一 Maven 定向命令 | 15 tests，0 failures，0 errors，0 skipped。 |
| GREEN：构建入口 | Git Bash 执行 `scripts/docker/tests/build-images.test.sh` | PASS；覆盖缺失/非法/不匹配 SHA、Docker 构建失败、固定双镜像及禁止别名。 |
| GREEN：烟测入口 | Git Bash 执行 `scripts/docker/tests/smoke-images.test.sh` | PASS；覆盖启动/健康/镜像/revision/root/readiness/业务验证失败和限定清理。 |
| GREEN：旧卷升级入口 | Git Bash 执行 `scripts/docker/tests/migrate-app-data.test.sh` | PASS；覆盖版本、卷名、卷不存在、迁移失败、UID 10001 读写复核和失败码传播。 |
| 既有业务验收脚本回归 | WSL 执行 `scripts/test/verify-compose.test.sh` | PASS。Git for Windows 对含换行和反斜杠凭据的进程参数语义不同，因此按脚本目标 Linux 环境复核。 |

## 3. 真实环境验收步骤

在最终源码提交后执行，确保用于构建和烟测的 SHA 与执行时 `HEAD` 完全一致：

```bash
export GIT_SHA="$(git rev-parse HEAD)"
export MYSQL_PASSWORD='<一次性烟测强密码>'
export MYSQL_ROOT_PASSWORD='<一次性烟测 root 强密码>'

mkdir -p output/issue-289
./scripts/docker/build-images.sh 2>&1 | tee output/issue-289/build.log
./scripts/docker/smoke-images.sh 2>&1 | tee output/issue-289/smoke.log
```

必须保留并在 PR 描述中记录：Docker/Compose 版本、完整 `GIT_SHA`、构建镜像数、healthy 服务数、检查路径数、两个命令退出码和原始日志路径。真实烟测成功标准为：3 个服务 healthy，2 个固定 SHA 镜像引用和 OCI revision 一致，2 个应用容器均非 root，后端与前端代理 readiness 均为 UP，业务只读验收通过，清理仅影响 `onlinejudge-smoke-<SHA 前 12 位>` 项目。

## 4. 失败与安全断言

- `GIT_SHA` 缺失、非 40 位或不等于当前 `HEAD` 时，在 Docker 构建/启动前返回非零。
- 工作树包含已跟踪或未跟踪改动时，构建入口返回非零，禁止给未提交内容标注当前 `HEAD` revision。
- `MYSQL_PASSWORD`、`MYSQL_ROOT_PASSWORD` 缺失时 Compose 和烟测入口返回非零；`.env.example` 不提供口令。
- 数据库查询失败时 readiness 返回 HTTP 503，不返回 `status="UP"`，不泄漏 JDBC URL、用户名或口令。
- 构建、Compose 启动、healthy 数量、镜像名、OCI revision、运行用户、两条 readiness 或业务验收任一不符时，烟测返回非零并输出限定项目诊断。
- MySQL 只引用 `mysql:8.4`，初始化仍只挂载 `database/mysql/compose-schema.sql`，本任务不复制 schema。
- 历史 root 所有权的 `app-data` 卷只通过显式迁移入口原地调整为 UID/GID 10001；脚本先检查卷存在，迁移后再以非 root 身份验证可读写，不删除数据卷。

## 5. 证据状态

本文提交时已完成第 2 节的自动化 Red/Green。第 3 节真实镜像构建与烟测必须在包含本文的最终提交 SHA 上运行；其动态结果写入忽略的 `output/issue-289/` 并同步到 PR，不以修改本文的方式改变待验证 SHA。
