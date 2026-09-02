# Issue #356 Assessment HWK 自审记录

| 项目 | 记录 |
| --- | --- |
| Issue | #356 `[D7-SVC-ASSESS-HWK] 将 HWK + Worker 落位到最终 Assessment 服务` |
| 分支 | `feature/356-assessment-hwk` |
| 基线 | `origin/dev@f948869` |
| 审查提交 | `05d8ae1` |
| 范围 | `AC-356-01` ~ `AC-356-06`；本次只修正 HWK 学生提交在 Course 投影未收敛时的失败语义 |

## 第一轮自审

| 检查项 | 结果 | 记录 |
| --- | --- | --- |
| AC-356-01 ~ AC-356-04 | PASS | 现有 Assessment API/Worker 分离 Compose、持久 task、lease/generation/fencing、outbox 事务测试均位于 `services/assessment`。 |
| AC-356-05 | FAIL | `assessment_course_projection_gap` 存在时，`POST /api/v1/homeworks/{id}/submissions` 仍只按 ACTIVE 成员行判断并返回 201，造成提交、任务与业务写入。红测 `HomeworkWorkflowContractTest#incompleteCourseProjectionReturns503AndWritesNoHomeworkFacts` 实际得到 `201`，预期 `503`。 |
| AC-356-06 后端 | PASS | `mvn -q test`：99 tests，0 failures，0 errors，8 skips（Docker 专项）。 |
| 变更范围/公共契约 | PASS | 不修改 migration、HTTP 路径、请求/成功响应、事件 payload、Worker 或 Compose 配置。 |

## 统一修复

- `CourseMemberProjectionRepository` 增加课程级 projection gap 查询。
- HWK 提交在成员检查前 fail closed；未收敛时返回 `503`、`COURSE_PROJECTION_UNAVAILABLE`、`retryable=true`。
- 增加 API 回归测试，断言 Homework Submission、通用 Submission 与 Evaluation Task 均零新增；发布前已有 outbox 事实不计入本次提交副作用。

## 第二轮自审

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| 红测转绿 | PASS | `mvn -q -Dtest=HomeworkWorkflowContractTest#incompleteCourseProjectionReturns503AndWritesNoHomeworkFacts test`。 |
| HWK 回归 | PASS | `mvn -q -Dtest=HomeworkWorkflowContractTest test`：21 tests，0 failures/errors/skips。 |
| Assessment 回归 | PASS | `mvn -q test`：99 tests，0 failures，0 errors，8 skips。 |
| HWK 前端单测 | PASS | `node node_modules/vitest/vitest.mjs run tests/unit/hwk --pool=threads --reporter=dot`：11 files，182 tests 通过。 |
| 前端类型检查 | PASS | `node node_modules/vue-tsc/bin/vue-tsc.js --noEmit`。 |
| 前端生产构建 | PASS | `node node_modules/vite/bin/vite.js build --debug`。 |
| 差异完整性 | PASS | `git diff --check origin/dev...HEAD` 通过；仅包含 issue #356 的 Assessment HWK 授权/错误处理与测试。 |

## 阻塞与残余风险

- 前端依赖安装首次被 Windows 对 `esbuild.exe` 的 `EPERM` 阻断，改在获授权环境重试后 `npm ci` 成功；当前 npm 11.12.1 仍不满足仓库声明的 npm 10.x，属于工具链兼容性风险。
- Playwright HWK E2E 未执行：本机 `127.0.0.1:8088` 未启动，测试会创建课程 9501 的真实业务数据，不能在缺少集成环境时冒充浏览器验收。
- Docker 沙箱相关 8 个后端集成测试按测试条件跳过；本地 H2 契约测试不替代真实 Compose/Docker 运行证据。

## PR #359 复审返工

| 项目 | 记录 |
| --- | --- |
| 复审结论 | `REQUEST_CHANGES`，仅阻断 `AC-356-06` 与 Issue 要求的真实运行证据。 |
| 已确认通过 | projection gap 下的 `503` 与零业务写入回归、99 个 Assessment 测试、5 个 GitHub CI 检查均已通过；复审未提出代码重构或额外场景。 |
| 缺失证据 | 真实 Compose 下当前 POST 的 taskId/eventId、独立 Worker 的 `PENDING` 到终态日志、assessment-api 与 assessment-worker 健康结果，以及 HWK Playwright E2E。 |

## 第三轮自审

