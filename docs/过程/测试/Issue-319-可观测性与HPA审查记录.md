# Issue #319 可观测性与 HPA 审查记录

## Round 1 — 2026-09-02

审查基线：`origin/dev...feature/319-observability-hpa`；范围为 #319 的 HPA 清单、渲染器、实验运行器和契约测试。

| 编号 | 结论 | 可复现证据 | 统一修复 |
| --- | --- | --- | --- |
| R1-01 | 阻塞 | 实验配置要求 `finishedAtUtc`，运行器只写 `startedAtUtc`。 | 在 exit trap 写入完整结束时间。 |
| R1-02 | 阻塞 | 原始请求行不保留 `requestId`，无法与 Gateway/Assessment/Grade 日志关联。 | 每行记录 UTC、requestId、HTTP 状态和耗时；汇总器按新列解析。 |
| R1-03 | 阻塞 | RabbitMQ 为非关键 readiness 依赖仅存在声明，没有受控故障验证。 | 增加可选 RabbitMQ outage 模式：缩容 broker、确认 Assessment API 保持 Available、恢复原副本数并保留诊断。 |

上述问题修复并通过回归后才进入 Round 2；本记录不将尚未运行的真实 HPA 负载结果表述为 GREEN。

## Round 2 — 2026-09-02

| 编号 | 结论 | 可复现证据 | 统一修复 |
| --- | --- | --- | --- |
| R2-01 | 阻塞 | 运行器只采集 HPA 时间线，不验证 HPA 从基线扩容再缩容；无扩缩容仍可输出成功。 | 记录基线 replicas，增加有界等待：负载后必须大于基线、空闲后必须回到基线或更低，否则失败并保留原始 HPA 时间线。 |

## Round 3 — 2026-09-02

| 编号 | 结论 | 可复现证据 | 统一修复 |
| --- | --- | --- | --- |
| R3-01 | 阻塞 | RabbitMQ outage 是可选标志；省略标志仍可输出 `EXPERIMENT_READY`，未覆盖 AC-319-03。 | 每轮实验固定执行 outage 与恢复，移除可跳过路径。 |
| R3-02 | 阻塞 | 运行器只发匿名 GET，无法调用需 JWT 或 POST body 的 Assessment 提交/查询链。 | 增加不记录内容的 Authorization 文件、请求 method 与 body 文件参数；只在 raw evidence 留 requestId/status/耗时。 |

## Round 4 — 2026-09-02

| 编号 | 结论 | 可复现证据 | 统一修复 |
| --- | --- | --- | --- |
| R4-01 | 阻塞 | `git log origin/dev..HEAD` 显示 #319 提交之前还包含 9 个 dev-container/skill 维护提交；以当前分支创建 PR 会混入不属于本 issue 的变更。 | 从 `origin/dev` 新建干净的 #319 交付分支，仅顺序移植 #319 的测试、实现、文档和本审查记录提交；移植后重新执行完整回归。 |

Round 4 的静态与契约回归已通过；但真实集群实验尚未执行，原因是当前工作站未安装 `kubectl` 且没有 Kind 集群。该环境缺口不是代码 GREEN，不能以 `EXPERIMENT_READY` 代替 AC-319-01 至 AC-319-05 的实际证据。

## Round 5 — 2026-09-02（真实集群实验执行 + 缺陷修复）

环境缺口已关闭：安装 kubectl v1.37.0、kind 集群 issue319（kindest/node v1.37.0）、
metrics-server，并按 #318 `deploy_kubernetes_disposable_environment.sh` 部署 9
workloads / 4 migrations；随后真实运行 HPA 扩缩容实验。正式实验证据目录：
`docs/过程/测试/Issue-319-HPA实验证据-20260902T080519Z/`（`EXPERIMENT_READY`）。

