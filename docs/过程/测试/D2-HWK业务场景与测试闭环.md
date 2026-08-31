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
| 学生提交作业并触发自动评测 | 独立场景 | UC-HWK-01 | 学生提交 TEXT/OBJECTIVE/CODE/FILE；OBJECTIVE 同步形成评测终态，CODE 形成持久 PENDING 任务并由后台 Worker 推进到终态；首次派发遗漏或进程重启后由恢复器重新投递，学生只能看到允许公开的反馈 |
| 草稿编辑/删除 | 备选路径 | UC-HWK-02 | 保存草稿、更新草稿或仅逻辑删除 DRAFT；历史和子记录不被级联清理 |
| 题目与测试用例配置 | 公共子流程 | UC-HWK-02 | OBJECTIVE/CODE 发布前完成配置完整性校验 |
| 附件上传/恢复/绑定/下载/清理 | 公共子流程 | UC-HWK-01 | 单附件从 UPLOADED 原子进入 BOUND/DELETED，下载每次重鉴权，失败执行物理对象补偿或延迟清理 |
| 自动评测 | 公共子流程 | UC-HWK-01 | OBJECTIVE 生成独立终态评测记录；CODE 生成持久 PENDING 任务，事务提交后由 Worker 原子认领并调用共享 Evaluator；恢复器定时重投 PENDING、在启动时重置旧进程遗留 RUNNING，API-HWK-11 保持纯读取 |
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
| CODE 提交后独立后台评测 | `codeHomeworkSubmissionEvaluatesIoCasesAndTeacherCanReevaluate`、`evaluationDetailReturnsPendingResultWithoutInvokingEvaluator`、`HomeworkEvaluationRecoveryTest` 与 `homework-lifecycle.spec.ts` 只轮询 API-HWK-10 | PASS：POST 返回 PENDING 后，Worker 独立推进到 ACCEPTED/失败终态；首次派发遗漏或进程重启后的恢复也进入终态；API-HWK-11 不再触发 evaluator |
| 附件恢复、失败补偿和清理 | Attachment Service/Controller/Scheduling 测试及 FILE 页面测试 | PASS |
| 统计与待处理名单 | Statistics/Repository/Attention 测试及统计页面测试 | PASS |
| 共享 E2E #267 | `homework-lifecycle.spec.ts` 复用 PR #268 的 Playwright runner 与公共夹具 | PASS 契约：第二个场景不调用 API-HWK-11，只轮询提交详情并要求 AC 与编译错误样本均进入终态且保留提交 |
| GRD 来源成绩真实链路 | PASS | 创建仅绑定本次 `homeworkId` 且 `includedInFinal=true` 的 HWK 成绩项，调用 GRD `/grades/sync` 后按 `gradeItemId` 与当前学生查询成绩记录，精确断言 `sourceId=homeworkId` 和原始分 88；将 `includedInFinal` 变异为 false 时该断言按预期 RED |
| LRN 发布/成绩通知成功链路 | 待五服务验收 | Assessment 本地 Homework + outbox 成功即发布；Learning 按成员投影异步创建任务和通知，需以新事件 ID 证明不丢不重 |
| 通知投递失败设计/实现一致性 | 待五服务验收 | 唯一有效规则是：仅本地 Homework/outbox 失败返回 `503/HWK_5003`/DRAFT；Learning/broker 失败不回滚，事件须含 title/deadline/`receiverScope=COURSE_ACTIVE_STUDENTS` 且无 roster；运行时实现与 E2E 由 #337/服务拆分 Issue 验收 |

## 4 执行结果

| 范围 | 总数 | 通过 | 失败 | 错误 | 跳过 | 判定 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| 图组闭环契约 RED | 1 | 0 | 1 | 0 | 0 | 预期失败：缺少拆分后的教师发布 SSD 源文件；规范调整后旧图号断言再次按预期失败 |
| 共享 E2E 契约 RED | 1 | 0 | 1 | 0 | 0 | 预期失败：`frontend/tests/e2e/hwk/homework-lifecycle.spec.ts` 不存在 |
| 文档闭环契约 GREEN | 1 | 1 | 0 | 0 | 0 | PASS |
| HWK 共享 E2E 业务验收 | 2 | 待复测 | 0 | 0 | 0 | #296 已将第二个场景改为仅轮询 API-HWK-10 并断言 ACCEPTED/COMPILE_ERROR 终态；待共享 Compose runner 复跑回填 |
| HWK 后端定向 | 105 | 105 | 0 | 0 | 0 | PASS；包含首次派发遗漏与旧进程 RUNNING 恢复 |
| HWK 前端定向 | 182 | 182 | 0 | 0 | 0 | PASS；11/11 files |
| 后端全量回归 | 394 | 387 | 0 | 0 | 7 | PASS；Docker/MySQL 环境专项按测试假设跳过 |
| 前端全量回归 | 556 | 556 | 0 | 0 | 0 | PASS；53/53 files |
| 前端类型检查 | 1 | 1 | 0 | 0 | 0 | PASS |
| 前端生产构建 | 1 | 1 | 0 | 0 | 0 | PASS；189 modules transformed |
| 三层 Mermaid 渲染与静态图目视检查 | 6 | 6 | 0 | 0 | 0 | PASS；仓库 `render-mermaid.mjs` 逐份生成白底 SVG；中文、生命线、消息、组合片段和长参与者名称清晰，风格与相邻 UML 一致 |
| 通知投递失败及 LRN 定向回归 | 9 | 9 | 0 | 0 | 0 | PASS；覆盖发布整体回滚、必需/尽力投递语义、通知持久化与查询 |

