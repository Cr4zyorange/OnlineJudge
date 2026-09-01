# D8 OPS：可观测性与 HPA 扩缩容实验契约（#319）

`deploy/platform/observability-hpa-experiment.json` 是 #319 在 #318 环境交付前的唯一可执行实验配置。它只声明 Assessment API 的 HPA、采集指标、诊断问题与证据保留；不替代 #318 的服务 Kubernetes adapter，也不伪造真实压测结果。

## 前置门禁

真实运行只能在 #318 发布 `ENVIRONMENT_READY` 后开始。每次运行记录 base/head SHA、部署版本、环境、UTC 起止时间；失败必须保留原始输出与原因，不能用截图或人工汇总代替。

## HPA 与可用性

- `assessment-api` 保持 manifest 中的 CPU/内存 requests/limits 及 startup/liveness/readiness 探针；HPA 从 1 扩至最多 3 副本，CPU 平均利用率阈值为 60%，缩容稳定窗为 300 秒。
- MySQL 是 readiness 的关键依赖；RabbitMQ 是非关键依赖。RabbitMQ 故障应留下 outbox/backlog 诊断而非令 Assessment API readiness 失败。

## 采集与验收

原始采集至少包括 Pod 数、CPU、内存、吞吐、平均/P95 延迟、错误率和 UTC 时间线。诊断必须能通过 `requestId`、`correlationId`、workload 关联 Gateway/服务请求，并报告 RabbitMQ queue backlog、Assessment outbox PENDING/lease、Grade projection watermark。

运行前先执行：

```sh
python3 scripts/platform/validate_observability_experiment.py \
  --workload-schema deploy/platform/workload-manifest.schema.json \
  --workload-manifest deploy/platform/workloads.json \
  --experiment deploy/platform/observability-hpa-experiment.json
```

该验证只证明配置和证据契约；不将其表述为 AC-319-01 至 AC-319-05 的真实环境 GREEN。