| 编号 | 结论 | 可复现证据 | 处理 |
| --- | --- | --- | --- |
| R5-01 | 阻塞 | 真实环境暴露 #318 渲染器缺陷：assessment-worker 的 Deployment args 未带 `--spring.config.additional-location=classpath:/application-compose.properties`，worker 连入 H2 内存库而非 MySQL，事件/投影全丢失。 | 实验环境以 `kubectl set env/patch` 修复；渲染器根因应归口 #318 另修。 |
| R5-02 | 阻塞 | grade-service 与 assessment-worker 的 `RABBITMQ_PORT` 被 Kubernetes service-link 注入为 `tcp://<svc-ip>:5672`（渲染器只给 course-service 显式注入 `RABBITMQ_PORT=5672`），Spring int 绑定崩溃 CrashLoop。 | 实验环境 patch 注入 `RABBITMQ_PORT=5672`；渲染器不一致应归口 #318。 |
| R5-03 | 阻塞 | course-service `/internal/v2/**` 要求服务 JWT/mTLS，而 #318 只注入纯字符串 workload identity → 一切依赖 course 授权的写路径（教师建作业/查全量等）返回 401→503。属 #318 环境与 #338 契约间的运行时缺口，此前基线只验证过 health。 | 非 #319 修复范围；本实验用学生投影授权（course member projection 经事件同步）走通业务链；建议归口 #320/#318。 |
| R5-04 | 阻塞 | `wait_for_replicas` 条件 `A && B \|\| C && D` 按 bash 左结合解析为 `((A&&B) \|\| C) && D`：scale-up 分支成功后仍被 scale-down 分支 `(current<=baseline)` 否决 → scale-up 断言永远失败，且失败未中断脚本（脚本仍以 0 退出并误报 `EXPERIMENT_READY`）。 | 改为显式 `if/elif` 分支；用 mock kubectl 行为验证（replicas=2>1 通过、=1 失败）；测试更新。 |
| R5-05 | 阻塞 | 高并发 HWK 新写入（并发 16–36）在 `assessment_homework_submission` 唯一索引上产生 InnoDB 死锁（错误率约 0.02%–0.1%），跨不同 (homework,student) 也发生；worker 在线时加剧；空索引首插必撞。 | 判定为 HWK 模块提交路径属性，非 #319 可改公共契约。按 owner 指示正式实验换稳定读路径；死锁运行保留为失败证据（AC-319-05）。建议另开 issue 加死锁重试/调事务顺序。 |
| R5-06 | 非阻塞 | 网关写路由 10r/s、读路由 30r/s 限流下，assessment-api（request 300m、HPA 60% 阈值 180m）CPU 利用率无法达到 60%（实测 10–30r/s × 每请求 1–5ms ≈ 30–150m），HPA 永不触发。 | 正式实验直连 assessment-api（port-forward）以去掉与 HPA 目标无关的网关限流瓶颈；该校准问题记录于本实验 NOTES.md，建议后续复核 HPA 阈值或网关限流口径。 |
| R5-07 | 阻塞 | 多 URL/多身份轮询下 requests.tsv 行交错（子 shell printf 缓冲与 curl 写入竞争）→ 行不可解析。 | 改为每次请求"捕获结果后单行原子写"；format 已由 44,680 行全 200 记录验证。 |
| R5-08 | 阻塞 | 调用方 `HTTP(S)_PROXY`（本机 7897 代理）使 127.0.0.1 实验请求被代理拦截返回 502（`no_proxy` 的 `127.*` 模式 curl 不识别）。 | runner 的 curl 增加 `--noproxy '*'`（实验目标是内部端口转发）。 |
| R5-09 | 非阻塞 | auth 文件约定为只含 `Bearer <token>`；首轮我误存完整 `Authorization:` 头前缀导致双重前缀 401。 | 操作层修正并验证 201；无需改代码。 |

Round 5 正式实验结果（evidence=`docs/过程/测试/Issue-319-HPA实验证据-20260902T080519Z`）：
44,680 个评估状态读请求全部 200、零错误、P95 17ms；HPA 从基线 1 副本在真实负载下
扩容至 2（`scaled up replicas=2 baseline=1`）并在负载结束后缩回 1
（`scaled down replicas=1 baseline=1`）；RabbitMQ 受控摘除期间 assessment-api 保持
Available 并恢复（`rabbitmq-outage.log`）；运行结束诊断显示 2,270 个 PENDING
evaluation_task 与关联 outbox/网关日志（`backlog-diagnostics.txt`、
`correlation-example.txt`）。失败试运行（死锁/断言 bug/假 READY）均保留证据目录并
在 NOTES.md 记录原因。

