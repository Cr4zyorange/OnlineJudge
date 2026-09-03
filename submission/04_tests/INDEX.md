# 04_tests 总索引与验收矩阵（#380）

> 生成时间：2026-09-03（Asia/Shanghai）
> final_sha=`27eab66891bfbbc21cb39ec96dcbedd6be2fabe2`
> 归档分支：`docs/380-04-tests-archive`（目标 PR -> `dev`，描述含 `closes #380`）

## 1. 总矩阵

计数口径：`total` 为测试系统报告的用例总数；`passed = total - failed - skipped`
（无 skipped 的系统直接采用其 passed 值）；`failed` 同时计入 failures 与 errors；
runner 原始计数与产品验收状态分开列示。

| 测试层级 | 业务场景/公开接口 | 脚本（可复现） | 环境 | SHA | 命令 | total | passed | failed | skipped | 原始报告（本目录内） | 状态 |
| --- | --- | --- | --- | --- | --- | ---: | ---: | ---: | ---: | --- | --- |
| Workflow 静态校验 | ci.yml 门禁图 | `.github/workflows/ci.yml`、`scripts/ci/check-workflows*` | GitHub Actions ubuntu-latest | 27eab668（run 33712921299） | `check-workflows` job | 67 checks | 67 | 0 | 0 | `01-unit-integration/ci/check-result.txt` | PASS |
| 后端单元 | AUTH/CRS/LAB/HWK/GRD/LRN 等领域单元 | `backend/src/test/java` | 同上（JDK 21/Maven） | 27eab668（run 33712921299） | `bash scripts/ci/backend-verify.sh` | 474 | 461 | 0 | 13 | `01-unit-integration/ci/backend/target/surefire-reports/unit/` | PASS |
| 后端集成 | Controller/API/MySQL/消息集成 | `backend/src/test/java`（integration 分组） | 同上 | 27eab668 | 同上 | 23 | 22 | 0 | 1 | `01-unit-integration/ci/backend/target/surefire-reports/integration/` | PASS |
| Assessment 服务套件 | LAB/HWK 生命周期、Rabbit、Worker、API 覆盖 | `services/assessment/src/test/java` | 同上 | 27eab668 | 同上 | 119 | 111 | 0 | 8 | `01-unit-integration/ci/services/assessment/target/surefire-reports/` | PASS |
| Course 服务套件 | CRS/LRN 路由、并发、投影、API 覆盖 | `services/course/src/test/java` | 同上 | 27eab668 | 同上 | 70 | 60 | 0 | 10 | `01-unit-integration/ci/ci-artifacts/backend-gate/course/surefire-reports/` | PASS |
| Grade 服务套件 | GRD API、投影、消息、API 覆盖 | `services/grade/src/test/java` | 同上 | 27eab668 | 同上 | 37 | 37 | 0 | 0 | `01-unit-integration/ci/services/grade/target/surefire-reports/` | PASS |
| 前端单元 | 组件/路由/API mock 单元 | `frontend/tests/unit/`、`frontend/tests/unit/*` | GitHub Actions（Node 22/npm 10.9.2） | 27eab668（run 33712921299） | `bash scripts/ci/frontend-verify.sh` | 573 | 573 | 0 | 0 | `01-unit-integration/ci/ci-artifacts/frontend-gate/frontend-unit-junit.xml` | PASS |
| 前端 runner 契约 | 共享 E2E 入口契约 | `frontend/tests/contracts/` | 同上 | 27eab668 | 同上 | 3 | 3 | 0 | 0 | `01-unit-integration/ci/ci-artifacts/frontend-gate/runner-contracts.txt` | PASS |
| 仓库契约（consumer/producer） | OpenAPI/AsyncAPI/平台 manifest/脚本契约 | `scripts/ci/contract-verify.sh` | GitHub Actions（JDK 21/Node 22/zsh） | 27eab668（run 33712921299） | `bash scripts/ci/contract-verify.sh … consumer/producer` | 25/27（两侧 Maven 契约用例） | 25/27 | 0 | 0 | `01-unit-integration/ci/backend/target/surefire-reports/contract-consumer/`、`…/contract-producer/`、`ci-artifacts/contracts-gate/*/gate.log` | PASS |
| 公开接口映射与 API 测试 | #367 全部公开接口（Identity 23 / Course 42 / Assessment 27 / Grade 22 / Gateway 10） | `02-api/code/*`、`scripts/test/run-api-coverage-367.sh` | 提取：Windows 10 + JDK 24（CI JDK 21）；执行：GitHub Actions + 本地 runner | 映射正本：FINAL_SHA；#367 tested head `27bda936` | `node tests/api/api-coverage.mjs all`、`node …/api-coverage.test.mjs`、`node …/api-coverage.mjs gateway-static`、`bash scripts/test/run-api-coverage-367.sh` | covered=124/124、unmapped=0；服务测试 264 run / 0 fail / 20 skipped（#367 口径） | 124 | 0 | 0 | — | `02-api/code/coverage-report.json`、`02-api/code/inventory.json`、`02-api/code/mapping.json`、FINAL JUnit（API 测试类，位于 `01-unit-integration/ci/`） | PASS |
| 浏览器/端到端（真实三服务） | #320 24 场景：AUTH 9 / CRS 2 / GRD 1 / HWK 2 / LAB 4 / LRN 4 / shared 2 | `03-e2e/code/tests/e2e/`、`03-e2e/scripts/run-business-e2e-disposable.mjs` | GitHub Actions Browser job：真实 9 workload / 4 migrations disposable（`oj318-27eab66891bf-3351`） | 27eab668（run 33712921299） | `npm run test:e2e:business:disposable` | 24 | 24 | 0 | 0 | `03-e2e/ci/33712921299/ci-artifacts/browser-e2e-gate/playwright-junit.xml`、`test-summary.json`、`representative-evidence.json` | PASS |
| HPA/可观测性 | #319 Assessment 读负载扩缩容、RabbitMQ 故障窗口、Grade 水位 | `04-hpa/scripts/run_hpa_observability_experiment.sh` | 临时 Kind `issue319-rerun`（9 workloads/4 migrations），直连 Assessment port-forward | experiment/deployment/runner=`cf2979dc2fcfd1bc7e6640a71d6f6864e7de7f1b`（含于 FINAL_SHA 历史） | 见 `04-hpa/evidence/Issue-319-HPA实验证据-20260902T161736Z/NOTES.md` 与 runner-console | 31,880 请求 / 0 错误 / P95 24.78 ms / avg 12.63 ms；HPA `1→3→1` | PASS | — | — | — | `04-hpa/evidence/…20260902T161736Z/`（raw 时间线、hpa.yaml、requests.tsv、rabbitmq-outage 等） | PASS |
| 故障处理/恢复 | #340 七个场景：course-delay / assessment-api-down / worker-kill / grade-down / rabbitmq-down / identity-down / duplicate-gap-dlq | `05-resilience/scripts/verify-issue-340-resilience.sh`、`issue-340-resilience-matrix.json` | Actions `issue-340-resilience`：isolated #318 disposable Compose（9 workloads/4 migrations） | tested=`cb53f265`（run 33708861734，PR head `4955b781`） | `bash scripts/test/verify-issue-340-resilience.sh --bootstrap-318 --output-dir ci-artifacts/issue-340` | 7 | 7 | 0 | 0 | `05-resilience/ci/report.json`、`05-resilience/ci/scenarios/*/` | PASS |
| 单体 vs 微服务性能 | 3 接口（course-list/homework-submission/my-grades）× 2 架构 × 3 轮 | `06-perf/scripts/issue-307.mjs`、`06-perf/scripts/issue-307-formal-run.sh`、`06-perf/`（README/plan/dataset） | 同一 Mac（指纹 `033a722a…`），Docker 4 CPU/6144 MiB 硬限制，10 学生/1s 节流，同一数据集 SHA `733338e1…` | monolith=`78715f21…`；micro=`c66686ff…`（两者均为 FINAL_SHA 历史内的实验固定基线） | `node scripts/perf/issue-307.mjs run …`（18 轮）→ `… aggregate …` | 18 轮 / 21,582 请求 | 21,582 | 0 | 0 | `06-perf/results/20260902-225234/raw/`（18 gzip + raw-manifest）、`report/comparison.md/.json`、`rounds.csv` | PASS |

