# 04 HPA / 可观测性（#319）

## 内容

- `scripts/run_hpa_observability_experiment.sh`：实验运行器正本（FINAL_SHA 树）。
- `evidence/`：FINAL_SHA 树内已提交的原始证据副本：
  - `Issue-319-HPA实验证据-20260902T161736Z/`：正式验收（Round 8，替代 130421Z）。
  - `Issue-319-HPA实验证据-20260902T121501Z/`：活跃 RUNNING 租约 1 秒采样的互补证据。
  - `Issue-319-当前head-Kubernetes兼容性证据-20260903T030700Z/`：`enableServiceLinks
    =false` 配置兼容补验收（不替代正式负载实验）。
  - `Issue-319-可观测性与HPA审查记录.md`：Round 1–8 评审与失败修复全过程。

## 正式实验结果（AC-TESTS-04）

```text
requests=31,880  errors=0  error_rate=0
avg=12.63 ms  P95=24.78 ms
HPA: scaled up replicas=3 baseline=1 → scaled down replicas=1 baseline=1
RabbitMQ outage window: confirmed unavailable (readyReplicas=0 / endpoints=0) → Assessment stays 1/1 → restored
Grade: watermark=1 projection=1 lag=0 unresolved_gap_rows=0
```

环境：临时 Kind `issue319-rerun`（9 workloads / 4 migrations）；base/head/runner/
deployment 均为 `cf2979dc2fcfd1bc7e6640a71d6f6864e7de7f1b`（该 SHA 含于 FINAL_SHA
历史，用途为 #319 实验基线，不冒充 FINAL_SHA 的 CI 门禁）。

复算入口（来自 NOTES.md）：

```sh
python3 - <<'PY'
import statistics
rows = [line.split() for line in open('raw/requests.tsv', encoding='utf-8')]
lat = sorted(float(r[3]) for r in rows)
p95 = lat[(len(lat)*95 + 99)//100 - 1]
print(len(rows), sum(int(r[2]) < 200 or int(r[2]) >= 300 for r in rows), p95)
PY
```

关键原始文件：`raw/hpa-transition.txt`、`raw/hpa-timeline.txt`、`raw/pod-timeline.txt`、
`raw/requests.tsv`、`raw/curl-errors.txt`（0 字节）、`raw/rabbitmq-outage.txt`、
`raw/grade_projection_watermark.txt`、`raw/assessment_outbox_pending_and_lease.txt`、
`metadata.json`、`load-summary.json`、`runner-console.txt`。
