# D2-HWK 业务场景与测试闭环

| 项目 | 内容 |
| --- | --- |
| GitHub Issue | #264 `[D2-HWK] 补齐 HWK 业务场景文档与测试闭环` |
| 执行基线 | `origin/dev@a30a096281a01d7169cc0c2d18360aa1a65cd6b0`（已包含 #281 / PR #285 的通知失败整体回滚契约） |
| 本地分支 | `test/264-hwk-doc-test-closure` |
| 最近复审日期 | 2026-08-27 |
| 实际完成日期 | 2026-08-27 |
| 正式用例边界 | `UC-HWK-01 ~ UC-HWK-02`，不新增或重排 UC 编号 |
| 需求范围 | `FR-HWK-01 ~ FR-HWK-06`、`NFR-HWK-01 ~ NFR-HWK-05` |
| 执行环境 | Windows 11；Java 25；Maven 3.9.16；Node.js 24.15.0；npm 11.12.1；Spring Boot 3.4.5；Vue 3 / Vite 6.4.2；H2；Tectonic 0.17.0 |

## 1 场景边界

HWK 保持两个已经确认的独立业务场景。页面动作、权限检查、附件生命周期、自动评测、人工批阅、重评和统计均按公共子流程或主场景的扩展路径追踪，不提升为新的正式 UC。

| 场景/流程 | 分类 | 归属 | 触发与可验证结果 |
| --- | --- | --- | --- |
| 教师创建并发布作业 | 独立场景 | UC-HWK-02 | 教师保存草稿并确认发布；合法配置变为 PUBLISHED，非法配置保留可修改草稿并返回稳定错误 |
| 学生提交作业并触发自动评测 | 独立场景 | UC-HWK-01 | 学生提交 TEXT/OBJECTIVE/CODE/FILE；OBJECTIVE 同步形成评测终态，CODE 当前只形成 PENDING 记录且没有独立 Worker，学生只能看到允许公开的反馈 |
| 草稿编辑/删除 | 备选路径 | UC-HWK-02 | 保存草稿、更新草稿或仅逻辑删除 DRAFT；历史和子记录不被级联清理 |
| 题目与测试用例配置 | 公共子流程 | UC-HWK-02 | OBJECTIVE/CODE 发布前完成配置完整性校验 |
| 附件上传/恢复/绑定/下载/清理 | 公共子流程 | UC-HWK-01 | 单附件从 UPLOADED 原子进入 BOUND/DELETED，下载每次重鉴权，失败执行物理对象补偿或延迟清理 |
| 自动评测 | 公共子流程 | UC-HWK-01 | OBJECTIVE 生成独立终态评测记录；CODE 只生成 PENDING 记录，当前由 API-HWK-11 读取请求同步触发 evaluator，与后台 Worker 契约不一致 |
| 教师批阅/重评 | 扩展路径 | UC-HWK-01 | 教师对待批阅提交评分，或新建 REJUDGE 记录；评分、评语和操作日志可追踪 |
| 统计与待处理名单 | 查询子流程 | UC-HWK-01 | 课程管理者查询固定五档、未提交、待评测和待批阅分页结果 |

## 2 三层图追踪

| 独立场景 | 需求层 | 概要层 | 详细层 |
| --- | --- | --- | --- |
| UC-HWK-02 教师创建并发布作业 | SRS 图 4-14B；`fig_4_14b_hwk_publish_ssd.mmd` | OOD 5.2 与 HWK 概要稿 9.1；`fig_5_2_hwk_02_publish_component.mmd` | DSD 与 HWK 详细设计稿 5.1.1；`fig_3_5_3a_hwk_publish_object.mmd` |
| UC-HWK-01 学生提交作业并触发自动评测 | SRS 图 4-14A；`fig_4_14a_hwk_submission_ssd.mmd` | OOD 5.2 与 HWK 概要稿 9.2；`fig_5_2_hwk_01_submission_component.mmd` | DSD 与 HWK 详细设计稿 5.1.2；`fig_3_5_3b_hwk_submission_object.mmd` |

## 3 可执行验证矩阵

