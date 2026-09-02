# #319 正式 HPA 扩缩容实验运行说明（EXPERIMENT_READY，复审重跑）

- runId: 20260902T090959Z
- baseSha（与 origin/dev 的 merge-base）: `65e578b26c4daa5db4b2dd9c0dc99d5678fbc11b`
- runnerSha / headSha（执行本实验的干净提交，工作区 tracked 文件无改动）: `81030437301d80a41e45a102bd5fe380f9859132`
- deploymentVersion（被测环境，读取自 deployment/assessment-api 的 GIT_SHA env 与镜像 tag）: `da6fd3f88e015dd9fa5a8c9223db9607b21aca0d`
- 最终证据提交: 提交本目录的 `docs(ops)` 提交（SHA 在 PR 描述与审查记录 Round 6 中登记）
- 结果: `EXPERIMENT_READY issue=#319`（runner-console.txt）

## 为什么重跑（Round 6 复审阻塞项）

Round 5 的正式目录 `Issue-319-HPA实验证据-20260902T080519Z/` 声明
head/deployment 均为 `da6fd3f8`，但决定实验能否通过的 runner 修复直到
`5d547072` 才提交——该 PASS 无法从声明 SHA 复现（审核阻塞项 1）。本次重跑：

1. runner 修复后新增"deploymentVersion 与 runnerSha 分开记录"（`e2265d7d`）：
   `metadata.json` 的 `deploymentVersion` 从集群 deployment 的 GIT_SHA 读取，
   `headSha`/`runnerSha` 是实际执行实验的干净提交，二者不再混用。
2. 实验从 tracked 文件干净的 `81030437` 提交重跑，本目录即该次运行原始输出。
3. 全部 `*.log` 证据改以 `.txt` 提交（仓库全局忽略 `*.log`，审核阻塞项 2）。

## 环境

- kind 集群 issue319（kindest/node v1.37.0）+ metrics-server；namespace
  `onlinejudge-platform`（#318 部署脚本部署，9 workloads / 4 migrations 全就绪）。
- 被测 deployment 镜像 tag/GIT_SHA = `da6fd3f8`（环境部署自该提交，含当时
  必要的构建链变更；该变更已按审核要求移出 #319 分支，另行归口）。
- 实验账号：student001（种子）与 hpastudent2..9（先前实验注册）。因一次性
  实验环境未留存后者的密码，本次在实验库内将其 password_hash/salt 重置为
  已知值后走真实登录链路铸 token（TTL 900s），随用随铸。

## 负载设计（与 Round 5 正式口径一致）

1. 主压测负载：student001 查询自身评测任务状态的读链路
   GET /api/v1/evaluations/{taskId} ×24（同一批真实 evaluation_task），
   kubectl port-forward 直连 assessment-api（127.0.0.1:18083），
   concurrency=20，duration=180s → **41,540 请求全部 200、零错误、
   P95=17.7ms**（requests.tsv、load-summary.json）。
   所有任务同属 student001，因此读负载必须使用单一身份；多身份轮询会因
   任务归属校验返回 403（见失败运行记录）。
2. 业务链佐证：两条低速率顺序 HWK 提交流（每条同时刻仅 1 个在途写，
   规避索引死锁），产生真实 submission → evaluation_task → outbox/积压，
   并逐条记录 X-Request-Id 与响应 submissionId（raw/hwk-submit-stream-*.txt）。

## AC 对照

- AC-319-01（扩容/缩容）：raw/hpa-transition.txt
  `scaled up replicas=2 baseline=1` / `scaled down replicas=1 baseline=1`；
  raw/hpa-timeline.txt、pod-timeline.txt、resource-timeline.txt、timeline.txt
  为逐 5s 原始时间线。
- AC-319-02（业务链正确 + 错误率/P95）：raw/requests.tsv（41,540 行原始
  状态/耗时）+ load-summary.json（requests=41540, errors=0, error_rate=0.0,
  request_latency_p95=0.017664s）。raw/curl-errors.txt 为 0 字节（无传输错误）。
- AC-319-03（非关键下游故障不级联摘流）：raw/rabbitmq-outage.txt —— 受控将
  rabbitmq StatefulSet 缩到 0，`deployment "assessment-api" successfully rolled
  out`，随后恢复原副本数。
- AC-319-04（请求去向/积压/待投递/水位）：
  - correlation-example.txt：网关 access log 的 request_id → 响应
    submissionId → assessment_homework_submission（public_id=→submission_id）
    → evaluation_task 的完整可复核链路，两条示例均可在本目录 raw 文件与
    集群 DB 中重查（复核命令见下）。
  - backlog-diagnostics.txt：实验结束时 evaluation_task 状态分布
    （PENDING=4594）、outbox DELIVERED=1698、提交总量 6296。
  - raw/assessment_outbox_pending_and_lease.txt、raw/grade_projection_watermark.txt、
    raw/rabbitmq_queue_backlog.txt、raw/gateway_request_correlation.txt。
  - worker-convergence.txt + raw/worker-convergence-samples.txt：负载期间
    PENDING 3085→4603（写入快于无沙箱 worker 的重试退避），停止写入后
    4603→4436 单调下降，证明 worker 在真实积压下持续收敛。
- AC-319-05（可重复、失败保留原因、原始指标非截图）：本目录全部为原始
  文本/TSV/JSON；SHA 三元组分开记录；失败重跑保留并记录原因（见下）。

## correlation-example.txt 复核命令

```sh
# 网关行（request_id 回显 X-Request-Id）
kubectl -n onlinejudge-platform logs deployment/gateway | grep 36f0134a-57ab-4337-b7b6-360e6e5486d2
# DB 链（HWK 提交响应里的 public submissionId -> submission 行 -> evaluation_task）
# 注意：HWK 提交路径的 evaluation_task.origin_request_id 由异步评测触发器生成，
# 不等于网关 X-Request-Id，因此关联走 submission 关联键而非该列。
#   assessment_homework_submission.public_id = API 响应 submissionId
#   evaluation_task.submission_id = assessment_homework_submission.submission_id
mysql> SELECT s.submission_id, s.homework_id, s.student_id, s.submission_version,
              t.id AS task_id, t.state
       FROM oj_assessment.assessment_homework_submission s
       JOIN oj_assessment.evaluation_task t ON t.submission_id = s.submission_id
       WHERE s.public_id = 4629;
```

## 失败重跑记录（保留原因，AC-319-05）

- `output/issue-319/81030437.../20260902T090111Z/`（EXPERIMENT_FAILURE）：
  主负载误用 9 身份轮询，而 24 个任务同属 student001，35,927 个请求 403
  （归属校验），runner 断言按设计失败并如实输出。教训已固化进编排脚本：
  读负载单身份；同时验证了 runner 对非 2xx 的失败路径（不再误报 READY）。

## 与 Round 5 正式目录的关系

`Issue-319-HPA实验证据-20260902T080519Z/` 保留为过程记录；其声明
（head=deployment=da6fd3f8）不可复现，正式证据以本目录为准。该目录 NOTES.md
已加 superseded 标记。
