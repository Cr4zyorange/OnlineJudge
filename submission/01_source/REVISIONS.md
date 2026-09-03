# REVISIONS — 版本与 PR/Issue 说明（Issue #378）

生成方式：从 `git log --merges origin/dev` 与 `git for-each-ref refs/tags` 提取；完整逐提交记录见 `snapshots/git-log-dev-full.txt`（1065 个提交），全部引用见 `snapshots/refs-all.txt`。本表只列里程碑，不替代完整提交记录。

## 冻结两端

| 标识 | SHA | 时间（+08:00） | 说明 |
| --- | --- | --- | --- |
| `monolith-start`（改造前） | tag 对象 `515bd6be…` → commit `78715f21288782a2c7ef1d9c23f933c46569b108` | 2026-08-25 14:50 | PR #260 合并提交；tag message「milestone: 13 legacy use cases maintained and verified」；全仓库唯一标签；已验证为 FINAL_SHA 祖先 |
| FINAL（改造后冻结） | `977338f414a8cb72df157b139c8546d870e8bf23`（tree `90acd5ca…`） | 2026-09-03 17:03 | PR #383（#379 devops 证据）合并；冻结时工作区 `git status --porcelain` 为空 |

## 单体阶段（monolith-start 之前，节选）

- 六模块 AUTH/CRS/LRN/LAB/HWK/GRD 功能与测试按 issue 逐个收口（分支 `feature/65`～`feature/74`、`feature/213`～`feature/284`、`fix/200`～`fix/281`、`test/244`～`test/267` 等，全部可由 bundle 中的对应引用追溯）。
- D3 基础设施合入：#287 数据库启动迁移、#289 容器镜像（PR #302）、#288 Kind 部署（PR #303）、#290 Actions 质量门禁（PR #298）、#292 交付编排（PR #329）。

## 微服务改造阶段（monolith-start → FINAL，按合并时间）

| 时间（2026 年） | PR | Issue/分支 | 内容 |
| --- | --- | --- | --- |
| 08-31 | #353 | docs/305-canonical-five-service | 五服务规范设计冻结 |
| 08-31 | #348 | feature/312-course-service | 课程服务拆分 |
| 08-31 | #351 / #352 | feature/314 / 315 | 评测（assessment）服务承接 LAB / HWK |
| 09-01 | #358 | feature/306-three-service-baseline | 三业务服务基线 |
| 09-01 | #333 | feature/317-gateway-routing | 网关路由 |
| 09-01 | #354 | feature/339-grade-service | 成绩服务 |
| 09-01 | #359 / #361 / #362 | feature/356 / 357 / 355 | 评测业务（HWK/LAB）与课程学习链路迁移收口 |
| 09-01 | #363 | feature/318-five-service-cicd | 五服务 CI/CD |
| 09-02 | #365 | fix/364-grade-mysql-readiness | Grade MySQL 就绪探针修复 |
| 09-02 | #371 | test/366-delivery-accept | 交付验收（#366） |
| 09-02 | #376 | fix/318-image-build-network-retries | 镜像构建网络重试 |
| 09-02 | #360 | test/307-monolith-three-service-perf | 单体 vs 三服务性能对比（3 轮×3 场景×2 形态） |
| 09-03 | #374 | feature/319-observability-hpa-clean | 可观测性与 HPA |
| 09-03 | #377 | feature/320-three-service-e2e | 三服务端到端验收 |
| 09-03 | #372 | feature/340-resilience | 韧性注入与停机验收 |
| 09-03 | #387 / #389 | fix/386 / fix/388 | JWKS 交付与 Grade MySQL 契约修复 |
| 09-03 | #384 / #390 | feature/369 / docs/369 | 05_management 交付（#369） |
| 09-03 | #385（合并后被 `0b7d599a` revert） | docs/368 | 02_docs 交付包（#368）返工中，冻结时未计入 dev |
| 09-03 | #395 | docs/380-04-tests-archive | 04_tests 交付（#380） |
| 09-03 | #383 | feature/379-devops-evidence | 03_devops 交付（#379）＝FINAL 所在提交 |

## 冻结时点状态（诚实记录）

- #366、#367、#307、#319、#320、#340 等工程前置均已合入 FINAL；#368（02_docs）在冻结基线中处于被 revert 后的返工状态，其影响范围仅 `submission/02_docs/`，不影响源码与数据库材料。
- #381（06_defense 视频，PR #394）冻结时为草稿；#378（本 Issue，PR #382）为冻结后追加的索引/清单 PR，不改变两个源码归档的内容。
- 归档、bundle 与最终 ZIP 的物理分发由总控 #321 汇合；本目录只固化索引、清单、哈希与验证证据。
