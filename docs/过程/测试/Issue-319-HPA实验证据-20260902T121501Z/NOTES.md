# #319 正式 HPA 扩缩容实验运行说明（EXPERIMENT_READY，Round 7 复审重跑）

- runId: 20260902T121501Z
- baseSha（与 origin/dev 的 merge-base）: `65e578b26c4daa5db4b2dd9c0dc99d5678fbc11b`
- runnerSha / headSha（执行本实验的干净提交）: `90455922de51c668e60e97e6236e3646a1929b93`
- deploymentVersion（被测环境，deployment/assessment-api 的 GIT_SHA env 与镜像 tag）: `da6fd3f88e015dd9fa5a8c9223db9607b21aca0d`
- 最终证据提交: 提交本目录的 `docs(ops)` 提交（SHA 在审查记录 Round 7 与 PR 评论中登记）
- 结果: `EXPERIMENT_READY issue=#319`（runner-console.txt）

## 本轮相对 Round 6 证据（20260902T090959Z）的差别

Round 7 复审两项 FAIL 及其处置：

1. **AC-319-03 故障窗口不真实**：原 runner 记录的只是 scale 接受 + 对已就绪
   deployment 立即返回的 rollout status。新 runner 的 rabbitmq-outage.txt 现在是
   完整时间线：受控缩容 → 轮询等待 **readyReplicas=0 且 service endpoints 为空**
   并打印"confirmed unavailable"确认行 → 在该已验证的故障窗口内以 3s 间隔采样
   assessment-api 的 availableReplicas / readyReplicas / serviceEndpoints（10 个
   采样，全部 availableReplicas=1）→ 恢复 → 等待 **readyReplicas 回到原值且
   endpoints 重新填充**并打印"restored"确认行。rollout status 已移除。
2. **AC-319-04 文件名不能替代信号**：两个同名信号文件改为数据库原始值转储：
   - `raw/assessment_outbox_pending_and_lease.txt`：outbox 状态分布、事件类型
     ×状态、evaluation_task 状态分布、**活跃租约原始值**
     （lease_owner/lease_until/heartbeat_at）与 60s 内租约生命周期行；
   - `raw/grade_projection_watermark.txt`：grade_source_projection_watermark
     原始行与行数、grade_source_projection 行数、grade inbox/outbox/deferred
     行数、assessment 侧 source grade 行数；
   - 新增 `raw/assessment-outbox-lease-timeline.txt`：负载期间每秒采样的
     outbox PENDING 计数 + 当刻活跃租约原始值（102 个采样，其中 1 个采样捕到
     真实 RUNNING 租约：lease_owner=assessment-worker@assessment-worker-
     67ccf846b6-7mc8d, lease_until=2026-09-02T12:17:38Z,
     heartbeat_at=2026-09-02T12:17:08Z）；
   - 原 Grade/Assessment 应用日志改名为 `grade-service-applog.txt` /
     `assessment-api-applog.txt`，仅作上下文，不再冒充信号文件。

## 主结果

- 主负载：student001 身份读链路 GET /api/v1/evaluations/{taskId} ×24，
  直连 assessment-api（port-forward 127.0.0.1:18083），concurrency=20，
  duration=180s → **36,140 请求全部 200、零错误、P95=23.3ms**（requests.tsv、
  load-summary.json；curl-errors.txt 为 0 字节）。
- HPA：`raw/hpa-transition.txt` `scaled up replicas=2 baseline=1` /
  `scaled down replicas=1 baseline=1`；hpa/pod/resource/timeline 逐 5s 原始时间线。
- 业务链佐证：两条低速率单在途 HWK 提交流（~4 req/s 合计），全 201
  （raw/hwk-submit-stream-1/2.txt，逐条含 X-Request-Id 与响应 submissionId）。
- 停载后 worker 收敛：PENDING 3045→3034 开始单调下降
  （raw/worker-convergence-samples.txt，30s 间隔）。

## AC-319-04 的 grade 投影水位为何为空（原始数据可证）

本一次性环境无评测沙箱：所有 evaluation 以 SYSTEM_ERROR 终态结束（永不成功），
因此 worker 从不产生 grade 消费的 `assessment.source-grade.changed.v2` 事件。
原始证据链：outbox 中只有 `assessment.evaluation.completed.v2`（生命周期事件，
4,952 条 DELIVERED）、`source-grade.changed.v2` 为 0 条；`grade.source-grades.v2`
队列深度 0；grade_event_inbox/outbox 均为 0；watermark/projection/source grade
表均为 0 行。空水位是该环境在全部原始值层面的真实状态，非采集缺失；每项数值
都可用 NOTES 下方命令在集群中复核。

## 复核命令

```sh
export KUBECONFIG=<issue319 kubeconfig>
# AC-319-03 故障窗口
cat docs/过程/测试/Issue-319-HPA实验证据-20260902T121501Z/raw/rabbitmq-outage.txt
# 活跃租约原始值（worker 在处理时）
kubectl -n onlinejudge-platform exec statefulset/mysql -- sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -t -e "SELECT id,state,attempt,lease_owner,lease_until,heartbeat_at FROM oj_assessment.evaluation_task WHERE lease_until > UTC_TIMESTAMP LIMIT 5;"'
# grade 水位原始值
kubectl -n onlinejudge-platform exec statefulset/mysql -- sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -t -e "SELECT COUNT(*) FROM oj_grade.grade_source_projection_watermark; SELECT event_type,state,COUNT(*) FROM oj_assessment.assessment_event_outbox GROUP BY event_type,state;"'
# correlation 链
kubectl -n onlinejudge-platform logs deployment/gateway | grep e6973021-8580-4906-b109-095692a6495b
```

## SHA 溯源

| 对象 | SHA |
| --- | --- |
| deploymentVersion（被测环境） | `da6fd3f88e015dd9fa5a8c9223db9607b21aca0d` |
| runnerSha / headSha（tested runner） | `90455922de51c668e60e97e6236e3646a1929b93` |
| 证据提交 | 本目录所在 `docs(ops)` 提交 |

前序目录 `Issue-319-HPA实验证据-20260902T090959Z/` 因诊断信号不满足 Round 7
复审要求已标注 SUPERSEDED，保留为过程记录；其自身的 AC-319-05 SHA 溯源结论
（复审 PASS 项）不受影响。
