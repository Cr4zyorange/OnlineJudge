# #262 D2-LRN 最终复测报告

## 结论

**PASS**。#262 范围内的 `FR-LN-01 ~ FR-LN-06`、`NFR-LN-03 ~ NFR-LN-05` 与 `LRN-SC-01 ~ LRN-SC-05` 均已闭环；#295/#297 已整合到最新 `dev`。原 #269 缺陷已复测：真实 LAB 通知先成功标记已读再进入详情，标记失败不导航，并发重复点击只发送一次已读请求。

## 基线与环境

| 项目 | 记录 |
| --- | --- |
| 需求审查锚点 | `origin/dev@8f8e4fc70341c701c25786f12efbffaeca2a3c5f` |
| 最新开发基线 | `origin/dev@50b3b397941aa6879b010a03951d1d19c5a57250` |
| 本轮被测头提交 | `d66b7debaddde20fee2d097c7b8a6096ddd35d6d` |
| 被测提交树 | `bc84f2d7938f2aae1339532a6db5e161e28708b4` |
| 验收分支 | `test/262-lrn-doc-test-closure` |
| 执行时间 | tested SHA 完整复测及证据生成 2026-08-27 14:51–14:58 +08:00 |
| 操作系统 | macOS 26.6.2，arm64 |
| Java / Maven | Java 25 / Maven 3.9.11 |
| Node / npm | Node 25.8.2 / npm 11.11.1 |
| 浏览器 / E2E | Google Chrome 151.0.7922.174 / Playwright 1.62.1 |
| 真实页面入口 | 每轮随机分配本地 Vite/Spring Boot 端口，不复用共享 `E2E_BASE_URL` |
| 数据隔离 | 每轮创建唯一临时 H2 文件库及上传目录，结束后清理精确进程树和临时目录 |
| Docker | 非本次验收前置条件；隔离式 Spring Boot + Vite 路径完成真实联调 |

未记录密码、Token、Cookie、真实个人数据或本机环境文件。

## 场景结果

| 场景 | 主成功 | 备选/异常 | 权限/状态 | 结果 | 主要证据 |
| --- | --- | --- | --- | --- | --- |
| LRN-SC-01 任务中心 | 真实页面展示聚合任务并按实验筛选 | 无任务/加载失败由 API 与前端单测覆盖 | 当前学生课程成员过滤 | PASS | E2E LRN 页面用例；`LearningTaskControllerTest`；`LearningTaskCenterView.spec.ts` |
| LRN-SC-02 继续学习/进度 | 真实进度页展示课程和继续学习入口；保存/恢复由 API 测试覆盖 | 离线队列失败保留、恢复重放 | 非成员拒绝；同用户同课程隔离 | PASS | E2E LRN 页面用例；`LearningProgressControllerTest`；`learningRecordsApi.spec.ts` |
| LRN-SC-03 个人统计 | 真实页面展示本人 7 天趋势和行为统计 | 无数据/失败/缓存回退由前端单测覆盖 | 401/403 不读取旧用户缓存 | PASS | E2E LRN 页面用例；`LearningRecordControllerTest`；`LearningStatisticsView.spec.ts` |
| LRN-SC-04 通知管理/跳转 | LAB/HWK/GRD 真实业务变化均生成本人未读通知；有效通知先标记已读再跳转目标 | 短时断线显示失败并在重试后恢复；重复事件及重复打开均幂等 | 学生创建 LAB 被 403；通知归属隔离、已读/删除 API 状态留痕通过 | PASS | 默认 4-worker LRN E2E 连续两轮 8/8 PASS；双击进入中的 LAB 通知只产生一次 `markNotificationsRead` 请求 |
| LRN-SC-05 提醒设置/触达 | 真实页面加载并保存提醒偏好 | 已提交/关闭非必要提醒跳过，写入失败记录扫描日志 | 必选规则不可非法关闭 | PASS | E2E LRN 页面用例；`ReminderRuleControllerTest`；`ReminderRuleFailureLoggingTest` |

## 公共子流程结果

| 子流程 | 结果 | 说明 |
| --- | --- | --- |
| LRN-SUB-01 成员过滤与任务聚合 | PASS | 单元/API、真实页面筛选和课程成员隔离均通过 |
| LRN-SUB-02 进度/行为/离线重放 | PASS | 保存、断点、限流、同用户缓存与恢复重放自动化通过 |
| LRN-SUB-03 跨模块事件/幂等/失败 | PASS | LAB/HWK/GRD 真实入口均产生通知；重复事件和写入失败日志由现有自动化覆盖 |
| LRN-SUB-04 提醒扫描/偏好/失败日志 | PASS | 规则保存、未提交过滤、偏好和失败日志通过 |
| LRN-SUB-05 通知状态与安全跳转 | PASS | 已读/删除/越权 API、安全跳转、打开有效通知即已读及重复打开幂等均通过 |