| 路径 | 主要自动化证据 | 当前判定 |
| --- | --- | --- |
| 创建、配置、草稿、发布、删除 | `HomeworkControllerTest`、`HomeworkServiceDeleteTest`、`HomeworkTeacherView.spec.ts`、`HomeworkEditorView.spec.ts` | PASS |
| TEXT/OBJECTIVE/CODE/FILE 提交与历史 | `HomeworkControllerTest`、`HomeworkSubmissionServiceTest`、`HomeworkAttachmentControllerTest`、学生提交/历史前端测试 | PASS |
| 截止、迟交、禁止重交、越权与隐藏答案 | HWK Controller/Bearer/Service 测试及学生结果页测试 | PASS |
| 客观题评测、人工批阅与手动重评 | `HomeworkControllerTest`、结果/批阅页面测试 | PASS |
| CODE 提交后独立后台评测 | `homework-lifecycle.spec.ts` 在不调用 API-HWK-11 的情况下等待后读取提交详情 | FAIL：POST 仅创建 PENDING 记录；等待 1 秒后仍为 PENDING，首次 API-HWK-11 读取才同步执行 evaluator |
| 附件恢复、失败补偿和清理 | Attachment Service/Controller/Scheduling 测试及 FILE 页面测试 | PASS |
| 统计与待处理名单 | Statistics/Repository/Attention 测试及统计页面测试 | PASS |
| 共享 E2E #267 | `homework-lifecycle.spec.ts` 复用 PR #268 的 Playwright runner 与公共夹具 | PASS（runner）：原始计数为 2 passed、0 failed；第二个通过的断言明确复现 CODE 后台评测缺失。runner 通过不等于 FR-HWK-04 产品验收通过，后者单独判定 FAIL |
| GRD 来源成绩真实链路 | PASS | 创建仅绑定本次 `homeworkId` 且 `includedInFinal=true` 的 HWK 成绩项，调用 GRD `/grades/sync` 后按 `gradeItemId` 与当前学生查询成绩记录，精确断言 `sourceId=homeworkId` 和原始分 88；将 `includedInFinal` 变异为 false 时该断言按预期 RED |
| LRN 发布/成绩通知成功链路 | PASS | 真实发布/成绩发布后从 LRN 通知 API 按 `sourceModule=HWK` 与 `sourceId=homeworkId` 断言落库 |
| 通知投递失败设计/实现一致性 | PASS | #281 / PR #285 已合并；`HomeworkControllerTest#publishRollsBackHomeworkWhenRequiredNotificationDeliveryFails` 证明必需通知投递失败时返回 `503/HWK_5003`，发布事务整体回滚，作业保持 `DRAFT` 且不留下通知记录 |

## 4 执行结果

| 范围 | 总数 | 通过 | 失败 | 错误 | 跳过 | 判定 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| 图组闭环契约 RED | 1 | 0 | 1 | 0 | 0 | 预期失败：缺少拆分后的教师发布 SSD 源文件；规范调整后旧图号断言再次按预期失败 |
| 共享 E2E 契约 RED | 1 | 0 | 1 | 0 | 0 | 预期失败：`frontend/tests/e2e/hwk/homework-lifecycle.spec.ts` 不存在 |
| 文档闭环契约 GREEN | 1 | 1 | 0 | 0 | 0 | PASS |
| HWK 共享 E2E runner | 2 | 2 | 0 | 0 | 0 | PASS；第二个通过的断言复现 CODE 提交后无后台 Worker、提交持续 PENDING。FR-HWK-04 的 CODE 后台 Worker 产品验收另行判定 FAIL（见 §5.2 / #296） |
| HWK 后端定向 | 101 | 101 | 0 | 0 | 0 | PASS |
| HWK 前端定向 | 182 | 182 | 0 | 0 | 0 | PASS；11/11 files |
| 后端全量回归 | 375 | 370 | 0 | 0 | 5 | PASS；Docker/环境专项按测试假设跳过 |
| 前端全量回归 | 556 | 556 | 0 | 0 | 0 | PASS；53/53 files |
| 前端类型检查 | 1 | 1 | 0 | 0 | 0 | PASS |
| 前端生产构建 | 1 | 1 | 0 | 0 | 0 | PASS；189 modules transformed |
| 三层 Mermaid 渲染与静态图目视检查 | 6 | 6 | 0 | 0 | 0 | PASS；仓库 `render-mermaid.mjs` 逐份生成白底 SVG；中文、生命线、消息、组合片段和长参与者名称清晰，风格与相邻 UML 一致 |
| 通知投递失败及 LRN 定向回归 | 9 | 9 | 0 | 0 | 0 | PASS；覆盖发布整体回滚、必需/尽力投递语义、通知持久化与查询 |

