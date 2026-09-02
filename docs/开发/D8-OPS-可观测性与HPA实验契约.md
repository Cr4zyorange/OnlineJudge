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

`#318` 的 Kubernetes 渲染器会把 `autoscaling/v2` Assessment API HPA 与其 Deployment 放在同一 applications stage。环境 Ready 后，使用一个已预置身份和业务事实、且返回 2xx 的 Assessment 业务链 URL 执行实验；该显式参数避免在仓库或证据中嵌入凭据：

```sh
bash scripts/platform/run_hpa_observability_experiment.sh \
  --namespace onlinejudge-platform \
  --gateway-url http://<gateway-address> \
  --request-url http://<gateway-address>/<authenticated-assessment-business-chain> \
  --authorization-file <受限读取的授权头文件> \
  --request-method POST \
  --request-body-file <受限读取的Assessment请求体文件>
```

授权头与请求体都只从调用方受限文件读取，不复制到证据目录或命令行输出。每次实验固定先受控摘除并恢复 RabbitMQ，确认 Assessment API 仍有 Available 副本；随后必须观察到 HPA 相对基线扩容并在负载结束后缩容。实验入口会在 `output/issue-319/<sha>/<utc>/` 保留 HPA/Pod/资源时间线、每次请求的原始耗时和状态、Gateway/Assessment/Grade 日志及 RabbitMQ queue 计数；无论成功或失败均输出证据目录。`kubectl top` 不可用会失败关闭，不能把无 Metrics API 的运行称为扩缩容实验。

## 真实运行结论（2026-09-02，Round 5 实验）

在 kind 集群（#318 环境 9 workloads 就绪）执行真实实验后确认：

- 正式实验证据：`output/issue-319/da6fd3f8/20260902T080519Z/`（EXPERIMENT_READY）：
  HPA 在真实读负载下从 1 扩到 2、负载结束后缩回 1；44,680 个 Assessment 业务请求
  全部 2xx、错误率 0、P95 17ms；RabbitMQ 受控摘除/恢复期间 assessment-api 保持
  Available。
- 压测入口经验：高并发 HWK 新写入会触发 assessment_homework_submission 唯一索引的
  InnoDB 死锁（错误率 ~0.02–0.1%，跨不同 (homework,student) 亦发生）。稳定做法是
  读链路为主负载 + 低速率（≤2 r/s、单在途）HWK 提交做业务链/积压佐证。上述死锁的
  失败运行已按 AC-319-05 保留证据。
- 校准发现：经网关限流（写 10r/s、读 30r/s）时，assessment-api 的 CPU 利用率无法
  达到 60% HPA 阈值（每请求约 1–5ms CPU，对应 30–150m << 180m）。实验负载因此直连
  assessment-api 服务（port-forward），去掉与 HPA 目标无关的网关限流瓶颈；请求仍走
  真实 JWT 鉴权、业务校验与 DB。HPA 阈值与网关限流口径的匹配建议后续单独复核。
- runner 现已支持：多 `--request-url`/多 `--authorization-file` 轮询、单行原子写入、
  `--noproxy '*'`、`wait_for_replicas` 显式分支（修复左结合优先级导致扩容断言失效）。
