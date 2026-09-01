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
