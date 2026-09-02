# #319 正式 HPA 扩缩容实验运行说明（EXPERIMENT_READY）

- runId: 20260902T080519Z（本证据目录已随 PR 提交至 docs/过程/测试/Issue-319-HPA实验证据-20260902T080519Z）
- baseSha (merge-base with origin/dev): 65e578b2
- headSha / deploymentVersion: da6fd3f8
- 环境: kind 集群 issue319（kindest/node v1.37.0，单 control-plane，6 CPU / 12Gi）
- namespace: onlinejudge-platform（#318 9 workloads / 4 migrations 全就绪）
- 结果: EXPERIMENT_READY issue=#319（runner-console.log）

## 负载设计（稳定 Assessment 路径）

按 owner 指示，正式扩缩容负载不再以"高并发 HWK 新写入"为唯一入口（该路径在并发写下
存在 assessment_homework_submission 唯一索引的 InnoDB 死锁，见失败运行记录）。正式实验采用：

1. 主压测负载：学生查询自身评测任务状态的读链路
   GET /api/v1/evaluations/{taskId} ×24（student001 的真实 evaluation_task），
   直连 assessment-api 服务（kubectl port-forward 127.0.0.1:18083），concurrency=20，
   duration=180s。44,680 个请求全部 200，零错误，P95=17ms。
   该路径无写锁竞争，负载稳定，驱动 CPU 越过 HPA 阈值（60% of 300m）触发扩容。
2. 业务链佐证（低速率单并发 HWK 提交，供 API/E2E 验收 + 积压诊断）：
   hwk-submit-stream.sh 以 ~2 req/s 顺序提交（每次仅 1 个在途写，规避索引死锁），
   产生真实 PENDING 任务/outbox 积压（见 backlog-diagnostics.txt 与
   raw/hwk-submit-stream-*.log）。

为什么直连而不是经网关：网关对 /api/v1/homeworks 写路由限 10r/s、evaluations 读路由
限 30r/s。实测在此限流下 assessment-api 的 CPU 利用率无法达到 60%（每请求约 1-5ms
CPU，10-30 r/s 上限对应约 30-150m << 180m 阈值），HPA 永不触发——这是本实验发现并
记录的设计校准问题（见审查记录 Round 5）。直连去掉的是"网关限流"这个与 HPA 目标无关
的测试瓶颈，请求仍经过真实 JWT 鉴权、真实业务校验与真实 DB。

## AC 对照

- AC-319-01（扩容/缩容）：raw/hpa-transition.log "scaled up replicas=2 baseline=1" /
  "scaled down replicas=1 baseline=1"；raw/hpa-timeline.txt / raw/pod-timeline.txt /
  raw/resource-timeline.txt 提供逐 5s 时间线。
- AC-319-02（业务链正确 + 错误率/P95 原始结果）：raw/requests.tsv（44,680 行原始
  状态与耗时）+ load-summary.json（errors=0, error_rate=0.0, p95=0.017082s）。
- AC-319-03（非关键下游故障不级联摘流）：raw/rabbitmq-outage.log —— 实验开始时受控
  将 rabbitmq StatefulSet 缩到 0，assessment-api rollout 仍成功、保持 Available，
  随后恢复原副本数。
- AC-319-04（日志/指标回答请求去向、积压、待投递、水位）：backlog-diagnostics.txt
  （2,270 PENDING evaluation_task 等）、correlation-example.txt（网关日志
  request_id 与 assessment 落库任务）、raw/assessment_outbox_pending_and_lease.log、
  raw/grade_projection_watermark.log、raw/rabbitmq_queue_backlog.txt、
  raw/gateway_request_correlation.log。
- AC-319-05（可重复、失败保留原因、原始指标非截图）：全部为 raw 文本/TSV/JSON 原始
  输出；多次失败试运行目录保留（见下）。

## 失败试运行记录（保留原因，符合 AC-319-05）

失败运行原始证据保留在本地工作区 output/issue-319/da6fd3f8/ 下（未随 PR 提交，因其内容在本 NOTES 与审查记录 Round 5 中逐项记录原因；如需复现可重新执行 runner，见 D8-OPS 契约文档）：
- 20260902T064219Z：9 学生 × 36 并发 HWK 新写入（worker 在线）→ InnoDB 死锁
  （4×500）+ 扩容断言缺陷（见下），EXPERIMENT_FAILURE。
- 20260902T070201Z：6 并发 × 3.5MB 请求体 HWK 新写入（worker 在线）→ 死锁 5×500。
- 20260902T072212Z：worker 停 + 3.5MB + 6 并发 → 空索引首次并发插入死锁 4×500
  （t=0 瞬间），后续 4,454 请求全 201。
- 20260902T073425Z：worker 停 + 暖场索引 + 3.5MB + 6 并发 → 44,180×201 零错误，
  但 CPU 随大请求体写入波动、HPA 中途缩回 → scale-up 断言失败
  （该断言另有优先级 bug，见下）。
- 20260902T075601Z：读路径 44,180×200、1→2 扩容成功；但 runner 的
  wait_for_replicas 存在 `A && B || C && D` 左结合优先级 bug，scale-up 分支被
  scale-down 分支否决，运行误报 EXPERIMENT_READY（bug 已修，见代码变更）。

死锁根因（HWK 模块属性，非 #319 修复范围）：assessment_homework_submission 的
uq(homework_id,student_id,submission_version) 唯一索引 + 同事务内
UPDATE is_final + INSERT 在并发写下产生 InnoDB gap/insert-intention 锁环；
worker 的租约/outbox/重试 DB 活动会显著加剧；空索引首插必撞。建议另开 issue 在
HWK 提交路径加死锁重试或改事务顺序。

## 关键代码变更（本分支，非 PR）

scripts/platform/run_hpa_observability_experiment.sh:
1. --request-url 可重复（round-robin）—— 单聚合行锁下多事实并行负载；
2. --authorization-file 可重复（与 URL 配对轮询）—— 多身份负载；
3. 每次请求单行原子写入 requests.tsv（修 32 并发下行交错）；
4. curl 增加 --noproxy '*'（修调用方 HTTP(S)_PROXY 导致 127.0.0.1 被代理拦截 502）；
5. wait_for_replicas 改为显式分支（修 `A && B || C && D` 左结合导致 scale-up
   断言永远失败/误报 READY）。
配套测试 scripts/platform/tests/test_disposable_environment_scripts.py 已更新并
通过（12/12）。
