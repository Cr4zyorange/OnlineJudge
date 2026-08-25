# #262 D2-LRN 第一轮验证报告

## 结论

**FAIL**。`LRN-SC-01/02/03/05` 为 PASS，`LRN-SC-04` 为 FAIL：真实 LAB 通知点击后成功进入实验详情，但通知未变为已读。缺陷已进入独立修复 Issue [#269](https://github.com/Cr4zyorange/OnlineJudge/issues/269)。#262 保持进行中，实际完成时间不填写；修复合入最新 `dev` 后复测。

## 基线与环境

| 项目 | 记录 |
| --- | --- |
| 需求审查锚点 | `origin/dev@8f8e4fc70341c701c25786f12efbffaeca2a3c5f` |
| 第一轮执行 SHA | `origin/dev@758afd98ba2caad5a00fb6e12413c48f0156b2fb` |
| 验收分支 | `test/262-lrn-doc-test-closure` |
| 执行时间 | 2026-08-25 15:24–15:44 +08:00 |
| 操作系统 | Windows 11 家庭版 中文版，amd64 |
| Java / Maven | Java 21.0.11 / Maven 3.9.16 |
| Node / npm | Node 24.16.0 / npm 11.13.0 |
| 浏览器 / E2E | Google Chrome 151.0.7922.138 / Playwright 1.62.1 |
| 真实页面入口 | `http://127.0.0.1:5173`，Vite 代理本地 Spring Boot `8080`，H2 文件库及公开演示数据 |
| Docker | Docker daemon 未运行；按 #267 支持的 Spring Boot + Vite 路径完成真实联调 |

未记录密码、Token、Cookie、真实个人数据或本机环境文件。

## 场景结果

| 场景 | 主成功 | 备选/异常 | 权限/状态 | 结果 | 主要证据 |
| --- | --- | --- | --- | --- | --- |
| LRN-SC-01 任务中心 | 真实页面展示聚合任务并按实验筛选 | 无任务/加载失败由 API 与前端单测覆盖 | 当前学生课程成员过滤 | PASS | E2E LRN 页面用例；`LearningTaskControllerTest`；`LearningTaskCenterView.spec.ts` |
| LRN-SC-02 继续学习/进度 | 真实进度页展示课程和继续学习入口；保存/恢复由 API 测试覆盖 | 离线队列失败保留、恢复重放 | 非成员拒绝；同用户同课程隔离 | PASS | E2E LRN 页面用例；`LearningProgressControllerTest`；`learningRecordsApi.spec.ts` |
| LRN-SC-03 个人统计 | 真实页面展示本人 7 天趋势和行为统计 | 无数据/失败/缓存回退由前端单测覆盖 | 401/403 不读取旧用户缓存 | PASS | E2E LRN 页面用例；`LearningRecordControllerTest`；`LearningStatisticsView.spec.ts` |
| LRN-SC-04 通知管理/跳转 | LAB/HWK/GRD 真实业务变化均生成本人未读通知，目标跳转成功 | 短时断线显示失败并在重试后恢复；重复事件幂等由 API 测试覆盖 | 学生创建 LAB 被 403；通知归属隔离、已读/删除 API 状态留痕通过 | FAIL | E2E 3 条中 1 FAIL；点击有效 LAB 通知后 `isRead=false`；[#269](https://github.com/Cr4zyorange/OnlineJudge/issues/269) |
| LRN-SC-05 提醒设置/触达 | 真实页面加载并保存提醒偏好 | 已提交/关闭非必要提醒跳过，写入失败记录扫描日志 | 必选规则不可非法关闭 | PASS | E2E LRN 页面用例；`ReminderRuleControllerTest`；`ReminderRuleFailureLoggingTest` |

## 公共子流程结果

| 子流程 | 结果 | 说明 |
| --- | --- | --- |
| LRN-SUB-01 成员过滤与任务聚合 | PASS | 单元/API、真实页面筛选和课程成员隔离均通过 |
| LRN-SUB-02 进度/行为/离线重放 | PASS | 保存、断点、限流、同用户缓存与恢复重放自动化通过 |
| LRN-SUB-03 跨模块事件/幂等/失败 | PASS | LAB/HWK/GRD 真实入口均产生通知；重复事件和写入失败日志由现有自动化覆盖 |
| LRN-SUB-04 提醒扫描/偏好/失败日志 | PASS | 规则保存、未提交过滤、偏好和失败日志通过 |
| LRN-SUB-05 通知状态与安全跳转 | FAIL | 已读/删除/越权 API 通过，安全跳转通过；“打开有效通知即已读”失败，见 #269 |

## 自动化统计

| 层级 | 命令/范围 | 总数 | 通过 | 失败 | 错误 | 跳过 | 结果 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| 后端单元/API/迁移/集成 | 16 个 LRN 目标测试类（含 `LrnCrossModuleEventIntegrationTest`、`GrdLrnIntegrationTest`） | 45 | 45 | 0 | 0 | 0 | PASS |
| 前端单元 | 13 个 LRN 及 CRS/LAB/HWK 联动文件 | 112 | 112 | 0 | 0 | 0 | PASS |
| 共享 E2E 契约 | `npm run test:e2e:contract` | 3 | 3 | 0 | 0 | 0 | PASS |
| LRN Playwright E2E | `lrn-business-closure.spec.ts` | 3 | 2 | 1 | 0 | 0 | FAIL |
| TypeScript | `npm run typecheck` | 1 | 1 | 0 | 0 | 0 | PASS |
| 前端构建 | `npm run build`（189 modules） | 1 | 1 | 0 | 0 | 0 | PASS |
| 场景文档契约 | `node --test scripts/test/verify-lrn-doc-test-closure.test.mjs` | 3 | 3 | 0 | 0 | 0 | PASS |
| Mermaid 图源/静态资产 | 5 场景 × 3 层 | 15 | 15 | 0 | 0 | 0 | PASS |
| NFR-LN-01/02 真实时延与响应时间 | 未执行逐项计时/压力采样 | 2 | 0 | 0 | 0 | 2 | BLOCKED |

后端、前端和 E2E 摘要见 [backend-summary.txt](backend-summary.txt)、[frontend-summary.txt](frontend-summary.txt)、[e2e-summary.txt](e2e-summary.txt)；环境记录见 [environment.txt](environment.txt)。

## 缺陷与复测标准

| Issue | 影响 | 负责人 | 目标时间 | 复测标准 | 状态 |
| --- | --- | --- | --- | --- | --- |
| [#269 点击有效通知跳转后未标记已读](https://github.com/Cr4zyorange/OnlineJudge/issues/269) | UC-LRN-01、FR-LN-04/05；未读数与用户实际查看行为不一致 | @luoZiHui-maker | 2026-08-26 | 修复合入最新 `dev`；同步验收分支；同一 E2E 中跳转成功后 `isRead=true`、未读数减少、`MARK_READ` 日志存在；重复点击和他人通知隔离仍通过 | FAIL |

## 页面证据

- [跳转前：真实 LAB/HWK/GRD 通知均为未读](evidence/notification-before-valid-jump.png)
- [跳转后：真实 LAB 详情目标可访问](evidence/valid-jump-target.png)
- Playwright 失败 trace、video、自动截图保留在被 Git 忽略的 `frontend/test-results/`，避免把可能含会话信息的浏览器产物提交远程。

## 图组与边界

- `docs/diagrams/lrn/manifest.json` 一一映射 `LRN-SC-01 ~ LRN-SC-05` 的需求层、概要层、详细层图源和 SVG。
- `LRN-SC-*`、`LRN-SUB-*` 只拆解正式 `UC-LRN-01`，不新增或重排 UC 编号。
- 教师课程学习统计保留为“候选独立场景”，等待教师/助教确认。
- 当前版本通知主路径为数据库落库 + 页面加载/手动刷新/可配置轮询；WebSocket/SSE、Redis 为扩展能力。

## 完成时间

实际完成时间：**留空**。#269 修复并在最新 `dev` 复测为 PASS 前，不关闭 #262。
