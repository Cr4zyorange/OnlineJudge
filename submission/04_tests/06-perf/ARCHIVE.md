# 06 性能对比（#307）

> 归档说明（ARCHIVE.md）：本目录的 `README.md` 是 `performance/issue-307/README.md`
> 的正本副本，保留其相对链接（`results/…`）以指向本目录 `results/`；本文件补充
> 归档口径与计数。

## 内容

- `README.md`、`dataset.json`、`formal-window.template.json`、`plan.json`、
  `resource-policy.json`：FINAL_SHA 树的 `performance/issue-307/` 计划正本。
- `scripts/`：`scripts/perf/` 全部运行器/工具与其 `tests/`（`node --test
  scripts/perf/tests/*.test.mjs` 39 项契约，见 #307 EVIDENCE_READY）。
- `results/20260902-225234/`：#307 唯一有效正式窗口原样副本（18 gzip raw、
  raw-manifest、preflight、evidence、report）。

## 有效正式结果（AC-TESTS-05）

```text
窗口：20260902-225234
架构：monolith=78715f21288782a2c7ef1d9c23f933c46569b108（monolith-start）
      three-service=c66686ff0e011f5ee63e3908683f01afd4f83ebc
机器指纹：033a722a0f09f91f2525c397c31fa628faa841eed7c8a223751e09a6520a6616
数据集 SHA-256：733338e1ba51a64b693b60678eeacaa78a0597f7e2034bba6dc2b09e067885c6
轮次：3 API × 2 架构 × 3 轮 = 18/18，0 invalid
请求：21,582/21,582 accepted（每轮 1,199），错误率 0%
资源：两架构均 4 CPU / 6144 MiB；10 学生、1 s 节流；网关 30/10 r/s 未改
```

三接口：`GET /api/v1/courses?page=0&size=20`、`POST /api/v1/homeworks/{id}/submissions`、
`GET /api/v1/courses/{courseId}/my-grades`。

聚合示例（monolith vs three-service，P95 ms / 成功吞吐 r/s）：

| 场景 | 单体 P95 | 三服务 P95 | 两者错误率 | 结论边界 |
| --- | ---: | ---: | ---: | --- |
| course-list | 19.674 | 32.758 | 0% | 只陈述实测差异，不做未测量因果 |
| homework-submission | 18.366 | 16.463 | 0% | 同上 |
| my-grades | 12.580 | 16.314 | 0% | 同上 |

复算命令（在仓库根目录执行；本目录为归档副本，脚本路径为仓库正本）：

```bash
node scripts/perf/issue-307.mjs validate-plan --plan performance/issue-307/plan.json
node scripts/perf/issue-307-archive-raw.mjs --window performance/issue-307/results/20260902-225234
node scripts/perf/issue-307.mjs aggregate \
  --plan performance/issue-307/plan.json \
  --raw-dir performance/issue-307/results/20260902-225234/raw \
  --output-dir performance/issue-307/results/20260902-225234/report
```

## 历史窗口与用途

`20260902T0958Z`（含 209MB 未压缩 gzip）与 `20260902T200359Z` 为被弃/superseded
失败证据，正本保留在仓库 `performance/issue-307/results/`（禁止用于 PASS 结论，
未挑轮）；本目录只复制有效窗口。perf 实验固定 SHA 为 #307 基线，非 FINAL_SHA，
用途与可比关系已在 `README.md` 与 `../INDEX.md` 第 3 节声明。
