# #319 Round 7：HPA、RabbitMQ 故障与投影诊断

- runId: `20260902T130421Z`
- baseSha: `c66686ff0e011f5ee63e3908683f01afd4f83ebc`
- runnerSha / headSha: `2e7e11025619ace9e02c6bd3488c50d725119746`
- deploymentVersion: `bb4d83ee7a0891490869960370670a2dd03e9962`
- namespace: `issue319-rework`（#318 Kubernetes 环境，9 workloads / 4 migrations）
- 结果: `EXPERIMENT_READY`，见 `runner-console.txt`

该轮替代 Round 6 作为 AC-319-03 和 AC-319-04 的正式证据。runner 版本、被测
deployment 版本、UTC 起止时间均由 `metadata.json` 分开记录；临时授权值在 YAML
快照中被替换为 `<redacted>`，并已对全部入库文件扫描 Bearer、密码和私钥文本。

## AC 对照

- AC-319-01：`raw/hpa-transition.txt` 记录 `1 -> 3 -> 1`；
  `raw/hpa-timeline.txt`、`raw/pod-timeline.txt`、`raw/resource-timeline.txt` 和
  `raw/timeline.txt` 是每 5 秒采样的原始时间线。HPA 的 CPU=60%、max=3 和
  300 秒缩容稳定窗见 `raw/hpa.yaml`。
- AC-319-02：`raw/requests.tsv` 有 20,700 条真实、带 JWT 的 Assessment
  `GET /api/v1/evaluations/{taskId}` 业务请求；`load-summary.json` 给出 0 错误、
  P95 20.281 ms，`raw/curl-errors.txt` 为 0 字节。直连 Assessment 的
  port-forward 避开了 Gateway 的 30 r/s 读限流，仍经过 JWT、业务归属校验和 MySQL。
- AC-319-03：`raw/rabbitmq-outage.txt` 在 `13:04:27Z`、`13:04:33Z`、
  `13:04:38Z` 连续记录 `readyReplicas=0`、`pods=0`、`endpoints=0`，同一行
  Assessment `availableReplicas=1`、`readyReplicas=1`；`13:04:54Z` 记录
  RabbitMQ 恢复为 `1/1/1`。
- AC-319-04：`raw/grade_projection_watermark.txt` 记录真实
  `LAB:1:1 watermark=1 / projection=1 / lag=0`、`watermark_rows=1`、
  `unresolved_gap_rows=0` 和 `APPLIED=1`。`raw/assessment_outbox_pending_and_lease.txt`
  直接查询 `evaluation_task` 的 lease 字段，以及 outbox 状态。该任务已终态化，
  所以实现按其 fenced terminal-write 语义清空 `lease_owner/lease_until`；同一行的
  `heartbeat_at=2026-09-02 13:02:08`、`attempt=4`、`generation=5` 是实际 worker
  生命周期值。runner 现不再过滤非 RUNNING 行，避免把这种真实终态误报成“无任务”。
- AC-319-05：所有原始采集均为文本、TSV 或 JSON；`fact-provenance.txt` 说明
  API 创建业务事实、前向迁移和同一持久事件的 DLQ 恢复路径，未用 SQL 填充 Grade
  watermark 或投影行。

## Grade 投影前向修复

第一次真实 `assessment.source-grade.changed.v2` 消费暴露了 legacy
`grade_source_projection.status NOT NULL` 与版本化 projection 写入
`source_status` 的不兼容。此分支新增
`V20260902_03__allow_legacy_projection_status_null.sql`，并将该列约束纳入
MySQL 8.4 Grade 服务冒烟验证。一次性环境应用同一前向迁移后，从 DLQ 重投原始
eventId；水位由 Grade 消费事务生成。详情和迁移 checksum 在
`fact-provenance.txt`，最终数据库读数在 `raw/grade_projection_watermark.txt`。
