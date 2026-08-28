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

## 验证证据(证据 head `94a110132190850dceb90bdadd8d800c717c422c`;分支最新提交仅为本 README 文档,不改变任何被测产物)

证据 head 基于 `origin/dev@2a3d355`(rebase,#287/#289 已合入)。下列全部离线测试、镜像构建与活体验证均在 `94a1101` 精确执行;其后提交只修改本文件。

环境:Windows 11 + Docker Desktop(client/server 29.7.2),kind v0.33.0,kubectl v1.36.1,节点 `kindest/node:v1.36.1`;后端/前端镜像由 #289 官方入口 `scripts/docker/build-images.sh` 从本 head 构建(非 root 用户 10001、OCI revision 标签、真实 `/api/v1/system/readiness`)。

| 项目 | 输入 | 命令 | 结果 |
| --- | --- | --- | --- |
| RED:基线缺失部署入口 | 基线 worktree(旧基线 origin/dev) | `bash scripts/test/verify-k8s-manifests.test.sh <基线>` | FAIL:`deploy/k8s directory not found` |
| RED:基线缺失脚本入口 | 同上 | `bash scripts/test/verify-kind-scripts.test.sh <基线>` | FAIL:`scripts/kind directory not found` |
| 执行位检查 | - | `git ls-files -s -- scripts/kind` | 7 个脚本全部 `100755`(契约测试已加模式断言防回归) |
| GREEN:manifest 契约 | - | `bash scripts/test/verify-k8s-manifests.test.sh` | PASS(14 项断言组) |
| GREEN:脚本行为(伪造 docker/kind/kubectl,原子日志) | `GIT_SHA=0123…4567` | `bash scripts/test/verify-kind-scripts.test.sh` | PASS(19 项断言组;另连跑 5 次均 rc=0 验证无日志竞态) |
| GREEN:shell 契约 | - | `bash scripts/test/verify-shell-contract.sh <本 worktree>` | PASS(33 个跟踪脚本,LF + bash -n) |

活体集群验证(2026-08-28,官方镜像,全流程逐步退出码均为 0):

| 步骤 | 命令 | 结果(原始输出摘录) |
| --- | --- | --- |
| 干净集群创建 | `env -u HTTP_PROXY -u HTTPS_PROXY … kind create cluster --config deploy/k8s/kind-cluster.yaml --wait 180s` | rc=0;`Ready after 16s` |
| 官方镜像准备 | `GIT_SHA=94a1101… bash scripts/docker/build-images.sh` + 节点内 `ctr` 拉取 `mysql:8.4`(DaoCloud 镜像站)并重打规范标签 | rc=0;节点 crictl 三镜像齐备 |
| 一条命令部署 | `GIT_SHA=94a1101… MYSQL_PASSWORD=… MYSQL_ROOT_PASSWORD=… KIND_SKIP_LOAD=1 scripts/kind/k8s-deploy.sh` | rc=0;mysql-0、backend、frontend 全部 `1/1 Running`(backend 以 uid 10001 运行,emptyDir 经 `fsGroup: 10001` 可写) |
| 契约断言 | `scripts/kind/k8s-verify.sh` | rc=0;三项 Ready 副本=1;`mysql pods all run mysql:8.4`;`backend/frontend pods all run …:94a1101…`(逐 Pod 匹配);`mysqld is alive`;backend readiness(数据库感知)200/UP;前端静态页;`frontend -> backend` 代理 readiness 200/UP |
| 滚动更新 | `kubectl rollout restart deployment/backend` + `rollout status --timeout=420s` | rc=0;新 Pod 先 Running、旧 Pod 后 Terminating(maxUnavailable=0/maxSurge=1) |
| 滚动后立即断言 | `scripts/kind/k8s-verify.sh` | rc=0(镜像断言逐 Pod 匹配容忍终止中的旧 Pod;HTTP 断言 `--retry-all-errors` 容忍端点切换瞬间的重置,等待仍有界) |
| 重复部署 | 再次执行 `k8s-deploy.sh` | rc=0(apply 幂等,三服务 Ready) |
| 精确清理(命名空间) | `scripts/kind/k8s-cleanup.sh` | rc=0;`namespace "onlinejudge-ci" deleted`;复查 NotFound;kind 集群保留 |
| 精确清理(集群) | `scripts/kind/k8s-cleanup.sh --cluster` | rc=0;`Deleted nodes: ["onlinejudge-ci-control-plane"]`;`No kind clusters found` |

说明与残余事项:

- 本机 Windows 侧原生 `kind` 曾在 "Preparing nodes" 处挂起(其派生的 `docker info -f` 子进程与 Docker Desktop CLI 插件纠缠,并随宿主代理 `127.0.0.1:7897` 注入节点 systemd)。对 Docker Desktop 与 WSL 做一次干净全量重启(并确认 `settings-store.json` 中 `EnableIntegrationWithDefaultWslDistro` 为 true)后,Windows 原生路径恢复正常,上表即在该路径完成;kind 创建集群时建议 `env -u HTTP_PROXY -u HTTPS_PROXY …` 清除代理变量,避免节点内代理不可达。GitHub-hosted Runner(Linux)不受这些 Windows 特有问题影响。
- 本环境两个已记录并绕过的差异:`docker save` 导出的 `mysql:8.4` 缺 attestation blob(改为节点内 `ctr` 直接从镜像站拉取后重打规范标签);`k8s-verify.sh` 在 Git Bash 下需 `MSYS_NO_PATHCONV=1` 防止容器内路径 `/bin/sh` 被误转换。
- 镜像装载在本机使用了 `KIND_SKIP_LOAD=1`(镜像已在节点),脚本默认路径 `kind load docker-image` 在 Linux Runner 上可直接使用;#292 串联流水线时应在 Runner 上走默认路径。
- GitHub Actions:#290 质量门禁 workflow 尚未合入 `dev`;待其可用后,本 PR 分支的推送将触发真实 Actions 运行,届时以其对同一 head 的结果为准补充链接。
