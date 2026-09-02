# Issue #307 同条件性能对比执行说明

## 当前状态

压测计划、逻辑数据集、原始请求采集、Docker 资源采样、完整性校验和报告聚合工具已经具备。#318 已发布 `ENVIRONMENT_READY` 并合入 `dev`，并已在同一台机器完成正式 18 轮实测，结果位于 [20260902T0958Z](results/20260902T0958Z/report/comparison.md)。

正式计数逐轮证明了以下条件；缺任意一项时工具会拒绝聚合：

- #318 的完整 `ENVIRONMENT_READY` 信号：`ENVIRONMENT_READY issue=#318 sha=2d6160fe570f60bba73922640cb8a58bdb692b97 endpoint=http://127.0.0.1:18080 workloads=9 migrations=4 evidence=https://github.com/Cr4zyorange/OnlineJudge/pull/363 actions=https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33500641015`。
- Docker daemon ready，并记录 `docker info`、Compose project、镜像 SHA 和容器清单。
- 正式窗口独占；关闭 HPA、E2E、故障注入和其他压力任务。
- 每轮开始前恢复同一数据库与文件快照。
- 两种架构使用相同总资源预算、相同脚本、相同负载和同一逻辑数据集。

工具会拒绝缺少上述证据、环境受污染、轮次不完整、机器/数据/负载不同或资源不可比的样本。

## 冻结基线

| 项目 | 值 |
| --- | --- |
| 单体 tag | `monolith-start` |
| 单体 commit | `78715f21288782a2c7ef1d9c23f933c46569b108` |
| 三服务执行 commit | `bb4d83ee7a0891490869960370670a2dd03e9962` |
| 三服务业务内容基线 | `bb4d83ee7a0891490869960370670a2dd03e9962` |
| 压测工具分支 | `test/307-monolith-three-service-perf` |
| 数据集 | `performance/issue-307/dataset.json` |
| 数据集 SHA-256 | `733338e1ba51a64b693b60678eeacaa78a0597f7e2034bba6dc2b09e067885c6` |
| 并发 | 10，不超过 SRS 的 20 并发业务请求边界 |
| 每轮 | 30 秒预热 + 120 秒正式采样 |
| 轮次 | 每个接口、每种架构 3 轮，共 18 轮 |
| 资源预算 | 两种架构总计均为 4 CPU / 6144 MiB |

三个接口分别代表读、写和拆分后的主链查询：

1. `GET /api/v1/courses?page=0&size=20`
2. `POST /api/v1/homeworks/{homeworkId}/submissions`
3. `GET /api/v1/courses/{courseId}/my-grades`

写场景每轮开始前必须恢复同一数据库与文件快照。不能让前一轮产生的 Submission 改变后一轮数据规模。

## 工具验证

```text
node --test scripts/perf/tests/*.test.mjs
node scripts/perf/issue-307.mjs validate-plan --plan performance/issue-307/plan.json
node scripts/perf/issue-307.mjs machine
```

阻塞模板应被拒绝：

```text
node scripts/perf/issue-307.mjs validate-window \
  --evidence performance/issue-307/formal-window.template.json
```

预期错误包含：`formal counting requires the ENVIRONMENT_READY signal from #318`。

## 正式执行

为每次数据库快照恢复生成一份实际 formal-window JSON。不得修改模板来伪造通过；实际文件至少要记录：

- #318 的完整 `ENVIRONMENT_READY` 原文和证据链接；
- Docker daemon 已就绪；
- 独占测试窗口；
- HPA、E2E、故障注入和其他压力均关闭；
- 当前轮数据快照恢复证据；
- 两种架构相同总 CPU/内存策略的执行证据。

运行时值全部通过环境变量注入，不写入 Git：

```text
OJ_PERF_MONOLITH_URL
OJ_PERF_MONOLITH_TOKEN
OJ_PERF_MONOLITH_CONTAINERS
OJ_PERF_THREE_SERVICE_URL
OJ_PERF_THREE_SERVICE_TOKEN
OJ_PERF_THREE_SERVICE_CONTAINERS
OJ_PERF_COURSE_ID
OJ_PERF_HOMEWORK_ID
OJ_PERF_HOMEWORK_BODY
```