## 自动化统计

| 层级 | 命令/范围 | 总数 | 通过 | 失败 | 错误 | 跳过 | 结果 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| 后端单元/API/迁移/集成 | 19 个目标测试类（含最新 dev 的 HWK/LRN 通知契约） | 101 | 101 | 0 | 0 | 0 | PASS |
| 前端单元 | 全量 Vitest | 566 | 566 | 0 | 0 | 0 | PASS |
| 共享 E2E 契约 | `npm run test:e2e:contract` | 3 | 3 | 0 | 0 | 0 | PASS |
| LRN Playwright E2E | 独立临时 H2/随机端口、默认 4 workers：`npm run test:e2e:lrn:disposable`；连续两轮（Playwright 9.4s、7.3s） | 8 | 8 | 0 | 0 | 0 | PASS |
| TypeScript | `npm run typecheck` | 1 | 1 | 0 | 0 | 0 | PASS |
| 前端构建 | `npm run build`（190 modules） | 1 | 1 | 0 | 0 | 0 | PASS |
| 场景文档契约 | `node scripts/test/verify-lrn-doc-test-closure.test.mjs` | 4 | 4 | 0 | 0 | 0 | PASS |
| Mermaid 图源/静态资产 | 5 场景 × 3 层，全部图源真实渲染到临时目录 | 15 | 15 | 0 | 0 | 0 | PASS |
| NFR-LN-01/02 专项浏览器验收 | #295 一次性浏览器 E2E（200 项任务、批量已读、跨模块刷新与计时阈值） | 1 | 1 | 0 | 0 | 0 | PASS |

后端、前端和 E2E 摘要见 [backend-summary.txt](backend-summary.txt)、[frontend-summary.txt](frontend-summary.txt)、[e2e-summary.txt](e2e-summary.txt)；环境记录见 [environment.txt](environment.txt)，可核对的去敏原始输出见 [raw/README.md](raw/README.md)。`git diff --check 50b3b397941aa6879b010a03951d1d19c5a57250...d66b7debaddde20fee2d097c7b8a6096ddd35d6d` 的命令和退出码见 [raw/diff-check.log](raw/diff-check.log)，结果为 exit code 0。evidence 提交的独立门禁还会核对 `execution_sha` 等于其直接父提交，且该提交只包含本目录内证据文件。

## 缺陷与合并复测

| Issue | 影响 | 负责人 | 目标时间 | 复测标准 | 状态 |
| --- | --- | --- | --- | --- | --- |
| [#269 点击有效通知跳转后未标记已读](https://github.com/Cr4zyorange/OnlineJudge/issues/269) | UC-LRN-01、FR-LN-04/05；未读数与用户实际查看行为不一致 | @luoZiHui-maker | 2026-08-25 | 按负责人指示直接并入 #262；同一 E2E 中 `isRead=true`、目标 ID 从未读集合移除、`MARK_READ` 日志存在，重复打开和他人通知隔离通过 | PASS |
| [#295 NFR-LN-01/02 实时刷新与性能验收](https://github.com/Cr4zyorange/OnlineJudge/issues/295) | 共享未读状态、轮询、会话隔离、200 项任务与计时阈值 | #295 负责人 | 已合入 `origin/dev` | 一次性浏览器 E2E 采集真实任务、通知、批量已读、进度保存和跨模块刷新 | PASS（1/1） |

## 页面证据

- [跳转前：真实 LAB/HWK/GRD 通知均为未读](evidence/notification-before-valid-jump.png)
- [跳转后：真实 LAB 详情目标可访问](evidence/valid-jump-target.png)
- [合并复测：目标通知返回列表后已无未读标识](evidence/notification-read-state-after-fix.png)
- [合并复测：已读成功后进入真实实验详情](evidence/notification-read-on-open-after-fix.png)
- Playwright 失败 trace、video、自动截图保留在被 Git 忽略的 `frontend/test-results/`，避免把可能含会话信息的浏览器产物提交远程。

## 图组与边界

- `docs/diagrams/lrn/manifest.json` 一一映射 `LRN-SC-01 ~ LRN-SC-05` 的需求层、概要层、详细层图源和 SVG。
- `LRN-SC-*`、`LRN-SUB-*` 只拆解正式 `UC-LRN-01`，不新增或重排 UC 编号。
- 教师课程学习统计保留为“候选独立场景”，等待教师/助教确认。
- 当前版本通知主路径为数据库落库 + 页面加载/手动刷新/可配置轮询；WebSocket/SSE、Redis 为扩展能力。

## 完成时间

实际完成时间：2026-08-27。#262 范围内的功能、隔离式联调、文档及证据门禁均已通过；#295 的专项浏览器验收也已在整合树上复跑通过。
