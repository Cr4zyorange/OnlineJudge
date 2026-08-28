# Kind 三服务 Kubernetes 部署基线(issue #288 / D3-K8S)

本目录与 `scripts/kind/` 共同构成 #288 的交付物:在没有额外服务器的前提下,用本地 Docker Desktop/Kind 或 GitHub-hosted Runner 一条命令把 `mysql`、`backend`、`frontend` 三服务部署进干净的 `onlinejudge-ci` 命名空间,并完成健康断言与精确清理。所有服务名、镜像引用、变量、Secret 键与探针路径均以 `docs/开发/D3-CICD-共享契约.md`(#293)为唯一正本。

## 组成

| 文件 | 职责 |
| --- | --- |
| `00-namespace.yaml` | 独立 CI 命名空间 `onlinejudge-ci`;清理脚本的唯一删除范围 |
| `01-configmap.yaml` | 非敏感共享配置(契约 4.1 的键与默认值) |
| `02-secret.example.yaml` | Secret 键名示例;真实值仅在部署时由环境注入,绝不入库 |
| `10-mysql-statefulset.yaml` / `11-mysql-service.yaml` | MySQL 8.4 StatefulSet + headless Service + PVC;schema 经部署期 ConfigMap 挂载 `database/mysql/compose-schema.sql` 正本 |
| `20-backend-deployment.yaml` / `21-backend-service.yaml` | 后端 Deployment(ClusterIP 8080);startup/readiness 探针用 `/api/v1/system/readiness`,liveness 用 `/api/v1/system/health` |
| `30-frontend-deployment.yaml` / `31-frontend-service.yaml` | 前端 Deployment(唯一 HTTP 入口,ClusterIP 80),经 `kubectl port-forward` 访问 |
| `kind-cluster.yaml` | 单 control-plane 节点,节点镜像固定 `kindest/node:v1.36.1` |

## 使用

前置:`docker`、`kind`、`kubectl` 可用;两个自建镜像已按 #289 的构建入口打上同一完整 Git SHA 标签(`onlinejudge/backend:${GIT_SHA}`、`onlinejudge/frontend:${GIT_SHA}`)。

```bash
export GIT_SHA=<40位提交SHA>
export MYSQL_PASSWORD=<由操作者或 GitHub Secrets 注入>
export MYSQL_ROOT_PASSWORD=<由操作者或 GitHub Secrets 注入>
# 可选:ONLINEJUDGE_NOTIFICATIONS_INTERNAL_TOKEN

scripts/kind/k8s-deploy.sh     # 一条命令:建/复用集群 → 装载镜像 → 渲染 → 部署 → 有界等待三服务 Ready
scripts/kind/k8s-verify.sh     # 契约断言:Ready 数、镜像标签精确匹配、mysql ping、readiness、前端代理
scripts/kind/k8s-diagnose.sh   # 手动诊断:导出 events/pods/describe/logs/rollout(部署失败时自动执行)
scripts/kind/k8s-cleanup.sh            # 精确清理:仅删除 onlinejudge-ci 命名空间
scripts/kind/k8s-cleanup.sh --cluster  # 连同 kind 集群一起删除
```

所有 `kubectl` 调用都固定 `--context kind-onlinejudge-ci`;等待一律使用 `--timeout` 有界参数,不使用固定 sleep。

## 网络与镜像下载说明

- 三服务均为 ClusterIP/headless,不发布宿主端口;前端经临时 `kubectl port-forward` 访问(默认本地端口 `18080`/`18088`,可用 `VERIFY_BACKEND_LOCAL_PORT`/`VERIFY_FRONTEND_LOCAL_PORT` 覆盖)。
- 直连 Docker Hub 缓慢时,先用国内镜像站拉取再重打成契约规范标签(镜像引用本身不变,不引入同义名):

```bash
docker pull docker.m.daocloud.io/library/mysql:8.4
docker tag docker.m.daocloud.io/library/mysql:8.4 mysql:8.4
docker pull docker.m.daocloud.io/kindest/node:v1.36.1
docker tag docker.m.daocloud.io/kindest/node:v1.36.1 kindest/node:v1.36.1
```

## 验证证据(2026-08-27,Windows 本地 Docker Desktop)

