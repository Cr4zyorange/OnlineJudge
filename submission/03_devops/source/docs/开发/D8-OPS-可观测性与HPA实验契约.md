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

## 当前正式运行结论（2026-09-02，Round 8 复审重跑）

Round 8 是 AC-319-03、AC-319-04、AC-319-05 的当前正式证据：
`docs/过程/测试/Issue-319-HPA实验证据-20260902T161736Z/`。它针对 Round 8 复审
唯一阻塞（合并后 runner 与 T121/T130 tested runner 均存在实质差异，证据不能外
推到当前运行器），从干净 worktree 以当前 head 构建全部镜像并以同一提交部署
环境后重跑。runnerSha / headSha / deploymentVersion 三者同为
`cf2979dc2fcfd1bc7e6640a71d6f6864e7de7f1b`（被测 deployment 的 `GIT_SHA` env
由 runner 原子读取），从声明 SHA 可完整复现。结果为：

- 31,880 条真实 JWT Assessment 查询全部 2xx、零错误、P95 24.78ms；HPA 从
  1 扩到 3，随后在 300 秒稳定窗后回到 1。
- RabbitMQ 在 `16:18:05Z` 确认 `readyReplicas=0 / pods=0 / endpoints=0`（3 个
  连续 outage-window 采样），同一原始时间戳中 Assessment 始终
  `availableReplicas=1 / readyReplicas=1`，并在 `16:18:42Z` 恢复 `1/1/1`。
- Grade 的实际数据库读数为 `LAB:5:1 watermark=1 / projection=1 / lag=0`、
  `unresolved_gap_rows=0`、`APPLIED=1`。该投影由真实 Assessment outbox 事件经
  RabbitMQ/Grade 消费产生；本轮 `V20260902_03` 由迁移 Job 预先应用，无 DLQ
  重投；业务事实链（课程/加入/LAB/提交/评分/发布）全部经真实 API 完成，
  `fact-provenance.txt` 如实记录，未以 SQL 写入 watermarks/projection。
- 原始 deployment YAML 会脱敏任何 Bearer 值；正式目录经凭据扫描后才入库。

Round 8 的 `NOTES.md` 将每项 AC 映射到 raw 文件；其任务 lease 查询包含终态任务
的 `heartbeat_at`、`attempt`、`generation` 以及按 fenced terminal-write 语义已
清空的 lease 字段（`39de4c76...`，attempt=3/generation=3）。活跃 RUNNING 租约
原始值由 `T121501Z` 互补覆盖。runner 现会查询全部任务，避免只查看 RUNNING 行
而遗漏该事实。

## 历史运行结论（Round 7 复审重跑；Round 5 实验；Round 6 复审重跑）

- Round 7 正式证据（已被 Round 8 独立复现并标注 SUPERSEDED）：
  `docs/过程/测试/Issue-319-HPA实验证据-20260902T130421Z/` 由提交 `2e7e1102`
  的 runner 执行，deployment 版本 `bb4d83ee`：20,700 条请求全 2xx、P95
  20.281ms、HPA 1→3→1、RabbitMQ `13:04:27Z`–`13:04:38Z` 确认 0/0/0 且
  Assessment available 全程为 1、`LAB:1:1 watermark=1`（经一次 legacy 迁移
  缺口的前向修复与 DLQ 恢复）。
- Round 7 的互补活跃租约证据：`docs/过程/测试/Issue-319-HPA实验证据-20260902T121501Z/`
  由提交 `9045592` 的 runner 运行，记录了每秒采样的真实 `RUNNING` 任务
  `lease_owner/lease_until/heartbeat_at`（102 个样本）；同时验证 HPA 从 1 扩到 2、
  36,140 条 Assessment 请求全为 2xx，RabbitMQ `readyReplicas=0` 且 endpoints 为空时
  Assessment 的 10 个可用性采样均为 1。该轮 Grade 投影仍为 0，原因是 Sandbox worker
  对该批事件均写入 SYSTEM_ERROR；它用于补强活跃租约事实，当前 Grade 结论以本文开头
  Round 8 `T161736Z` 的真实事件恢复和水印数据为准。
- Round 6 历史证据：`docs/过程/测试/Issue-319-HPA实验证据-20260902T090959Z/`（EXPERIMENT_READY，
  Round 6 从包含最终 runner 的干净提交 `81030437` 重跑；deployment/runner/证据提交
  SHA 分开记录）：HPA 在真实读负载下从 1 扩到 2、负载结束后缩回 1；41,540 个
  Assessment 业务请求全部 2xx、错误率 0、P95 17.7ms；RabbitMQ 受控摘除/恢复期间
  assessment-api 保持 Available。Round 5 目录 `Issue-319-HPA实验证据-20260902T080519Z/`
  因 SHA 记录不可复现已标记 SUPERSEDED，仅作过程记录保留。
- 证据入库约定：仓库全局忽略 `*.log`，随 PR 提交的原始证据一律以 `.txt` 命名；
  HWK 佐证流逐条记录 X-Request-Id 与响应 submissionId，经
  assessment_homework_submission.public_id/submission_id 关联 evaluation_task
  （HWK 写路径的 evaluation_task.origin_request_id 由异步触发器生成，不等于网关
  X-Request-Id）。
- 压测入口经验：高并发 HWK 新写入会触发 assessment_homework_submission 唯一索引的
  InnoDB 死锁（错误率 ~0.02–0.1%，跨不同 (homework,student) 亦发生）。稳定做法是
  读链路为主负载 + 低速率（≤2 r/s、单在途）HWK 提交做业务链/积压佐证。上述死锁的
  失败运行已按 AC-319-05 保留证据。
- 校准发现：经网关限流（写 10r/s、读 30r/s）时，assessment-api 的 CPU 利用率无法
  达到 60% HPA 阈值（每请求约 1–5ms CPU，对应 30–150m << 180m）。实验负载因此直连
  assessment-api 服务（port-forward），去掉与 HPA 目标无关的网关限流瓶颈；请求仍走
  真实 JWT 鉴权、业务校验与 DB。HPA 阈值与网关限流口径的匹配建议后续单独复核。
- runner 现已支持：多 `--request-url`/多 `--authorization-file` 轮询、单行原子写入、
  `--noproxy '*'`、`wait_for_replicas` 显式分支（修复左结合优先级导致扩容断言失效）；
  metadata 的 `deploymentVersion` 从被测 deployment 的 GIT_SHA 读取，与执行实验的
  `headSha`/`runnerSha` 分开记录，保证运行可从声明 SHA 复现。