| 检查项 | 结果 | 原始观察 |
| --- | --- | --- |
| Docker daemon 可用性 | FAIL | `docker version --format 'client={{.Client.Version}} server={{.Server.Version}}'` 仅得到 `client=29.7.2 server=`，并报 `dockerDesktopLinuxEngine` 命名管道不存在。 |
| 临时 Assessment Compose | BLOCKED | `docker compose --project-name <独立名称> --file deploy/docker/compose.assessment.yml up --detach --build assessment-api assessment-worker` 在创建任何容器前因同一 daemon 错误退出；未产生可作为证据的 taskId、eventId、状态迁移或健康结果。 |
| Docker Desktop 恢复尝试 | FAIL | 已两次以隐藏后台进程启动 Docker Desktop；第二次按 ServerVersion 非空而非仅退出码判断，60 秒内仍未获得 daemon。 |

### 统一处理结论

- 本轮不修改 Assessment 业务代码：阻断原因是本机 Docker Desktop daemon 未提供 Linux engine，而不是可由代码修复的行为缺陷。
- Docker daemon 恢复后，先运行 `scripts/test/verify-issue-314-recovery-disposable.sh` 的等价真实 Compose 验收，再补 HWK 当前 POST、taskId/eventId、API/Worker 健康和 Playwright E2E 原始输出；这些证据齐全后再开始下一轮复审。
- 先前自审将已知的 Docker/E2E 缺口记为“残余风险”却仍将 Issue 视为可提交，错误地以 H2 契约和本地测试替代了 Issue 明示的真实 Compose 证据；该判断已由本轮复审纠正。

## 第四轮自审（PR #359 第二次 REQUEST_CHANGES 返工，AC-356-05）

| 项目 | 记录 |
| --- | --- |
| 复审结论 | 第二次 `REQUEST_CHANGES`（2026-09-01 03:09，按修订后的 Issue 口径）。前次要求 Compose/Playwright 等证据的 review 已撤销；仅保留 1 个本分支可复现的阻断项 `AC-356-05`。 |
| 阻断原文 | HWK 提交按 `courseId` 查任意 projection gap 后直接 503：① 某用户缺口阻断全课程其他用户；② 相关用户投影不权威时未走真实 `HttpCoursePermissionClient` 做有界 fallback；③ 要求删除本分支 `hasProjectionGap(courseId)` 重复实现、接入公共 `CourseMembershipGuard`（#357/#361 已合入 dev），并用本地延迟 HTTP stub 证明超时 503 与 Homework/Submission/task/source-grade 零写入。 |
| 返工基线 | rebase 到 `origin/dev@8114149e`（含 #361 公共 guard），旧提交 `05d8ae1` 中课程级 gap 实现被替换。 |

### 修复内容

- 删除 `CourseMemberProjectionRepository.hasProjectionGap(courseId)` 课程级查询（还原为 origin/dev 版本）；删除 `CourseProjectionUnavailableException` 及其 503 处理器。
- `HomeworkController.submit` 改为调用公共 `CourseMembershipGuard.isActiveMember(courseId, userId, requestId)`：用户本人投影权威时用本地成员行；不权威时经真实 `HttpCoursePermissionClient.canViewCourse` 有界 fallback（超时/不可用抛 `CourseAuthorizationUnavailableException` → 503 `COURSE_AUTHORIZATION_UNAVAILABLE` retryable）。成员拒绝仍为 403。
- 用户级隔离：其他用户的 gap 不再阻断本用户提交。

### 红测证据（改前）

```bash
mvn -q -Dtest="HomeworkCourseProjectionFallbackTest,HomeworkWorkflowContractTest" test
# Tests run: 24, Failures: 4
# anotherUsersProjectionGapDoesNotBlock...  expected 201, was 503   （课程级阻断）
# courseAuthorizationIsUsedWhenHomeworkProjectionHasAGap  expected 201, was 503 （无 HTTP fallback）
# delayedCourseAuthorizationTimesOut... expected COURSE_AUTHORIZATION_UNAVAILABLE, was COURSE_PROJECTION_UNAVAILABLE
# courseProjectionGapFallsBackToCourseAuthorization...  expected 403, was 503
```

### 绿测证据（改后）

```bash
mvn -q -Dtest="HomeworkCourseProjectionFallbackTest,HomeworkWorkflowContractTest" test   # 3 + 21 tests 全过
mvn -q test                                                                              # 108 tests, 0 failures, 0 errors, 8 skips
```