| 项目 | 环境与版本 | 输入 | 命令 | 结果 |
| --- | --- | --- | --- | --- |
| RED:基线缺失部署入口 | 基线 worktree `origin/dev@54e47e9` | - | `bash scripts/test/verify-k8s-manifests.test.sh ../oj-baseline` | FAIL:`deploy/k8s directory not found` |
| RED:基线缺失脚本入口 | 同上 | - | `bash scripts/test/verify-kind-scripts.test.sh ../oj-baseline` | FAIL:`scripts/kind directory not found` |
| GREEN:manifest 契约 | `feature/288-kind-kubernetes` | - | `bash scripts/test/verify-k8s-manifests.test.sh` | PASS(14 项断言组) |
| GREEN:脚本行为(伪造 docker/kind/kubectl) | 同上 | `GIT_SHA=0123…4567` | `bash scripts/test/verify-kind-scripts.test.sh` | PASS(17 项断言组) |
| GREEN:shell 契约 | 同上 | - | `bash scripts/test/verify-shell-contract.sh ../oj-288` | PASS(22 个跟踪脚本,LF + bash -n) |

活体集群验证(2026-08-28 评审后复跑,Windows Git Bash + Docker Desktop 引擎,kind v0.33/kubectl v1.36.1,节点 `kindest/node:v1.36.1`,集群 Ready 15s;全流程逐步退出码均为 0):

| 项目 | 命令 | 结果(原始输出摘录) |
| --- | --- | --- |
| 一条命令部署 | `GIT_SHA=4833391e… MYSQL_PASSWORD=… MYSQL_ROOT_PASSWORD=… KIND_SKIP_LOAD=1 scripts/kind/k8s-deploy.sh` | exit 0;mysql-0、backend、frontend 全部 `1/1 Running`;滚动等待均带 `--timeout` |
| 契约断言 | `scripts/kind/k8s-verify.sh` | 三项 Ready 副本=1;`mysql pods all run mysql:8.4`;`backend/frontend pods all run …:4833391e…`(逐 Pod 匹配);`mysqld is alive`;backend readiness 200/UP;前端静态页;`frontend -> backend` 代理 readiness 200/UP |
| 滚动更新 | `kubectl rollout restart deployment/backend` + `rollout status --timeout=420s` | exit 0;新 Pod 先 Running、旧 Pod 后 Terminating(maxUnavailable=0/maxSurge=1) |
| 滚动后立即断言 | `scripts/kind/k8s-verify.sh` | exit 0(复跑通过;评审中先后修复两处误报:镜像断言改为逐 Pod 匹配以容忍终止中的旧 Pod,HTTP 断言加 `--retry-all-errors` 以容忍端点切换瞬间的连接重置,等待仍有界) |
| 重复部署 | 再次执行 `k8s-deploy.sh` | exit 0(apply 幂等,三服务 Ready) |
| 精确清理(命名空间) | `scripts/kind/k8s-cleanup.sh` | exit 0;`namespace "onlinejudge-ci" deleted`;复查 NotFound;kind 集群保留 |
| 精确清理(集群) | `scripts/kind/k8s-cleanup.sh --cluster` | exit 0;`Deleted nodes: ["onlinejudge-ci-control-plane"]`;`No kind clusters found` |

说明与残余事项:

- 后端验证镜像构建自本地分支 `tmp/288-readiness-stub@4833391eaf2b4066b2ed69b5918b9cccde1962c7`(origin/dev + 最小 readiness 桩,契约 §5 语义:`SELECT 1` → 200/UP,503 不含 UP,拦截器放行)。该桩仅用于本地验证,不推送;#289 合入后需在最新 `dev` 上 rebase 并用正式镜像重跑本表作为最终验收证据(父任务 #286 门禁)。
- 本机 Windows 侧原生 `kind` 曾在 "Preparing nodes" 处挂起:其派生的 `docker info -f` 子进程与 Docker Desktop CLI 插件纠缠,并随宿主代理 `127.0.0.1:7897` 注入节点 systemd。对 Docker Desktop 与 WSL 做一次干净全量重启(并确认 `settings-store.json` 中 `EnableIntegrationWithDefaultWslDistro` 为 true)后,Windows 原生路径恢复正常,最终复跑即在该路径完成;kind 创建集群时建议 `env -u HTTP_PROXY -u HTTPS_PROXY …` 清除代理变量,避免节点内代理不可达。本环境另有两个已记录并绕过的差异:`docker save` 导出的 `mysql:8.4` 缺 attestation blob(改为节点内 `ctr` 直接从 DaoCloud 镜像站拉取后重打规范标签);`k8s-verify.sh` 在 Git Bash 下需 `MSYS_NO_PATHCONV=1` 防止容器内路径 `/bin/sh` 被误转换。GitHub-hosted Runner(Linux)不受这些 Windows 特有问题影响。
- 镜像装载在本机使用了 `KIND_SKIP_LOAD=1`(镜像已在节点),脚本默认路径 `kind load docker-image` 在 Linux Runner 上可直接使用。
