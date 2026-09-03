# #319 Round 8：当前 head 的可复现正式重跑

- runId: `20260902T161736Z`
- baseSha: `c66686ff0e011f5ee63e3908683f01afd4f83ebc`
- runnerSha / headSha: `cf2979dc2fcfd1bc7e6640a71d6f6864e7de7f1b`
- deploymentVersion: `cf2979dc2fcfd1bc7e6640a71d6f6864e7de7f1b`
- namespace: `issue319-rerun`（#318 Kubernetes 环境，9 workloads / 4 migrations）
- 结果: `EXPERIMENT_READY`，见 `runner-console.txt`

该轮针对 Round 8 复审唯一阻塞（AC-319-05 SHA/可重复性回归）：T121/T130 的
运行器与合并后 runner 存在实质差异。本轮从干净 worktree 以当前 head 构建
全部 workload 镜像并以同一提交部署环境，deploymentVersion = runnerSha =
headSha，三个 SHA 完全同源；镜像构建在 `/tmp` 独立干净 checkout 中进行，
tracked 树无任何未提交改动。被测 deployment 的 `GIT_SHA` env 与 runner 读取
的 head 一致，从声明 SHA 可完整复现。临时授权值在 YAML 快照中被替换为
`<redacted>`，全部入库文件经 Bearer、密码、私钥文本扫描为净。

## AC 对照

- AC-319-01：`raw/hpa-transition.txt` 记录 `scaled up replicas=3 baseline=1` 与
  `scaled down replicas=1 baseline=1`；`raw/hpa-timeline.txt`、
  `raw/pod-timeline.txt`、`raw/resource-timeline.txt`、`raw/timeline.txt` 为每
  5 秒采样原始时间线；HPA CPU=60%、max=3、300 秒缩容稳定窗见 `raw/hpa.yaml`。
- AC-319-02：`raw/requests.tsv` 有 31,880 条真实、带 JWT 的 Assessment
  `GET /api/v1/evaluations/{taskId}` 业务请求，全部 HTTP 200；`load-summary.json`
  给出 0 错误、P95 24.78 ms、平均 12.63 ms；`raw/curl-errors.txt` 为 0 字节。
  直连 Assessment 的 port-forward 避开 Gateway 的 30 r/s 读限流，请求仍经过
  真实 JWT 鉴权、业务归属校验和 MySQL。
- AC-319-03：`raw/rabbitmq-outage.txt` 在 `16:18:05Z` 确认
  `readyReplicas=0 / pods=0 / endpoints=0`，随后 3 个 `outage-window` 采样
  （16:18:07Z、16:18:13Z、16:18:21Z）同一行记录 Assessment
  `availableReplicas=1 / readyReplicas=1`；`16:18:42Z` 记录恢复为 `1/1/1`。
- AC-319-04：`raw/grade_projection_watermark.txt` 记录真实
  `LAB:5:1 watermark=1 / projection=1 / lag=0`、`unresolved_gap_rows=0`、
  `APPLIED=1`；该投影由真实 Assessment outbox 事件经 RabbitMQ/Grade 消费产生
  （本轮 V20260902_03 由迁移 Job 预先应用，无 DLQ 重投，见
  `fact-provenance.txt`）。`raw/assessment_outbox_pending_and_lease.txt` 直接
  查询 `evaluation_task`：任务 `39de4c76...` 终态 FAILED/SYSTEM_ERROR（环境无
  沙箱的真实行为），`lease_owner/lease_until` 按 fenced terminal-write 语义为
  NULL，同行 `heartbeat_at=2026-09-02 16:16:10`、`attempt=3`、`generation=3`
  是实际 worker 生命周期值；outbox 4 条事件 DELIVERED。活跃 RUNNING 租约的
  原始值由 `Issue-319-HPA实验证据-20260902T121501Z/` 的 1 秒级
  `assessment-outbox-lease-timeline` 采样继续覆盖（该目录结论不变）。
- AC-319-05：本轮 metadata 的 baseSha/headSha/runnerSha/deploymentVersion 与
  起止时间由 runner 原子写入；`raw/requests.tsv` 每行含 UTC 时间、请求 UUID、
  HTTP 状态与耗时，可独立复算汇总（31,880 行全部 2xx、0 错误）。

## 与既有证据的关系

- 本轮替代 `Issue-319-HPA实验证据-20260902T130421Z/` 成为 AC-319-03、
  AC-319-04、AC-319-05 的当前正式证据（该目录标注 SUPERSEDED，其结论仍被
  本轮独立复现）。
- `Issue-319-HPA实验证据-20260902T121501Z/` 保留为活跃 RUNNING 租约原始值的
  互补证据。

## 复核入口

```sh
# 复算负载汇总
python3 - <<'PY'
import csv, statistics
rows = [line.split() for line in open('raw/requests.tsv', encoding='utf-8')]
lat = sorted(float(r[3]) for r in rows)
p95 = lat[(len(lat)*95 + 99)//100 - 1]
print(len(rows), sum(int(r[2]) < 200 or int(r[2]) >= 300 for r in rows), p95)
PY
# 复核 Grade 水位（集群内）
kubectl -n issue319-rerun exec statefulset/mysql -- sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e \
  "SELECT * FROM oj_grade.grade_source_projection_watermark;"'
```