## 2. 场景与证据结构（按前置 Issue）

### #320 E2E（AC-TESTS-03 口径）

- 已确认 24 条业务场景全部保留在 `03-e2e/code/tests/e2e/`，目录按模块划分，fixtures
  统一在 `tests/e2e/fixtures.ts`，禁用固定 sleep，权限与状态边界见各 spec。
- 每组代表场景（AUTH-CRS `course-2`、ASSESSMENT-WORKER `task-2b13c708…`、
  GRD-LRN `notification-12`/`eventId=fae938b7…`/`correlationId=71d8a32d…`）在
  `03-e2e/ci/33712921299/…/representative-evidence.json` 中保存主成功 + 运行时日志
  关联（queued → worker terminal → final GET），截图与 JUnit 位于同目录。
- #320 PR 侧验收（head `653116212e`，disposable `oj318-653116212e-37508`）为历史
  支持证据；FINAL_SHA run 33712921299 的 24/24 为本目录对 AC-TESTS-03 的交付判定。

### #319 HPA（AC-TESTS-04 口径）

- 正式验收目录 `04-hpa/evidence/Issue-319-HPA实验证据-20260902T161736Z/`：
  metadata 的 base/head/runner/deployment 三元组同源；`raw/hpa-transition.txt`
  记录 `scaled up replicas=3 baseline=1` 与 `scaled down replicas=1`；
  `raw/requests.tsv` 31,880 行可独立复算；`raw/rabbitmq-outage.txt` 记录确认
  不可用窗口（readyReplicas=0/endpoints=0）与恢复；`raw/grade_projection_watermark.txt`
  记录 watermark=1/projection=1/lag=0。