新增 `HomeworkCourseProjectionFallbackTest`（真实 `HttpCoursePermissionClient` + 本地延迟 HTTP stub，`assessment.course.timeout=PT0.2S`）：
- 相关用户 gap 时 CRS 允许 → 201，写入 1 条 HWK submission + 1 条 task；
- 延迟 stub 超时 → 503 `COURSE_AUTHORIZATION_UNAVAILABLE`、`retryable=true`、requestId 回显，且 `assessment_homework_submission` / `assessment_submission` / `evaluation_task` / `assessment_source_grade` 均零写入；
- 其他用户 gap 不影响本用户提交（201）。

### Rebase 暴露的基线测试隔离缺陷（与本次改动无关，须一并说明）

- 现象：`mvn test` 完整套件在 origin/dev 基线即存在 4 errors（`LabCourseProjectionFallbackTest`、`AssessmentSecurityAndProjectionTest` 的 `resetFacts` 在 `DELETE FROM evaluation_task` 时触发 `fk_assessment_homework_evaluation_task` 外键冲突）。隔离运行两测试类均通过；同一 JVM 内先跑的 HWK 测试（如 `HomeworkWorkerFencingTest` 的 `task-fencing-315`）残留 `assessment_homework_evaluation` 行导致。
- 基线验证：`origin/dev@8114149e` 全量 `mvn test` 同样 104 tests / 4 errors；`git diff origin/dev...HEAD` 不含这两个文件，证明非本次 PR 引入。
- 处理：在两个 LAB 测试的清理中先删除引用 `evaluation_task` 的 HWK 事实表（`assessment_homework_evaluation` / `_review_log` / `_submission`），保持共享 JVM 下完整套件可绿。纯测试改动，不影响生产代码。
- 该缺陷由审核人要求的 rebase 暴露，属 `AC-356-06` 报告精确计数的一部分；若不修，CI backend-gate（`mvn -f services/assessment/pom.xml test`）会变红。

### 为什么前三轮自审未发现该问题

1. 验收口径在开发中途变更：Issue 2026-09-01 追加纠正把公共 guard 所有权从 #356 划给 #357，并规定"#357 合入后 rebase 消费、删除课程级 `hasProjectionGap`"。分支基线 `f948869` 早于 #361 合入，前几轮自审从未同步最新 `origin/dev` 复核公共 guard 是否存在，导致继续按旧口径自洽地实现了课程级 503。
2. 红测设计验证了"已实现行为"而非"契约行为"：`incompleteCourseProjectionReturns503AndWritesNoHomeworkFacts` 只为同一用户种 gap 并断言 503，未覆盖"其他用户 gap 不应阻断本用户"与"gap 时 CRS 可 fallback"两个审核人指出的场景，因此测试全绿也无法暴露缺陷。
3. 自审清单缺少"公共 guard 复用/跨模块重复实现检测"门禁，且 `git diff --check origin/dev...HEAD` 只查格式、不查基线是否过期；"同步最新基线"步骤未执行。
4. 本轮修复即把上述缺口固化为回归测试：`anotherUsersProjectionGapDoesNotBlockThisStudentsHomeworkSubmission` 与 `courseAuthorizationIsUsedWhenHomeworkProjectionHasAGap` 正是前几轮缺失的两个断言方向。

### 本轮自审结论

- 审核意见 4 条要求全部落实并有测试证据（见"修复内容"与"绿测证据"）。
- 自审第二轮（回归）与第三轮（差异复核）：`git diff --check` 通过；无 TODO/FIXME/调试残留/冲突标记；HWK 提交唯一 HTTP 路径已接入公共 guard，通用 `/api/v1/submissions` 端点显式拒绝 `HWK` sourceType，无旁路；requestId 经真实 HTTP client 透传至 CRS stub（延迟 stub 命中即为证明）。
- 公共契约：migration、HTTP 路径、错误信封（`COURSE_AUTHORIZATION_UNAVAILABLE` 沿用既有处理器）、事件 payload、Worker 均未改动；仅 HWK 提交的成员判断语义从"课程级 gap 即 503"改为"用户级权威判断 + CRS fallback"，与 #357 冻结契约一致。
- 结论：`AC-356-05` 满足，无新增回归，PR 允许送复审。残余风险：完整 Playwright/Gateway 主链归 #320；Docker 专项 8 个条件测试按既有规则跳过，均与本次改动无关。