## Round 6 — 2026-09-02（复审 Rework：可复现重跑、证据入库与范围冻结）

针对 Round 5 送审的 REQUEST_CHANGES 三项阻塞逐项处置。证据提交：`3bbb6aa7`；
其后的 `294edde` 仅清理证据文件的 EOF 空行与行尾空格，无内容变化。

| 编号 | 阻塞点 | 处置 |
| --- | --- | --- |
| R6-01 | AC-319-05：正式运行 metadata 声明 head=deployment=`da6fd3f8`，但决定实验能否通过的 runner 修复直到 `5d547072` 才提交，PASS 只能来自未记录的 dirty working tree，无法从声明 SHA 复现。 | 1) `e2265d7d`：runner 的 `deploymentVersion` 改从被测 deployment 的 GIT_SHA 读取（40-hex 校验，缺失即失败），与 `headSha`/`runnerSha`（执行实验的干净提交）分开记录，测试同步断言二者不得同源。2) 从 tracked 文件干净的提交 `81030437`（包含最终 runner）重跑实验：EXPERIMENT_READY，HPA 1→2→1，41,540 读请求全 2xx、零错误、P95 17.7ms。3) SHA 三元组分开登记：deployment=`da6fd3f8`、runner/tested=`81030437`、证据提交=`3bbb6aa7`（本提交链）。旧目录 `Issue-319-HPA实验证据-20260902T080519Z/` 标注 SUPERSEDED 保留为过程记录。 |
| R6-02 | AC-319-03/04：正文引用的 `hpa-transition.log`、`rabbitmq-outage.log`、Gateway/Assessment/Grade 诊断日志、`hwk-submit-stream-*.log` 因全局 `*.log` 忽略未入库；`correlation-example.txt` 的 DB 映射段为空。 | 全部原始证据改以 `.txt` 随 PR 提交（`3bbb6aa7`，7.7MB 原始输出，含 0 字节 curl-errors 以证明无传输错误）。correlation 链补全为可复核闭环：HWK 佐证流逐条记录 X-Request-Id 与响应 submissionId → 网关 access log 行 → `assessment_homework_submission`（public_id=submissionId）→ `evaluation_task`（submission_id 关联）。注意：HWK 写路径 `evaluation_task.origin_request_id` 由异步评测触发器生成，不等于网关 X-Request-Id，Round 5 的空段正是沿该列查询所致；正确关联键与复核 SQL 已写入新目录 NOTES.md。积压侧新增停载后收敛采样（PENDING 4603→4436 单调下降）。 |
| R6-03 | 范围冻结：PR 混入 `deploy/docker/frontend.Dockerfile` 与全局 `build_workload_images.sh` 的 #318/交付构建链变更（host network 构建、npm 重试参数）。 | `569d6318` 将两文件恢复为 origin/dev 状态并移除钉住该行为的测试断言；变更完整保留在独立分支 `fix/318-image-build-network-retries`（cherry-pick 自原提交），另行开 issue/PR 归口。被测环境的镜像仍由含该变更的提交构建（deploymentVersion 如实记录为 `da6fd3f8`），不影响实验有效性。 |
| R6-04 | `git diff --check` 因证据表格尾随空格失败。 | `81030437` 清理已提交 raw 输出中的 kubectl 尾随空格；当前 `git diff --check origin/dev...HEAD` 干净。 |

复审重跑期间的失败运行（保留，AC-319-05）：`output/issue-319/81030437.../20260902T090111Z/`
—— 主负载误用 9 身份轮询，而 24 个评测任务同属 student001，35,927 请求 403（归属
校验），runner 断言按设计失败并如实输出 EXPERIMENT_FAILURE。教训固化：同一批任务
的读负载必须单身份；同时实证了 runner 非 2xx 失败路径不再误报 READY。