- `…121501Z/` 为活跃 RUNNING 租约 1 秒级采样的互补证据；`…20260903T030700Z/`
  为当前 head `enableServiceLinks=false` 兼容性补验收（不替代正式负载实验）。
- 历史中间轮（080519Z/090959Z/130421Z）未复制入本目录，仍以仓库
  `docs/过程/测试/Issue-319-*` 为可追溯正本并带 SUPERSEDED/互补标注。

### #340 韧性（AC-TESTS-04 后半部分）

- `05-resilience/ci/report.json` 为 run 33708861734 原样产物：7/7 PASS、failed=0、
  blocked=0，含每场景 before/during/recovery 断言摘要与唯一 event/task 身份。
- 每场景子目录（`scenarios/*/{before,during,recovery,status,evidence,domain-before,
  domain-after}` 等）保存注入命令命中证据、即时数据库/消息断言与恢复秒数；
  `worker-rabbit-recovery-evidence.log` 保存 Worker/RabbitMQ 恢复原始断言。
- `query-login-meta.json`、`evidence-scan.json` 为脱敏登录元数据与 49 文件 0 命中扫描。
- 残余风险（已在 #340 结论披露）：MySQL 为 disposable 中单个物理 workload，实验
  证明逻辑服务边界与恢复行为，不宣称跨物理数据库故障域。

### #307 性能（AC-TESTS-05 口径）

- 唯一可验收正式窗口：`06-perf/results/20260902-225234/`（18/18 轮有效，
  21,582/21,582 接受，0 invalid rounds）。每轮 1,199 请求；Course 预检逐一断言
  `data.total == 105`；raw-manifest 记录 18 个 gzip 的压缩前/后 SHA-256。
- `06-perf/README.md` 记录同一机器指纹、数据集 SHA、固定脚本/负载、显式
  4 CPU/6144 MiB 硬限制、独占窗口与逐轮数据恢复证据；`report/rounds.csv` 与
  `comparison.md` 可复算 P95/吞吐/错误率/CPU/内存。
- 较早窗口（20260902T0958Z、20260902T200359Z 及被弃诊断轮）为 FAIL/无效样本，
  本目录不复制其 200MB+ 原始 gzip；正本仍在仓库
  `performance/issue-307/results/*`（历史失败轮次全部保留，未挑轮）。

## 3. 运行器计数与产品验收状态分离

| run / 来源 | 运行器计数 | 验收/产品状态 | 判定依据 |
| --- | --- | --- | --- |
| ci-quality-gate 33712921299（FINAL_SHA） | 上表 unit/integration/E2E 计数全绿 | PASS（#380 的 FINAL CI 证据） | 六项 job 全部 success，artifact 原始 XML/汇总 |
| ci-quality-gate 33696824293（#307 head `7b7aa8ad`） | 六项 job success | #307 正式窗口单独以本地 Mac 18 轮证据判定 | run 元数据 + `06-perf/README.md`；该 run 不替代 FINAL_SHA 门禁 |
| issue-340-resilience 33708861734 | 7/7 PASS | #340 验收 PASS | `05-resilience/ci/report.json` |
| ci-quality-gate 33708861783（#340 PR head `4955b781`，整体 cancelled） | Validate/Repo/Backend/Frontend/Browser SUCCESS | 冗余门禁，被 final run 取代 | run 元数据（历史记录） |
| ci-quality-gate 33705748031（#320 head `653116212e`） | Browser job 在 `docker scout … --format` 处失败（code 125），无 Playwright 输出 | #320 FAIL 调试记录 | `03-e2e/history/33705748031/…/gate.log` |
| ci-quality-gate 33706794044（#320 head `6338d4a3`） | Playwright 24 passed/0 failed/0 skipped；job 在运行时证据排序断言失败 | #320 FAIL（业务全过、证据校验 FAIL）→ 修复后 head `c382cd56` 离线 replay PASS | `03-e2e/history/33706794044/…/gate.log`、`test-summary.json` |
| d3-delivery 33714164312（FINAL_SHA） | 四阶段 success | D3 交付证据（#379 范围） | 本目录只记录元数据，artifact 归 03_devops |

