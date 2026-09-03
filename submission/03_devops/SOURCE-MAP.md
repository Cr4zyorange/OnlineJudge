# Final SHA 配置来源映射

快照根目录为 `source/`，所有条目均从
`ce87dfabd54239b9d4138736cbb93b06e6c9b260` checkout 复制。仓库原路径仍是
canonical source；快照只为评审和离线验收服务。

| 领域 | canonical source | snapshot |
| --- | --- | --- |
| CI workflow | `.github/workflows/ci.yml`、`.github/workflows/d3-delivery.yml` | `source/.github/workflows/` |
| Docker/Compose | `deploy/docker/compose.yml`、`deploy/docker/compose.gateway.yml`、`deploy/docker/backend.Dockerfile`、`deploy/docker/frontend.Dockerfile` | `source/deploy/docker/` |
| 服务镜像构建 | `services/{gateway,identity,course,assessment,grade}/Dockerfile` 及受限 runner 使用的 `Dockerfile.cached-runtime` | `source/services/` |
| Gateway / proxy | `deploy/gateway/**`、`services/gateway/{Dockerfile,entrypoint.sh,nginx.conf}`、`scripts/gateway/**`、`deploy/nginx/default.conf` | `source/deploy/gateway/`、`source/services/gateway/`、`source/scripts/gateway/`、`source/deploy/nginx/` |
| Kubernetes / Kind | `deploy/k8s/**` | `source/deploy/k8s/` |
| 平台唯一清单 | `deploy/platform/workloads.json`、`workload-manifest.schema.json`、`migration-runner.Dockerfile`、`frontend-disposable.conf.template`、`observability-hpa-experiment.json` | `source/deploy/platform/` |
| 交付和诊断 | `scripts/delivery/**`、`scripts/kind/**`、`scripts/platform/**` | `source/scripts/{delivery,kind,platform}/` |
| CI 校验 | `scripts/ci/**` | `source/scripts/ci/` |
| CI 复现性/运行时验证 | `scripts/test/` 中被 `scripts/ci/{backend,frontend,contract}-verify.sh` 调用的验证脚本及其 Grade/Course 复现脚本 | `source/scripts/test/` |
| 数据库迁移 | `database/migrations/**`、`database/mysql/**` | `source/database/{migrations,mysql}/` |
| seed 与账号边界 | `database/seeds/*.sql`、`database/ownership/*.csv` | `source/database/{seeds,ownership}/` |
| 设计/协作契约 | `docs/开发/D3-CICD-共享契约.md`、`D3-DATABASE-数据库启动与迁移契约.md`、`D7-平台工作负载清单契约.md`、`D7-GATEWAY-路由切流与回滚.md`、`D8-OPS-可观测性与HPA实验契约.md`、`CI-质量门禁开发流程.md` | `source/docs/开发/` |

## 有意未纳入

- 与 #379 交付链无关的其它 issue workflow（例如 `issue-340-resilience.yml`）不纳入本归档；D8 配置和契约已随 #319 合入 `origin/dev`，正式 HPA 原始实验仍由 #319 的过程证据维护，#379 只在验收矩阵中引用其 provenance，不复制第二份大体积压测日志。
- 仓库没有 Helm chart/source；当前 D7 采用平台清单渲染 Kubernetes/Kind manifests，未伪造 Helm 映射。
- `deploy/docker/compose.assessment.yml` 含开发测试用的字面量密码，不复制到提交归档；原始 source 仍可由 final SHA 取得，归档遵守“不得提交 Secret 实值”边界。
- 未复制 `deploy/docker/.env`、生成的运行时 Secret、镜像 tar；镜像 artifact 的 ID、大小、保留期及可下载 URL 记录在 [ACTIONS-MANIFEST.md](ACTIONS-MANIFEST.md)。
- `services/*` 只冻结镜像 Dockerfile（含受限 runner 的 cached-runtime 变体），未复制业务源码；业务源码仍以 final SHA 的仓库正本为准。Gateway 镜像所需的 `scripts/gateway/**` 已单独纳入，保证构建输入引用可追溯。