Round 6 验证：`python3 -m unittest discover -s scripts/platform/tests` 52/52 通过；
实验契约校验 PASS；`git diff --check` 干净；44,680→41,540 的 requests.tsv 可独立
解析（41540×200、0 错误、P95 0.017664s）。

## Round 7 — 2026-09-02（复审 Round 6 rework：故障窗口与诊断信号）

owner 复审（head=`f5e5d82b`）：Round 6 的 AC-319-05 SHA 溯源、独立交付边界、
`git diff --check` 三项 PASS；两项 FAIL 打回：

| 编号 | FAIL 点 | 处置 |
| --- | --- | --- |
| R7-01 | AC-319-03：rabbitmq-outage 证据只有 scale 接受、对已就绪 deployment 立即返回的 rollout status、恢复 scale 三行；runner 未等 RabbitMQ 真正不可用，availableReplicas 未写入证据 → 无法复核故障窗口内未被级联摘流。 | `90455922`：runner 故障阶段改为——缩容后轮询等待 **readyReplicas=0 且 service endpoints 为空**并输出"rabbitmq confirmed unavailable"确认行；在已验证的故障窗口内（30s）以 3s 间隔采样 assessment-api availableReplicas/readyReplicas/serviceEndpoints（本轮 10 个采样全部 availableReplicas=1）；恢复后等待 **readyReplicas 回到原值且 endpoints 重新填充**并输出"rabbitmq restored"确认行；rollout status 移除。 |
| R7-02 | AC-319-04：grade_projection_watermark.txt 只是 Grade 全量应用日志（仅启动与 RabbitMQ 断连/重连），无投影 version/lag/cursor 水位；Assessment 证据无任何 lease 值——文件名不能替代所需诊断信号。 | `90455922`：两个信号文件改为数据库原始值转储——`assessment_outbox_pending_and_lease`（outbox 状态分布、事件类型×状态、任务状态分布、活跃租约原始值 lease_owner/lease_until/heartbeat_at、60s 内租约生命周期行）；`grade_projection_watermark`（水印表原始行/行数、投影行数、grade inbox/outbox/deferred、assessment source grade 行数）。新增 1s 级 `assessment-outbox-lease-timeline`（负载期 102 个采样，1 个采样捕获真实 RUNNING 租约：lease_owner=assessment-worker@assessment-worker-67ccf846b6-7mc8d、lease_until=12:17:38Z、heartbeat_at=12:17:08Z）；应用日志改名为 `assessment-api-applog`/`grade-service-applog` 仅作上下文。 |

Round 7 重跑（干净提交 `9045592`，tracked 树无改动）：EXPERIMENT_READY；
HPA 1→2→1（`scaled up replicas=2 baseline=1` / `scaled down replicas=1
baseline=1`）；36,140 读请求全 2xx、零错误、P95 23.3ms；HWK 双流佐证全 201；
证据提交=`6345a3bf`，正式目录
`docs/过程/测试/Issue-319-HPA实验证据-20260902T121501Z/`。SHA 三元组：
deployment=`da6fd3f8`、runner/tested=`9045592`、证据提交=`6345a3bf`。
`...T090959Z/` 目录标注 SUPERSEDED（其 SHA 溯源与 correlation 链结论仍有效）。

诊断信号的环境事实（原始数据可证）：本一次性环境无评测沙箱，全部 evaluation
以 SYSTEM_ERROR 终态结束，worker 从不产生 grade 消费的
`assessment.source-grade.changed.v2` 事件（outbox 事件类型分布中该类型为 0 条，
`grade.source-grades.v2` 队列深度 0，grade_event_inbox/outbox 0 行）→ grade
投影水印为 0 行是该环境的真实状态，非采集缺失；每项数值均可在集群内复核
（复核 SQL 见证据目录 NOTES.md）。

## Round 8 — 2026-09-02（复审 Round 7 rework：当前 head 的可复现正式重跑）

