# Issue #244｜D1-UC-03 第一轮验证记录

## 结论

执行时间：2026-08-25 09:00–11:05（Asia/Shanghai）。本记录为 11:20 前第一轮结果，尚未关闭 Issue。

| 关键用例 | 主流程 | 备选/异常 | 权限/状态 | 结果 | 证据 | 问题 Issue |
| --- | --- | --- | --- | --- | --- | --- |
| UC-LRN-01 | 任务聚合、进度保存/恢复、通知读取和成绩通知跳转已执行 | 空任务、离线回放、通知失效、重复事件已执行 | 未登录 401、非成员隔离、当前用户通知隔离已执行 | PASS | 自动化/API 记录及 `01`–`05` 页面截图 | 无 |
| UC-GR-05 | 课程总评分析正确展示六类指标和数据时间点 | 单成绩项、非法维度、统计错误提示已执行；快照复用与单项页面指标不符合要求 | 学生访问分析返回 403；教师课程权限自动化通过 | FAIL | 自动化/API、代码定位及 `06`–`07` 页面截图 | #253、#254 |

UC-LRN-01 的真实页面主链路与自动化异常/权限路径均已实际执行，判定 PASS。UC-GR-05 已有两个可复现产品缺陷，判定 FAIL；Issue #244 不应关闭，实际完成时间应在缺陷修复并复测后填写。

## 基线与环境

- 本地与远程基线：`dev` = `origin/dev` = `3a802574415658df98a5df787a31f2c7590897f7`。
- 验证分支：`test/244-uc-lrn-grd-validation`。
- 操作系统：Windows 11 10.0 amd64。
- Java：Oracle JDK 21.0.11；Maven 3.9.16；Spring Boot 3.4.5。
- Node.js 24.16.0；npm 11.13.0；Vite 6.4.2；Vitest 3.2.4。
- 数据库：本地 H2 文件库；演示课程 `9501`。
- 演示身份：仓库集成种子中的学生、教师演示账号；本记录不保存密码或令牌。

## README 启动结果

- `bash scripts/dev/start-dev.sh`：FAIL。脚本以 CRLF 保存，WSL Bash 在第 2 行报 `set: -\r: invalid option`，未进入服务启动逻辑。
- 按 README 手动执行后端 `mvn spring-boot:run`：PASS，Tomcat 在 `8080` 启动。
- 按 README 手动执行前端 `npm run dev -- --host 127.0.0.1`：PASS，Vite 在 `5173` 启动。
- 首次前端测试前 `node_modules` 不存在，原命令因找不到 `vitest` 被判为环境 BLOCKED；执行锁文件安装 `npm ci` 后复跑通过。
- `npm ci` 报告 5 个依赖漏洞（4 high、1 critical）。本任务未执行自动升级；仓库已有 Issue #234 跟踪相关依赖安全风险。

## 自动化统计

| 范围 | 结果 |
| --- | --- |
| LRN + GRD 后端定向测试 | 22 个测试类，92 tests，0 failures，0 errors，0 skipped |
| LRN + GRD 前端定向测试 | 19 files，147 tests，全部通过 |
| 前端类型检查 | PASS |
| 前端生产构建 | PASS，189 modules transformed |

后端定向范围包括 LRN 的任务、进度、行为、通知、提醒规则、迁移、GRD/LRN 集成和演示数据，以及 GRD 的成绩项、来源同步、成绩表、教学分析、复核、迁移和权限。前端定向范围包括全部 LRN 页面/API，以及 GRD 成绩项、成绩总表、教学分析、学生成绩和路由。

## UC-LRN-01 执行证据

### 主流程

- 学生 API 登录与当前用户角色：PASS，角色为 `STUDENT`。
- `GET /api/v1/learning/tasks`：PASS，返回 5 条任务，覆盖 `RESOURCE`、`EXPERIMENT`、`HOMEWORK`。
- 进度保存与恢复：PASS。对课程 `9501` / 资源 `950102` 保存 37% 和断点 `section=verification-244`，随后查询仍为 37%，继续学习地址包含课程、章节、资源和编码后的断点。
- 通知查询与已读：PASS。成绩通知 `950504` 标记已读后再次查询 `isRead=true`，并保留 GRD 来源和成绩目标地址。
- 通知跳转：PASS。真实页面点击“课程成绩已发布”的“查看详情”后进入 `/courses/9501/grades`，页面展示课程总评 89.6 和两条成绩构成；前端定向测试 7/7 通过。

### 本轮真实页面截图

| 文件 | 可验证结果 |
| --- | --- |
| `01-student-tasks.png` | 学习任务中心聚合 5 条资源/实验/作业任务，并展示截止时间、状态和进度 |
| `02-student-progress.png` | 学习进度为 37%，上次位置为 `section=verification-244` |
| `03-student-resume.png` | 继续学习地址携带课程、章节、资源和断点，课程页提示已恢复位置 |
| `04-student-grade-notification.png` | 通知中心展示成绩通知、阅读状态、来源和详情入口 |
| `05-student-grade-detail.png` | 成绩通知跳转至学生成绩页，展示总评与成绩构成 |

### 备选/异常与权限