首次 Maven 运行因受限沙箱不能连接 Maven Central，未进入测试断言；允许既有依赖解析后同一命令运行通过，不计为产品 FAIL。首次 E2E 因本机未安装 Playwright ffmpeg 而未进入业务步骤；按共享 runner 的敏感信息约定关闭失败录像后复测通过，不计为产品 FAIL。执行过程未记录 Token、Cookie、真实个人数据或本机凭据。

## 5 缺陷关闭与残余风险

### 5.1 #281：通知投递失败契约已关闭

- 确认契约：`HOMEWORK_PUBLISHED` 是发布事务的必需通知；投递失败则发布整体失败并回滚。
- 合并证据：#281 / PR #285 已合并到 `dev`，合并提交 `a30a096281a01d7169cc0c2d18360aa1a65cd6b0`。
- 定向复测：`publishRollsBackHomeworkWhenRequiredNotificationDeliveryFails`、`PersistentNotificationEventPublisherTest`、`NotificationControllerTest` 共 9/9 PASS；失败响应为 `503/HWK_5003`，作业保持 `DRAFT`。
- 闭环复测：通知成功链路、四类提交、批阅/手动重评与 GRD 精确成绩消费均执行；CODE 提交后的独立后台评测未通过，因此不能以 Playwright runner 2/2 通过替代产品验收。

### 5.2 FR-HWK-04：CODE 后台自动评测未闭环

- 复现：CODE 提交 POST 返回 `PENDING`；不调用 `GET /api/v1/submissions/{id}/evaluation`，等待 1 秒后通过只读提交详情查询仍为 `PENDING`。
- 代码证据：`createInitialEvaluation` 只保存 `CODE_JUDGE/PENDING`；`evaluationDetail` 调用 `latestOrCreateEvaluation`，在 API-HWK-11 读请求中同步执行 evaluator。
- 判定：FR-HWK-04 的客观题自动评分为 PASS，CODE “提交后创建任务并由后台 Worker 异步执行”为 FAIL；TC-HWK-10、TC-HWK-11 不通过，由修复 Issue #296 实现任务调度并补不读取 API-HWK-11 的终态测试。
- 责任与计划：修复 Issue #296，负责人 @terrana37，计划开始 2026-08-29，目标完成 2026-09-05；复测标准：不调用 API-HWK-11 的独立终态测试通过，TC-HWK-10/11 更新为 PASS。
- Issue #264 结论：文档与测试闭环交付完成，可按 Issue #264 关闭；FR-HWK-04 的 CODE 后台 Worker 产品验收仍为 FAIL，修复由 #296 独立跟踪。#264 记录复现、影响、责任人与复测标准，不以该产品缺口冒充 PASS。

### 5.3 环境风险

当前本地后端使用的真实沙箱不可用；教师手动重评可进入同步 evaluator 并记录 `SYSTEM_ERROR`，提交保留与重评可追踪。真实 Docker 沙箱中的 CODE AC/WA/资源限制仍需部署环境专项复核；该环境风险独立于已确认的后台 Worker 缺失，不改变 FR-HWK-04 的 FAIL 判定。

## 6 PASS / FAIL / BLOCKED 规则

- `PASS`：命令在本分支真实执行，断言与业务结果均通过。
- `FAIL`：已执行且出现产品行为或文档契约错误；记录复现、影响和独立缺陷 Issue。
- `BLOCKED`：因共享入口或跨模块环境尚不可用而无法执行；不得写成 PASS，也不得以截图代替可执行 E2E。

## 7 当前边界

本 Issue 只修改 HWK 章节、HWK 图源、HWK 测试闭环记录及其契约测试。不修改 AUTH、CRS、LAB、LRN、GRD 的生产代码、数据库结构、API、DTO、事件或状态枚举；按一个 Issue / 一个测试分支 / 一个 PR 提交至 `dev`。
