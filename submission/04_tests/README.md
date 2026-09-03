# 04_tests 测试证据归档

本目录是 Issue [#380](https://github.com/Cr4zyorange/OnlineJudge/issues/380)
（D10-TESTS）的交付物：从最终固定 SHA 与 #307/#319/#320/#340/#367 的真实结果装配
单元、集成/API、浏览器/端到端、公开接口映射、完整业务场景、HPA、故障处理和单体/微服务
性能对比的脚本、固定数据、原始报告与可复算汇总。

## 归档结论

```text
EVIDENCE_READY issue=#380 final_sha=27eab66891bfbbc21cb39ec96dcbedd6be2fabe2
unit=p api=124/124 e2e=24/24 hpa=PASS resilience=7/7 perf_rounds=18 blocked=3 evidence=submission/04_tests
```

说明：

- `unit=p`：FINAL_SHA（`27eab66891bfbbc21cb39ec96dcbedd6be2fabe2`）的
  ci-quality-gate run 33712921299 六项 job 全绿；后端单元/集成与三个服务套件、
  前端单元、runner 契约的精确计数见 `INDEX.md` 与 `01-unit-integration/`。
- `api=124/124`：#367 公开接口 total=124、covered=124、uncovered=0，映射见
  `02-api/code/`（`coverage-report.json`、`inventory.json`、`mapping.json`）。
- `e2e=24/24`：真实 9-workload disposable 三服务环境的 Playwright 业务场景
  24 passed / 0 failed / 0 skipped，证据为 run 33712921299 的
  `ci-browser-e2e-gate-33712921299`，见 `03-e2e/ci/33712921299/`。
- `hpa=PASS`：31,880 次真实 JWT 请求 0 错误、P95 24.78 ms、HPA `1→3→1`、
  RabbitMQ 故障窗口与 Grade 投影水位证据见 `04-hpa/evidence/`。
- `resilience=7/7`：#340 七个故障前/中/后与恢复场景全部 PASS，
  `report.json` 见 `05-resilience/ci/`。
- `perf_rounds=18`：#307 有效正式窗口 `20260902-225234` 共 18 轮、
  21,582/21,582 请求成功、0 错误率，全部原始 gzip 样本与聚合报告见 `06-perf/`。
- `blocked=3`：本 Issue 只归档，不代做工程修复；无法获得原始文件或缺口的项以
  BLOCKED 记录于 `INDEX.md`「缺口与补证」清单（#367 runner 控制台日志、
  #319 中间轮证据目录未复制、历史 superseded 性能窗口未复制），均有 Owner、
  影响与补证标准。

## 装配来源

| 前置 Issue | 正本/来源（仓库内或 Actions） | 归档位置 |
| --- | --- | --- |
| #367 公开接口 | `tests/api/`、服务端 API 测试类、run 33605721693/33606176261、run 33712921299 | `02-api/` |
| #320 E2E | `frontend/tests/e2e/`、`scripts/test/run-business-e2e-*`、run 33712921299、历史 run 33705748031/33706794044 | `03-e2e/` |
| #319 HPA | `docs/过程/测试/Issue-319-HPA实验证据-*`、`scripts/platform/run_hpa_observability_experiment.sh` | `04-hpa/` |
| #340 韧性 | `scripts/test/verify-issue-340-resilience*`、矩阵 JSON、run 33708861734 | `05-resilience/` |
| #307 性能 | `scripts/perf/`、`performance/issue-307/`（有效窗口 `results/20260902-225234`） | `06-perf/` |

代码副本与入库证据一律取自 FINAL_SHA 树（`27eab668`，与当前仓库材料内容一致），
不使用未提交工作区内容；Actions artifacts 为对应 run 的原样 zip，zip SHA-256 与
下载元数据见 `actions/manifest.json`，全文件哈希见 `checks/SHA256SUMS.txt`。

## 目录

- [`INDEX.md`](INDEX.md)：总矩阵、AC-TESTS-01~08 对照、失败/缺口与补证清单。
- [`01-unit-integration/`](01-unit-integration/README.md)：单元/集成/仓库契约代码与
  FINAL_SHA CI 原始 JUnit/汇总。
- [`02-api/`](02-api/README.md)：公开接口清单、映射、覆盖率与 API 测试证据。
- [`03-e2e/`](03-e2e/README.md)：Playwright 场景代码、运行器与最终/历史原始报告。
- [`04-hpa/`](04-hpa/README.md)：HPA/可观测性实验脚本与原始证据。
- [`05-resilience/`](05-resilience/README.md)：故障处理矩阵、脚本与原始报告。
- [`06-perf/`](06-perf/README.md)：性能对比计划、数据集、脚本与 18 轮原始样本。
- [`actions/`](actions/README.md)：下载的 Actions artifact zip 与清单。
- [`checks/`](checks/README.md)：格式、空占位、敏感信息、断链与 SHA256 检查结果。

## 敏感信息边界

归档仅使用课程演示账号与脱敏后元数据。本目录不包含 token、Cookie、私钥、真实口令、
kubeconfig 或未脱敏个人数据；`checks/` 中的扫描结果记录命中数为 0。测试身份、SHA、
计数与故障阶段的可审计性在脱敏后保持完整。
