# D2-HWK 业务场景与测试闭环

| 项目 | 内容 |
| --- | --- |
| GitHub Issue | #264 `[D2-HWK] 补齐 HWK 业务场景文档与测试闭环` |
| 执行基线 | `origin/dev@a30a096281a01d7169cc0c2d18360aa1a65cd6b0`（已包含 #281 / PR #285 的通知失败整体回滚契约） |
| 本地分支 | `test/264-hwk-doc-test-closure` |
| 实际完成日期 | 2026-08-26 |
| 正式用例边界 | `UC-HWK-01 ~ UC-HWK-02`，不新增或重排 UC 编号 |
| 需求范围 | `FR-HWK-01 ~ FR-HWK-06`、`NFR-HWK-01 ~ NFR-HWK-05` |
| 执行环境 | Windows 11；Java 25；Maven 3.9.16；Node.js 24.15.0；npm 11.12.1；Spring Boot 3.4.5；Vue 3 / Vite 6.4.2；H2；Tectonic 0.17.0 |

## 1 场景边界

HWK 保持两个已经确认的独立业务场景。页面动作、权限检查、附件生命周期、自动评测、人工批阅、重评和统计均按公共子流程或主场景的扩展路径追踪，不提升为新的正式 UC。

| 场景/流程 | 分类 | 归属 | 触发与可验证结果 |
| --- | --- | --- | --- |
| 教师创建并发布作业 | 独立场景 | UC-HWK-02 | 教师保存草稿并确认发布；合法配置变为 PUBLISHED，非法配置保留可修改草稿并返回稳定错误 |
| 学生提交作业并触发自动评测 | 独立场景 | UC-HWK-01 | 学生提交 TEXT/OBJECTIVE/CODE/FILE；系统保存历史并形成评测或待批阅状态，学生只能看到允许公开的反馈 |
| 草稿编辑/删除 | 备选路径 | UC-HWK-02 | 保存草稿、更新草稿或仅逻辑删除 DRAFT；历史和子记录不被级联清理 |
| 题目与测试用例配置 | 公共子流程 | UC-HWK-02 | OBJECTIVE/CODE 发布前完成配置完整性校验 |
| 附件上传/恢复/绑定/下载/清理 | 公共子流程 | UC-HWK-01 | 单附件从 UPLOADED 原子进入 BOUND/DELETED，下载每次重鉴权，失败执行物理对象补偿或延迟清理 |
| 自动评测 | 公共子流程 | UC-HWK-01 | OBJECTIVE/CODE 生成独立评测记录；异常保留提交并记录失败状态 |
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
| 评测异常、人工批阅与重评 | `HomeworkControllerTest`、结果/批阅页面测试 | PASS |
| 附件恢复、失败补偿和清理 | Attachment Service/Controller/Scheduling 测试及 FILE 页面测试 | PASS |
| 统计与待处理名单 | Statistics/Repository/Attention 测试及统计页面测试 | PASS |
| 共享 E2E #267 | PASS | 直接复用 PR #268 的 Playwright runner 与公共夹具；`homework-lifecycle.spec.ts` 2/2 通过，不新建 runner、配置或报告目录 |
| GRD 来源成绩真实链路 | PASS | 创建仅绑定本次 `homeworkId` 且 `includedInFinal=true` 的 HWK 成绩项，调用 GRD `/grades/sync` 后按 `gradeItemId` 与当前学生查询成绩记录，精确断言 `sourceId=homeworkId` 和原始分 88；将 `includedInFinal` 变异为 false 时该断言按预期 RED |
| LRN 发布/成绩通知成功链路 | PASS | 真实发布/成绩发布后从 LRN 通知 API 按 `sourceModule=HWK` 与 `sourceId=homeworkId` 断言落库 |
| 通知投递失败设计/实现一致性 | PASS | #281 / PR #285 已合并；`HomeworkControllerTest#publishRollsBackHomeworkWhenRequiredNotificationDeliveryFails` 证明必需通知投递失败时返回 `503/HWK_5003`，发布事务整体回滚，作业保持 `DRAFT` 且不留下通知记录 |

## 4 执行结果

| 范围 | 总数 | 通过 | 失败 | 错误 | 跳过 | 判定 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| 图组闭环契约 RED | 1 | 0 | 1 | 0 | 0 | 预期失败：缺少拆分后的教师发布 SSD 源文件；规范调整后旧图号断言再次按预期失败 |
| 共享 E2E 契约 RED | 1 | 0 | 1 | 0 | 0 | 预期失败：`frontend/tests/e2e/hwk/homework-lifecycle.spec.ts` 不存在 |
| 文档闭环契约 GREEN | 1 | 1 | 0 | 0 | 0 | PASS |
| HWK 共享 E2E | 2 | 2 | 0 | 0 | 0 | PASS；真实 Spring Boot + Vite，系统 Chrome；本机未安装 Playwright ffmpeg，按共享 runner 开关以 `E2E_FAILURE_ARTIFACTS=off` 运行 |
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
- 闭环复测：真实 Spring Boot + Vite + 系统 Chrome 的 HWK Playwright 2/2 PASS，通知成功链路、四类提交、评测异常、批阅/重评与 GRD 精确成绩消费均通过。

本 Issue 验收范围内无 FAIL 或 BLOCKED 项。

### 5.2 环境风险

当前本地后端使用的真实沙箱不可用，CODE E2E 因此记录 `SYSTEM_ERROR` 并验证提交保留与重评可追踪；客观题自动评测主成功路径仍为 PASS。真实 Docker 沙箱中的 CODE AC/WA/资源限制保留为部署环境专项复核。本项是已披露环境风险，不改变 #264 的闭环 PASS 判定。

## 6 PASS / FAIL / BLOCKED 规则

- `PASS`：命令在本分支真实执行，断言与业务结果均通过。
- `FAIL`：已执行且出现产品行为或文档契约错误；记录复现、影响和独立缺陷 Issue。
- `BLOCKED`：因共享入口或跨模块环境尚不可用而无法执行；不得写成 PASS，也不得以截图代替可执行 E2E。

## 7 当前边界

本 Issue 只修改 HWK 章节、HWK 图源、HWK 测试闭环记录及其契约测试。不修改 AUTH、CRS、LAB、LRN、GRD 的生产代码、数据库结构、API、DTO、事件或状态枚举；按一个 Issue / 一个测试分支 / 一个 PR 提交至 `dev`。
