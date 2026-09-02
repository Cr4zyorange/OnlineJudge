# Issue #307 同条件性能对比执行说明

## 当前状态

压测计划、逻辑数据集、原始请求采集、Docker 资源采样、完整性校验和报告聚合工具已经具备。#318 已发布 `ENVIRONMENT_READY` 并合入 `dev`，正式计数不再等待三服务环境。

正式计数仍必须逐轮证明以下条件；缺任意一项时不得声明 Issue 完成：

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
| 三服务执行 commit | `b1121cf89e15731e3e8246a4abb2cb055d326d3b` |
| 三服务业务内容基线 | `84e017dd466e330cea723441979842d0633c14eb` |
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

指标单位固定为延迟 `ms`、吞吐 `requests/second`、错误率 `%`、CPU `%`、内存 `MiB`。报告只陈述观测差异；进程数、网络跳数、序列化、连接池和缓存只能在有对应证据时作为解释，不能自动写成因果结论。

## AC 对照

| 验收项 | 当前证据 | 状态 |
| --- | --- | --- |
| AC-307-01 同机、同数据、同脚本、同负载、可比资源 | plan、dataset checksum、机器指纹和聚合拒绝门禁 | 准备完成，待正式环境执行 |
| AC-307-02 3 API × 2 架构 × 3 轮 | 18 轮完整性门禁和原始请求样本格式 | 准备完成，尚无正式样本 |
| AC-307-03 无 HPA/E2E/故障/压力污染 | formal-window fail-closed 校验 | 准备完成，待独占窗口逐轮记录 |
| AC-307-04 P95/吞吐/错误率/CPU/内存 | nearest-rank P95、Docker stats 汇总、JSON/Markdown/CSV | 工具完成，尚无正式数值 |
| AC-307-05 有限解释且不宣称未测结论 | 报告解释边界与差异字段 | 工具完成，待真实结果解释 |