## 4. 失败/缺口与补证清单

| # | 类型 | 内容 | 影响 | Owner | 复现/证据 | 目标时间与 retest 标准 |
| --- | --- | --- | --- | --- | --- | --- |
| GAP-01 | BLOCKED | #367 的原始 runner 控制台（`output/issue-367/coverage.log` 等，Git 忽略目录）未入库且 Actions 无对应 artifact，无法在本目录离线提供逐行 runner 输出 | 不影响接口映射结论（mapping/coverage/JUnit 均在），但 #367 runner 的原始 console 文本缺失 | lyc（#367/#380） | 需在 #367 tested head `27bda936` 重跑 `bash scripts/test/run-api-coverage-367.sh` 并将 `output/issue-367/` 日志归档（retest 标准：runner 输出 exit 0 + final-summary 与 coverage-report 一致） | 总控 #321 冻结前补；无法补齐时 #321 侧以 BLOCKED 保留 |
| GAP-02 | BLOCKED（归档范围缺口） | #319 中间轮实验目录（080519Z/090959Z/130421Z）未复制入 04_tests（仓库保留 SUPERSEDED 正本） | 不影响正式验收（161736Z 目录完整）；中间轮仅作过程追溯 | terrana37（#319） | 仓库 `docs/过程/测试/Issue-319-HPA实验证据-20260902T{080519Z,090959Z,130421Z}/` + 审查记录 | 无需补证；如需随包携带可在 #321 装配时按仓库正本复制 |
| GAP-03 | BLOCKED（归档范围缺口） | #307 被弃/superseded 性能窗口（`20260902T0958Z` 约 209MB、`20260902T200359Z` 等）未复制入 04_tests | 不影响有效窗口验收（20260902-225234 已完整）；历史失败轮次在仓库正本保留 | luoZiHui-maker/Cr4zyorange（#307） | 仓库 `performance/issue-307/results/` | 无需补证；禁止以历史窗口冒充 FINAL 有效结论，INDEX 第 3 节已分离用途 |

## 5. AC-TESTS 验收对照

| AC | 验收项 | 状态 | 本目录证据 |
| --- | --- | --- | --- |
| AC-TESTS-01 | 三类测试代码与最终原始报告齐全，环境/SHA/命令/计数完整 | PASS | `01-unit-integration/`、`02-api/`、`03-e2e/`（代码副本来自 FINAL_SHA 树；原始报告来自 run 33712921299） |
| AC-TESTS-02 | 公开接口 mapped=124/124、uncovered=0，含负向边界 | PASS | `02-api/code/coverage-report.json`；负向覆盖说明见 `02-api/README.md` 与 mapping |
| AC-TESTS-03 | 24 业务场景 E2E 计数与 #320 一致 | PASS | `03-e2e/ci/33712921299/…/test-summary.json`（24/24/0/0）+ representative 证据 |
| AC-TESTS-04 | HPA 扩缩容 + 故障前中后恢复材料含原始指标/日志与业务断言 | PASS | `04-hpa/evidence/…161736Z/`、`05-resilience/ci/report.json` |
| AC-TESTS-05 | 2-3 接口 × 2 架构 × ≥3 轮，条件一致、原始数据完整可复算 | PASS | `06-perf/results/20260902-225234/`（18 轮）+ `06-perf/README.md` |
| AC-TESTS-06 | Actions artifacts 离线保存，历史/不同 SHA 用途清楚 | PASS | `actions/manifest.json`（artifact zip + SHA-256 + 下载/过期时间）；INDEX 第 3 节用途分离 |
| AC-TESTS-07 | PASS 均有证据；FAIL/BLOCKED 有复现/影响/Owner/retest | PASS | 第 3、4 节与 `03-e2e/history/`（两个 #320 FAIL 窗口原样保留） |
| AC-TESTS-08 | INDEX、矩阵、文件哈希、检查 total/pass/fail/blocked 与原始日志完整 | PASS | 本文件 + `checks/SHA256SUMS.txt` + `checks/README.md`（含空占位/断链/敏感信息扫描结果） |
