# 02_docs 最终文档归档索引

> Issue #368 frozen base: `c56b16f916b4a4c3d33915aa37beab6b05c72888`; build source: `f33007324df4be27594005e45818e9bf07f72b8d`. 本目录由 `scripts/delivery/build-issue-368-docs.mjs` 从唯一正本生成。

## 冻结口径

当前系统只有 **Course、Assessment、Grade 三个业务服务**。Identity 提供身份认证；Gateway 是统一入口；Assessment Worker 独立消费评测任务；RabbitMQ 承载可靠事件；MySQL 承载 identity/course/assessment/grade 四个 schema 与四个最小权限账号。工作负载清单固定为 9 个工作负载、4 个迁移任务。

## 任务书与验收映射

| 验收项 | 唯一正本 | 冻结产物 | 状态 |
| --- | --- | --- | --- |
| AC-368-01 INDEX 与任务书映射 | `submission/02_docs/README.md`、Issue #368 | 本文件、`manifest.json` | PASS |
| AC-368-02 场景全链路追溯 | SRS/概要/详细/实现/测试文档与 24 个 E2E 场景 | `inventory/traceability.csv` | PASS（#320 最终执行证据为 BLOCKED） |
| AC-368-03 服务/schema/接口/表/调用方向 | `deploy/platform/workloads.json`、`tests/api`、迁移与 `contracts/v2` | 四份 inventory CSV | PASS |
| AC-368-04 可编辑源与 PDF/SVG | `docs/最终提交`、`docs/diagrams` | `editable/`、`rendered/` | 见 `evidence/render-manifest.json` |
| AC-368-05 标题/链接/旧口径 | 八份最终 Markdown | `reports/gaps-and-fixes.md` | 见报告 |
| AC-368-06 原始证据引用 | #307/#319/#320/#340/#366/#367 | `inventory/evidence-status.csv` | 3 PASS / 3 BLOCKED |
| AC-368-07 SHA/命令/计数/哈希 | Git、渲染器与验证器 | `manifest.json`、`SHA256SUMS`、`evidence/` | 见验证日志 |

## 内容导航

- 可编辑最终文档：`editable/final/`（8 个 Markdown 及其本地 assets）。
- 可编辑模型源：`editable/models/`（100 Mermaid、7 PlantUML，另含模型 manifest）。
- PDF：`rendered/pdf/`；模型 SVG：`rendered/svg/models/`。
- 追溯与事实清单：`inventory/`；渲染与验证原始记录：`evidence/`。
- 缺口与修复：`reports/gaps-and-fixes.md`。
