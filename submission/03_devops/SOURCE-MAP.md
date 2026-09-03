# Final SHA 配置来源映射

快照根目录为 `source/`，所有条目均从
`c56b16f916b4a4c3d33915aa37beab6b05c72888` checkout 复制。仓库原路径仍是
canonical source；快照只为评审和离线验收服务。

| 领域 | canonical source | snapshot |
| --- | --- | --- |
| CI workflow | `.github/workflows/ci.yml`、`.github/workflows/d3-delivery.yml` | `source/.github/workflows/` |
| Docker/Compose | `deploy/docker/compose.yml`、`deploy/docker/compose.gateway.yml`、`deploy/docker/backend.Dockerfile`、`deploy/docker/frontend.Dockerfile` | `source/deploy/docker/` |
| 服务镜像构建 | `services/{gateway,identity,course,assessment,grade}/Dockerfile` | `source/services/` |
| Gateway / proxy | `deploy/gateway/**`、`services/gateway/{entrypoint.sh,nginx.conf}`、`deploy/nginx/default.conf` | `source/deploy/gateway/`、`source/services/gateway/`、`source/deploy/nginx/` |
| Kubernetes / Kind | `deploy/k8s/**` | `source/deploy/k8s/` |
| 平台唯一清单 | `deploy/platform/workloads.json`、`workload-manifest.schema.json`、`migration-runner.Dockerfile`、`frontend-disposable.conf.template` | `source/deploy/platform/` |
| 交付和诊断 | `scripts/delivery/**`、`scripts/kind/**`、`scripts/platform/**` | `source/scripts/{delivery,kind,platform}/` |
| CI 校验 | `scripts/ci/**` | `source/scripts/ci/` |
| 数据库迁移 | `database/migrations/**`、`database/mysql/**` | `source/database/{migrations,mysql}/` |
| seed 与账号边界 | `database/seeds/*.sql`、`database/ownership/*.csv` | `source/database/{seeds,ownership}/` |
| 设计/协作契约 | `docs/开发/D3-CICD-共享契约.md`、`D3-DATABASE-数据库启动与迁移契约.md`、`D7-平台工作负载清单契约.md`、`D7-GATEWAY-路由切流与回滚.md`、`CI-质量门禁开发流程.md` | `source/docs/开发/` |

## 有意未纳入

- `deploy/platform/` 中没有 HPA/observability 配置文件；最终 SHA 的 D8 证据不在本次 checkout 中，因此只在 [ACCEPTANCE.md](ACCEPTANCE.md) 标为 `BLOCKED`。
- 仓库没有 Helm chart/source；当前 D7 采用平台清单渲染 Kubernetes/Kind manifests，未伪造 Helm 映射。
- `deploy/docker/compose.assessment.yml` 含开发测试用的字面量密码，不复制到提交归档；原始 source 仍可由 final SHA 取得，归档遵守“不得提交 Secret 实值”边界。
- 未复制 `deploy/docker/.env`、生成的运行时 Secret、镜像 tar；镜像 artifact 的 ID、大小、保留期及可下载 URL 记录在 [ACTIONS-MANIFEST.md](ACTIONS-MANIFEST.md)。