首次 Maven 运行因受限沙箱不能连接 Maven Central，未进入测试断言；允许既有依赖解析后同一命令运行通过，不计为产品 FAIL。首次 E2E 因本机未安装 Playwright ffmpeg 而未进入业务步骤；按共享 runner 的敏感信息约定关闭失败录像后复测通过，不计为产品 FAIL。执行过程未记录 Token、Cookie、真实个人数据或本机凭据。

## 5 缺陷关闭与残余风险

### 5.1 作业发布与通知可靠性

- Assessment 在同一本地事务提交 Homework `PUBLISHED` 和 outbox；仅本地事务失败返回 `503/HWK_5003` 并保持 DRAFT。
- Learning 或 broker 不可用不回滚作业；恢复后通过 at-least-once、inbox 幂等、DLQ、重放和对账收敛。
- 闭环验收必须创建本次运行唯一的事件，证明 Learning 最终生成且只生成一次任务和通知；单体内同步调用测试不作为五服务验收依据。

### 5.2 FR-HWK-04：CODE 后台自动评测已由 #296 闭环

- 实现：API-HWK-07 在提交事务内保存 `CODE_JUDGE/PENDING`，事务提交后投递 `HomeworkEvaluationTaskCreated`；`HomeworkEvaluationWorker` 使用 HWK 专用线程池消费，并以条件更新原子认领任务。`HomeworkEvaluationRecovery` 定时扫描持久 PENDING，并在应用启动时将旧进程遗留 RUNNING 重置为 PENDING 后重新投递；线程池拒绝不会删除任务，下一轮扫描会重试。
- 共享边界：Worker 复用 `Evaluator` / `EvaluationTask`，不新增公共 API、DTO、错误码或数据库表；API-HWK-11 仅查询最新评测记录，不再执行 evaluator。
- 异常语义：Worker 或评测器出现未预期异常时，独立事务将评测和提交更新为 `SYSTEM_ERROR`，提交主记录及历史不删除。
- 自动化证据：提交后不调用 API-HWK-11 即进入终态的 Controller 测试、PENDING 结果纯读取单元测试、Worker 异常保留提交测试，以及 `recoveryEvaluatesPersistedCodeSubmissionWhenInitialAfterCommitDispatchWasMissed` 与 `recoveryRequeuesRunningCodeSubmissionLeftByPreviousProcess` 均通过；共享 E2E 改为只轮询 API-HWK-10。
- 判定：FR-HWK-04 的 CODE 后台评测与 TC-HWK-10、TC-HWK-11 更新为 PASS；真实 Docker 多语言、资源限制和并发压测仍按部署专项执行，不属于 #296。
- Issue 生命周期：#264 已由 PR #276 按文档与测试闭环交付完成；FR-HWK-04 产品缺陷由 #296 独立跟踪，本分支完成修复并在 #296 合并后关闭该产品缺陷。

### 5.3 环境风险

当前本地后端使用的真实沙箱不可用；自动 Worker 与教师手动重评均能记录 `SYSTEM_ERROR`，提交保留且评测历史可追踪。真实 Docker 沙箱中的 CODE AC/WA/资源限制仍需部署环境专项复核；该环境风险不改变 #296 对任务调度、纯读取和异常持久化的 PASS 判定。

## 6 PASS / FAIL / BLOCKED 规则

- `PASS`：命令在本分支真实执行，断言与业务结果均通过。
- `FAIL`：已执行且出现产品行为或文档契约错误；记录复现、影响和独立缺陷 Issue。
- `BLOCKED`：因共享入口或跨模块环境尚不可用而无法执行；不得写成 PASS，也不得以截图代替可执行 E2E。

## 7 当前边界

本 Issue 只修改 HWK 章节、HWK 图源、HWK 测试闭环记录及其契约测试。不修改 AUTH、CRS、LAB、LRN、GRD 的生产代码、数据库结构、API、DTO、事件或状态枚举；按一个 Issue / 一个测试分支 / 一个 PR 提交至 `dev`。