单轮示例：

```text
node scripts/perf/issue-307.mjs run \
  --plan performance/issue-307/plan.json \
  --formal-window <本轮正式窗口证据.json> \
  --architecture monolith \
  --scenario course-list \
  --round 1 \
  --output performance/issue-307/results/<window>/raw/monolith/course-list/round-1.json
```

必须执行计划中的所有架构、场景和轮次。聚合器会拒绝挑轮、缺轮和条件不一致：

```text
node scripts/perf/issue-307.mjs aggregate \
  --plan performance/issue-307/plan.json \
  --raw-dir performance/issue-307/results/<window>/raw \
  --output-dir performance/issue-307/results/<window>/report
```

输出包括：

- `comparison.json`：完整可机器复算结果；
- `comparison.md`：逐轮、全量聚合和有限解释；
- `rounds.csv`：图表和二次分析数据。

完成窗口中的全量逐请求 JSON 已无损压缩为 `raw/**/*.json.gz`；`raw/raw-manifest.json` 同时记录压缩前、压缩后 SHA-256 和字节数。聚合器可直接读取 `.json.gz`：

```text
node scripts/perf/issue-307-archive-raw.mjs \
  --raw-dir performance/issue-307/results/<window>/raw
node scripts/perf/issue-307.mjs aggregate \
  --plan performance/issue-307/plan.json \
  --raw-dir performance/issue-307/results/<window>/raw \
  --output-dir performance/issue-307/results/<window>/report
```

指标单位固定为延迟 `ms`、总请求吞吐 `requests/second`、成功请求吞吐 `requests/second`、错误率 `%`、CPU `%`、内存 `MiB`。报告只陈述观测差异；当错误率非零时，总请求吞吐和 P95 包含快速失败，不能作为成功业务容量结论，必须查看成功请求吞吐。进程数、网络跳数、序列化、连接池和缓存只能在有对应证据时作为解释，不能自动写成因果结论。

## 2026-09-02 正式结果与结论边界

正式窗口完成 `3 API × 2 架构 × 3 轮 = 18` 个样本。三服务的所有九个工作负载和单体的三个工作负载分别限制为同一总预算；逐轮窗口、重置日志、硬限制、镜像 SHA 和压缩原始样本均在 [evidence](results/20260902T0958Z/evidence/README.md) 中可复核。

该窗口不是“成功业务容量”的胜负结论：三服务 `course-list`、`homework-submission`、`my-grades` 的错误率分别为 `99.419%`、`99.822%`、`100%`，对应成功请求吞吐仅为 `30.102`、`10`、`0` requests/second。单体 `homework-submission` 也有 `82.665%` 错误率，成功请求吞吐为 `82.794` requests/second。完整逐轮数据、P95、CPU、内存与差异都以 [comparison.md](results/20260902T0958Z/report/comparison.md) 为准；任何“低 P95 / 高总吞吐”都必须结合该错误率解释为快速失败，而不是性能提升。

## AC 对照

| 验收项 | 当前证据 | 状态 |
| --- | --- | --- |
| AC-307-01 同机、同数据、同脚本、同负载、可比资源 | 同一机器指纹、数据集 SHA、每轮 formal-window、硬限制证据 | 已完成 |
| AC-307-02 3 API × 2 架构 × 3 轮 | `raw-manifest.json` 的 18 个无损压缩原始样本 | 已完成 |
| AC-307-03 无 HPA/E2E/故障/压力污染 | 每轮 `formal/**/*.json` 均为独占窗口且污染项为 false | 已完成 |
| AC-307-04 P95/吞吐/错误率/CPU/内存 | `comparison.json`、`comparison.md`、`rounds.csv`，并拆分成功吞吐 | 已完成 |
| AC-307-05 有限解释且不宣称未测结论 | 报告和本节明确快速失败不代表性能提升 | 已完成 |