- 空任务：PASS。不存在课程过滤返回 `total=0`；页面空态由组件测试覆盖。
- 离线同步：PASS。`learningRecordsApi.spec.ts` 实际执行请求失败、同用户/课程离线队列、恢复回放和失败项保留。
- 通知失效：PASS。`NotificationCenterView.spec.ts` 实际执行被删除/非白名单地址，页面显示“入口已失效”且不渲染可点击链接。
- 重复事件：PASS。`NotificationControllerTest` 实际重复投递相同幂等事件，不重复创建通知。
- 未登录任务查询：PASS，返回 HTTP 401。
- 非成员任务/进度/行为隔离、通知仅本人可见：PASS，由后端控制器测试实际执行。

## UC-GR-05 执行证据

### 主流程、单项、权限与异常

- 课程 `9501` 总评分析：PASS。均分/最高分/最低分均为 89.6，及格率 100%，完成率 100%，返回 5 个分数区间，并展示 `sourceDataTime`。
- 单成绩项 `950401` 完整分析 API：PASS。均分/最高分/最低分均为 92，及格率 100%，返回 5 个分数区间。
- 成绩项完成情况 API：PASS，返回平均分、提交/完成/缺失状态和来源时间点。
- 非法统计维度：PASS，返回 HTTP 400 和受控错误。
- 学生访问教师教学分析：PASS，返回 HTTP 403，不暴露分析数据。
- 课程总评分析单次实测约百毫秒以内；单成绩项分析 94 ms，低于 5 秒设计阈值。该结果是本机单用户 smoke，不是负载测试。
- 教师真实页面课程总评：PASS，页面展示均分/最高/最低 89.6、及格率/完成率 100%、5 段分布和数据时间点，证据为 `06-teacher-course-analysis.png`。
- 教师真实页面单成绩项：FAIL。选择“实验一成绩”后页面显示均分 92、完成率 100%，但最高/最低为“-”、及格率 0%、分布为空，证据为 `07-teacher-item-analysis-fail.png`。

### 缺陷 1：统计快照从不复用

同一课程、同一统计目标、来源数据未变时连续查询，两次 `generatedAt` 分别为 `2026-08-25T10:52:27.4778221` 与 `2026-08-25T10:52:27.565729`，`reused=false`。

代码证据：`GradeAnalysisService.analyzeCourseGrades` 每次直接重新计算并调用 `gradeAnalysisSnapshotRepository.save(...)`，未调用已经存在的 `findLatest(...)`。因此“快照存在且来源未变化时复用”的路径无法成立，且会持续新增重复快照。

独立修复 Issue：[#253](https://github.com/Cr4zyorange/OnlineJudge/issues/253) `[GRD] 教学分析在来源未变化时未复用统计快照`。

- 建议负责人：GRD 模块负责人；创建时由项目负责人 `@Cr4zyorange` 指派。
- 复测标准：给定已有同目标快照且最新来源时间不晚于快照 `sourceDataTime`，再次查询返回原快照 `generatedAt` 且快照行数不增加；来源成绩更新时间变化后重新计算并生成新快照。

### 缺陷 2：教师端单成绩项分析丢失关键指标

完整单项分析接口能够返回最高分、最低分、及格率和 5 段分布，但 `TeacherGradeTableView.refreshAnalysis` 在选择成绩项时改调 completion 接口，`completionToAnalysis` 将 `maxScore/minScore` 固定为 `null`、`passRate` 固定为 `0.0000`、`distribution` 固定为空数组。

这会让教师看到平均分与完成情况，却看不到需求明确要求的单任务最高分、最低分、真实及格率和成绩分布；当真实成绩全及格时，页面仍会错误显示及格率 0%。

独立修复 Issue：[#254](https://github.com/Cr4zyorange/OnlineJudge/issues/254) `[GRD] 单成绩项教学分析页面丢失最高/最低分、及格率和分布`。

- 建议负责人：GRD 前端负责人；创建时由项目负责人 `@Cr4zyorange` 指派。
- 复测标准：选择任一 LAB/HWK 成绩项后调用完整 `GRADE_ITEM` 分析契约，页面展示均分、最高分、最低分、真实及格率、完成率、5 段分布、来源时间点；补充失败、无成绩与非法成绩项测试。

## 其他独立问题

独立维护 Issue：[#255](https://github.com/Cr4zyorange/OnlineJudge/issues/255) `[DEV] Windows/WSL 下 README 一键启动脚本因 CRLF 无法运行`。

- 建议负责人：开发基础设施负责人；创建时由项目负责人 `@Cr4zyorange` 指派。
- 复测标准：新检出仓库在 Windows + WSL Bash 中直接执行 README 命令，脚本能启动前后端并在 Ctrl+C 时回收两个子进程；同时增加行尾/启动脚本契约测试。

## 后续复测

- #253、#254 已关联负责人 `@Cr4zyorange` 和可执行复测标准；#255 独立跟踪 README 启动问题。
- #253、#254 修复 PR 合入 `dev` 后，必须从新的完整 SHA 重跑 UC-GR-05 的课程总评、快照复用、单成绩项、无成绩、统计失败和权限路径。
- 当前结论为一个 PASS、一个 FAIL，因此 Issue #244 暂不关闭，也不填写“实际完成时间”。