owner 复审（head=`cf2979dc`）：Round 7 的两项 FAIL（AC-319-03 故障窗口、
AC-319-04 Grade 投影水位与 Assessment 租约原始值）均 PASS；范围冻结与
`git diff --check` PASS。唯一阻塞为 AC-319-05 的 SHA/可重复性回归：两次真实
实验分别运行在 `90455922`（T121501Z）与 `2e7e1102`（T130421Z），合并后当前
runner 与两者都有实质差异（相对 T130 tested runner 又新增/变更 41 行；相对
T121 为 225 additions/98 deletions），现有证据只能证明两个父提交，不能证明
合并后的运行器能产出同一套通过证据。处置如下：

| 编号 | 阻塞点 | 处置 |
| --- | --- | --- |
| R8-01 | AC-319-05：在当前 head（或仅含文档提交的直接后继）重跑一次正式实验并提交 metadata/raw evidence，不新增功能、不扩大范围。 | 1) 本地分支快进到复审 head `cf2979dc`；在 `/tmp` 独立干净 checkout 以该提交构建全部 7 个 workload 镜像 + migration runner（tracked 树无改动），`kind load` 后以 `deploy_kubernetes_disposable_environment.sh --git-sha cf2979dc` 部署一次性环境 `issue319-rerun`（9 workloads / 4 migrations 就绪，`KUBERNETES_ENVIRONMENT_READY`）。2) 经真实 API 重建业务事实链（teacher001/student001 登录 → 建课 → 加入 → 建 LAB（1 测试用例）→ 发布 → 学生提交 → 教师评分 95.00 → 发布成绩），Assessment outbox 真实交付 `assessment.source-grade.changed.v2`，Grade 消费产生 `LAB:5:1 watermark=1 / projection=1 / lag=0 / APPLIED=1`；本轮 `V20260902_03` 由迁移 Job 预先应用，无 DLQ 重投。3) 以当前 head 的 committed runner 正式重跑：EXPERIMENT_READY，HPA 1→3→1，31,880 读请求全 200、零错误、P95 24.78ms，RabbitMQ `16:18:05Z` 确认 0/0/0 且 Assessment available 全程为 1，`16:18:42Z` 恢复 1/1/1。证据提交与目录 `docs/过程/测试/Issue-319-HPA实验证据-20260902T161736Z/`；`T130421Z` 标注 SUPERSEDED。 |

Round 8 正式证据的 SHA 三元组完全同源：deploymentVersion = runnerSha =
headSha = `cf2979dc2fcfd1bc7e6640a71d6f6864e7de7f1b`，证据提交为其仅含文档的
直接后继；从声明 SHA 复现 = 干净 checkout 渲染部署 + 重放业务链 + 运行同一
提交的 runner。被测 deployment 的 `GIT_SHA` env 由 runner 原子读取并写入
metadata，与 head 一致，不存在"声明 SHA 不可复现"的缺口。

环境运行时修补（与 Round 5 已记录的 #318 渲染器缺陷一致，均属一次性环境
操作步骤，非 #319 代码改动，已在 `fact-provenance.txt` 登记）：R5-02
`RABBITMQ_PORT=5672` 显式注入；R5-01 worker args 追加 compose 配置使其连入
MySQL；R5-03 服务身份缺口的环境侧处置——用环境 secret 的
`IDENTITY_JWT_SIGNING_KEY` 离线铸造服务 JWT（aud=course/assessment 及对应
scopes）注入 `*_SERVICE_AUTHORIZATION`，并把
`ASSESSMENT_COURSE_AUTHORIZATION_URI` 修正为 course 实际暴露的
`/internal/v2/courses/{courseId}/authorizations/{userId}`（渲染器硬编码 URI
与控制器映射不一致，归口 #318）。

Round 8 验证：`python3 -m unittest discover -s scripts/platform/tests` 55/55
通过；`validate_observability_experiment.py` 契约校验 PASS；
`raw/requests.tsv` 可独立复算为 31,880×200、0 错误、P95 0.02478s，与
`load-summary.json` 一致；入库文件凭据扫描干净（无 Bearer/JWT/私钥/口令值，
YAML 快照 Bearer 值已由 runner 脱敏）；`git diff --check` 干净。
