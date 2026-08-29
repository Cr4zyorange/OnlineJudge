# 03_devops 部署与运维

本目录是课程最终提交的 DevOps **索引**，不是第二份 Docker、Kubernetes、workflow 或日志副本。版本冻结时，按下表从正本收集配置和一次真实复演产生的证据；不要复制命令、Secret 实值或未经执行的输出。D3 的跨任务不变量以 [D3 CI/CD 共享契约](../../docs/开发/D3-CICD-共享契约.md) 为唯一正本。

## 当前正本与 D3 交付落点

| 交付物 | 当前正本或最终唯一落点 | 责任/状态 | 归档说明 |
| --- | --- | --- | --- |
| Compose 编排 | `deploy/docker/compose.yml` | #289 已合入 `origin/dev`（PR #302） | 记录三服务、健康检查和卷挂载，不复制第二份 YAML |
| 后端容器构建 | `deploy/docker/backend.Dockerfile` | #289 已合入 `origin/dev`（PR #302） | 提交 Dockerfile 与构建证明 |
| 前端容器构建 | `deploy/docker/frontend.Dockerfile` | #289 已合入 `origin/dev`（PR #302） | 提交 Dockerfile 与构建证明 |
| Nginx 反向代理 | `deploy/nginx/default.conf` | 当前已有 | 证明 `/api/ -> backend:8080` 路由 |
| MySQL schema | `database/mysql/compose-schema.sql` | #287 维护唯一 schema/migration/seed 正本 | 只引用，不复制 SQL |
| Compose API 验收 | `scripts/deploy/verify-compose.sh` | 当前已有 | 保存真实运行原始输出，敏感值必须遮蔽 |
| Compose 验收脚本契约 | `scripts/test/verify-compose.test.sh` | 当前已有 | 保存脚本测试退出码和输出 |
| CI 质量门禁 | `.github/workflows/ci.yml`、`scripts/ci/**` | #290 已合入 `origin/dev`（PR #298） | 归档依赖、编译、测试、报告与失败诊断配置 |
| Kubernetes / Kind 基线 | `deploy/k8s/**`、`scripts/kind/**` | #288 已合入 `origin/dev`（PR #303） | 归档三服务清单、探针、部署和精确清理入口 |
| 端到端交付编排 | `.github/workflows/d3-delivery.yml`、`scripts/delivery/**` | #292 已合入 `origin/dev`（PR #329） | 归档临时 Kind、镜像加载、部署、诊断和清理流程 |
| 权威部署说明 | `README.md`、`docs/最终提交/部署文档.md` | #291 | README 给出入口；本文只保留索引 |
| 复演证据 | `output/issue-291/replay-<UTC>/` | 在真实复演时产生 | 只保存一次来源明确的原始记录，不预生成 PASS |

## D3 运行边界

最终 D3 流水线在 GitHub-hosted Runner 上创建临时 Kind 集群，部署 `mysql`、`backend`、`frontend` 三项服务，完成健康和代理 API 验收后清理集群。它不依赖额外服务器、长期 Kubernetes 集群、生产域名、TLS 或云资源。#287、#289、#288、#290 与 #292 已汇入 `origin/dev@5cdbe8533991bb0c7cfbe23e08d81b78d47af483`；该 SHA 的 [d3-delivery run 33227922081](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33227922081) 为成功状态。Actions artifact 是 CI 运行证据的唯一来源，本地复演则按 README 的入口保留独立原始记录。

| 项目 | D3 契约值 | 说明 |
| --- | --- | --- |
| 自建镜像 | `onlinejudge/backend:${GIT_SHA}`、`onlinejudge/frontend:${GIT_SHA}` | `GIT_SHA` 必须是本次 checkout 的完整 40 位 SHA；禁止仅用 `latest` |
| MySQL 镜像 | `mysql:8.4` | 不伪造仓库 revision label |
| 服务端口 | MySQL `3306`、backend `8080`、frontend `80` | 只有 frontend 是 Compose 宿主入口；默认 `OJ_HTTP_PORT=8088` |
| 健康接口 | liveness `/api/v1/system/health`；readiness `/api/v1/system/readiness` | readiness 实际访问数据源；Kind 探针与前端代理验收由 #288/#292 落地 |
| 初始化 | `database/mysql/compose-schema.sql` 加后端演示数据 | 当前 Compose 首次空卷启动行为；迁移/seed 正本以 #287 最终实现为准 |
| 删除边界 | `down --remove-orphans` 保留卷；`down --volumes --remove-orphans` 删除 MySQL 与应用数据 | 后者不可恢复，复演前先导出需要保留的数据 |

## 配置与 Secret 边界

| 类型 | 键名或规则 | 默认值与记录要求 |
| --- | --- | --- |
| 非敏感环境变量 | `GIT_SHA`、`OJ_HTTP_PORT`、`MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DATABASE`、`MYSQL_USER`、`ONLINEJUDGE_DEMO_DATA_ENABLED`、`ONLINEJUDGE_EVALUATION_SANDBOX_MODE` | 以共享契约为准；可记录解析后的非敏感值 |
| GitHub / Kubernetes Secret | `MYSQL_PASSWORD`、`MYSQL_ROOT_PASSWORD`、`ONLINEJUDGE_NOTIFICATIONS_INTERNAL_TOKEN` | 只记录键名，绝不记录值；D3 完成后不允许用仓库内 password fallback 冒充 Secret |
| Compose 本地文件 | `deploy/docker/.env` | 由 `.env.example` 复制后由操作者维护；不得提交 |
| 镜像追溯 | `org.opencontainers.image.revision=${GIT_SHA}` | 标签与 label 必须相同；检查结果可记录完整 SHA |

## 复演记录与失败证据

由未参与实现的人在干净 checkout 执行 README 指向的实际脚本后，创建一次复演目录。镜像构建会拒绝未跟踪文件，因此构建前必须将目录置于 checkout 外；构建结束后可将归档移入 `output/issue-291/replay-<UTC>/`。记录至少包括：

| 文件 | 必须记录的内容 |
| --- | --- |
| `environment.txt` | 操作系统、Docker/Kind/Git 版本、完整 Git SHA、是否为 GitHub-hosted Runner |
| `commands.txt` | 每条实际命令、开始/结束时间、退出码、服务数量、测试数量和资源数量 |
| `raw/` | Compose/Actions/Kind 的原始 stdout、stderr、测试报告和失败诊断；Secret 值必须先遮蔽 |
| `result.txt` | 仅在所有预期断言成功且退出码为 `0` 时写 `PASS`；失败或跳过必须如实写 `FAIL` 或 `SKIP` 和原因 |

GitHub Actions 的成功、失败和测试报告以对应 workflow run 的 artifact 为唯一来源；#290/#292 已合入后仍不得伪造 artifact 或输出。macOS Docker Desktop、Linux GitHub-hosted Runner 和 Windows Git Bash/WSL 的差异由最终脚本吸收，或者必须在该次复演的 `environment.txt` 中明确记录。
