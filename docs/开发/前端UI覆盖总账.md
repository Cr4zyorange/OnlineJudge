# 前端 UI 覆盖总账

本总账以 `docs/最终提交/软件详细设计说明书.md` 的正式 UI 编号为边界，记录当前 Vue Router 中可以承载该责任的真实路由和组件。`meta.uiIds` 是追踪标记，不代表每个编号已经拥有独立 URL、独立组件或完整浏览器验收。

状态口径：`已实现` 表示已有与设计责任基本对应的路由页面；`合并承载` 表示责任已有真实实现，但与其他编号共用路由、组件或页内工作区；`缺失` 表示尚无真实实现；`延期` 表示已有明确延期决定。证据优先指向路由契约和相应行为测试。

| ID | 模块 | 设计责任 | 当前状态 | 真实路由 + 组件 | 证据 |
| --- | --- | --- | --- | --- | --- |
| UI-AUTH-01 | AUTH | 登录页面：账号密码登录、失败提示、进入角色工作区 | 合并承载 | `/login` — `AuthView.vue` | `router.ts` 的 `login`；`AuthView.spec.ts` 登录用例 |
| UI-AUTH-02 | AUTH | 注册页面：填写账号资料并创建平台账号 | 合并承载 | `/register` — `AuthView.vue` | `router.ts` 的 `register`；`AuthView.spec.ts` 注册失败用例 |
| UI-AUTH-03 | AUTH | 个人资料页面：查看和维护基础信息 | 合并承载 | `/profile` — `AuthProfileView.vue` | `router.ts` 的 `profile`；`AuthProfileView.spec.ts` 资料保存用例 |
| UI-AUTH-04 | AUTH | 修改密码页面：校验原密码和新密码并提交 | 合并承载 | `/profile/password` — `AuthProfileView.vue` | `router.ts` 的 `profile-password`；`AuthProfileView.spec.ts` 密码用例 |
| UI-AUTH-05 | AUTH | 用户管理页面：查询、创建、启用和禁用用户 | 合并承载 | `/admin/auth` — `AuthAdminView.vue` | `router.ts` 的 `auth-admin`；`AuthAdminView.spec.ts` 用户管理用例 |
| UI-AUTH-06 | AUTH | 角色管理页面：查询、创建和维护角色 | 合并承载 | `/admin/auth` — `AuthAdminView.vue` | `AuthAdminView.spec.ts` 角色创建与更新用例 |
| UI-AUTH-07 | AUTH | 权限分配页面：查询权限点并调整角色权限 | 合并承载 | `/admin/auth` — `AuthAdminView.vue` | `AuthAdminView.spec.ts` 角色权限更新用例 |
| UI-AUTH-08 | AUTH | 用户角色分配页面：分配或移除用户角色 | 合并承载 | `/admin/auth` — `AuthAdminView.vue` | `AuthAdminView.spec.ts` 用户角色更新用例 |
| UI-AUTH-09 | AUTH | 安全审计日志页面：按操作人、类型、时间和结果筛选 | 合并承载 | `/admin/auth` — `AuthAdminView.vue` | `AuthAdminView.spec.ts` 审计筛选用例 |
| UI-AUTH-10 | AUTH | 无权限提示页面：解释拒绝原因并提供返回入口 | 合并承载 | `/403` — `AuthStatusView.vue` | `router.ts` 的 `forbidden`；`router.spec.ts` 权限拒绝用例 |
| UI-AUTH-11 | AUTH | 登录失效提示页面：提示会话过期并引导登录 | 合并承载 | `/session-expired` — `AuthStatusView.vue` | `router.ts` 的 `session-expired`；`router.spec.ts` 失效会话用例 |
| UI-CRS-01 | CRS | 课程列表页面：查看、搜索和加入课程 | 合并承载 | `/courses` — `CourseManagementView.vue` | `router.ts` 的 `courses`；`CourseManagementView.spec.ts` 列表和加入用例 |
| UI-CRS-02 | CRS | 课程详情页面：查看课程、章节、公告和成员信息 | 合并承载 | `/courses/:courseId` — `CourseManagementView.vue` | `router.ts` 的 `course-home`；`PageState` 加载/失败态和课程主页用例 |
| UI-CRS-03 | CRS | 章节管理页面：创建、编辑、排序和删除章节 | 合并承载 | `/courses` 页内章节工作区 — `CourseManagementView.vue` | `openChapterManagement`；章节保存与拖拽排序用例 |
| UI-CRS-04 | CRS | 资源管理页面：上传、分类、下载和删除资源 | 合并承载 | `/courses` 页内资源工作区 — `CourseManagementView.vue` | `openResourceManagement`；资源下载用例 |
| UI-CRS-05 | CRS | 成员管理页面：审批、调角色和移除成员 | 合并承载 | `/courses/:courseId` 详情区 — `CourseManagementView.vue` | 成员审批、角色调整和移除用例 |
| UI-CRS-06 | CRS | 公告管理页面：发布、编辑、置顶和删除公告 | 合并承载 | `/courses` 页内公告工作区 — `CourseManagementView.vue` | `openAnnouncementManagement`；公告发布用例 |
| UI-CRS-07 | CRS | 课程管理页面：创建、编辑和归档课程 | 合并承载 | `/courses` 管理视图 — `CourseManagementView.vue` | 课程创建和管理布局用例 |
| UI-LAB-01 | LAB | 实验列表页面：按课程和状态查看实验 | 已实现 | `/courses/:courseId/labs` — `CourseLabIndexView.vue`；管理态 `/labs/manage` — `LabTeacherView.vue` | `router.ts` 的 `course-labs`、`lab-manage` |
| UI-LAB-02 | LAB | 学生实验详情：查看要求、提交内容和进入结果 | 合并承载 | `/courses/:courseId/labs/:labId` 与 `/submit` — `LabStudentView.vue` | `router.spec.ts` 学生 detail/submit 契约 |
| UI-LAB-03 | LAB | 教师实验详情：管理实验和查看提交 | 合并承载 | `/courses/:courseId/labs/:labId/manage` — `LabManageView.vue`；`/manage/submissions` — `LabSubmissionWorkspaceView.vue` | `router.spec.ts` 教师管理路由矩阵 |
| UI-LAB-04 | LAB | 实验发布和编辑页面 | 已实现 | `/courses/:courseId/labs/new`、`/:labId/edit` — `LabEditorView.vue` | `router.spec.ts` create/edit 契约；`LabEditorView.spec.ts` |
| UI-LAB-05 | LAB | 学生提交历史页面 | 已实现 | `/courses/:courseId/labs/:labId/submissions` — `LabSubmissionHistoryView.vue` | `router.ts` 的 `lab-submission-history`；对应组件测试 |
| UI-LAB-06 | LAB | 教师评分页面：查看提交并填写评分反馈 | 合并承载 | `/manage/submissions` — `LabSubmissionWorkspaceView.vue`；`/:submissionId` — `LabSubmissionReviewView.vue` | `router.spec.ts` workspace/review 契约；评分组件测试 |
| UI-LAB-07 | LAB | 学生实验结果页面 | 已实现 | `/labs/:labId/result` 与 `/submissions/:submissionId/result` — `LabSubmissionResultView.vue` | `router.spec.ts` latest/historic result 契约 |
| UI-LAB-08 | LAB | 教师实验统计页面 | 已实现 | `/courses/:courseId/labs/:labId/manage/statistics` — `LabStatisticsView.vue` | `router.spec.ts` statistics 契约；`LabStatisticsView.spec.ts` |
| UI-HWK-01 | HWK | 作业中心页面：按角色、状态和关键词查看作业；教师总览仅对 DRAFT 提供 API-HWK-22 确认式逻辑删除，失败保留原行、成功刷新并在末页为空时回退 | 合并承载 | `/courses/:courseId/homeworks` — `CourseHomeworkIndexView.vue`；管理态 `/homeworks/manage` — `HomeworkTeacherView.vue` | `router.ts` 的学生/教师作业入口；`HomeworkTeacherView.spec.ts` 的删除可见性、确认/pending/失败保留/末页回退契约；1440px/390px 浏览器证据 |
| UI-HWK-02 | HWK | 教师作业创建和编辑页面 | 已实现 | `/homeworks/new`、`/:homeworkId/edit` — `HomeworkEditorView.vue` | `router.spec.ts` create/edit 契约；`HomeworkEditorView.spec.ts` |
| UI-HWK-03 | HWK | 作业发布管理页面 | 合并承载 | `/homeworks/manage` — `HomeworkTeacherView.vue`；`/:homeworkId/manage` — `HomeworkManageView.vue` | `router.spec.ts` manage 契约；管理组件测试 |
| UI-HWK-04 | HWK | 学生作业详情页面 | 合并承载 | `/courses/:courseId/homeworks/:homeworkId` — `HomeworkStudentView.vue` | `router.spec.ts` homework-detail 契约；`HomeworkStudentView.spec.ts` |
| UI-HWK-05 | HWK | 学生作业提交页面 | 合并承载 | `/courses/:courseId/homeworks/:homeworkId/submit` — `HomeworkStudentView.vue` | `router.spec.ts` homework-submit 契约；学生提交测试 |
| UI-HWK-06 | HWK | 学生和教师提交历史页面 | 合并承载 | `/submissions` — `HomeworkSubmissionHistoryView.vue`；管理态 `/manage/submissions` — `HomeworkSubmissionWorkspaceView.vue` | `router.spec.ts` history/workspace 契约 |
| UI-HWK-07 | HWK | 自动评测结果页面 | 已实现 | `/result` 与 `/submissions/:submissionId/result` — `HomeworkSubmissionResultView.vue` | `router.spec.ts` latest/historic result 契约；结果组件测试 |
| UI-HWK-08 | HWK | 教师批阅和重评页面 | 合并承载 | `/manage/submissions` — `HomeworkSubmissionWorkspaceView.vue`；`/:submissionId` — `HomeworkSubmissionReviewView.vue` | `router.spec.ts` workspace/review 契约；批阅组件测试 |
| UI-HWK-09 | HWK | 教师作业统计页面 | 已实现 | `/courses/:courseId/homeworks/:homeworkId/manage/statistics` — `HomeworkStatisticsView.vue` | `router.spec.ts` statistics 契约；`HomeworkStatisticsView.spec.ts` |
| UI-GRD-01 | GRD | 教师成绩项配置页面 | 已实现 | `/courses/:courseId/grades/manage/items` — `GradeItemConfigView.vue` | `router.ts` 的 `grade-items-manage`；`GradeItemConfigView.spec.ts` |
| UI-GRD-02 | GRD | 教师成绩总表：筛选、分页、同步和重算 | 合并承载 | `/courses/:courseId/grades/manage/table` — `TeacherGradeTableView.vue` | 成绩总表筛选和分页测试 |
| UI-GRD-03 | GRD | 单名学生成绩构成明细 | 合并承载 | `/courses/:courseId/grades/manage/table` 详情区 — `TeacherGradeTableView.vue` | `TeacherGradeTableView.spec.ts` 学生明细测试 |
| UI-GRD-04 | GRD | 单项或总评成绩调整 | 合并承载 | `/courses/:courseId/grades/manage/table` 调整区 — `TeacherGradeTableView.vue` | 单项和总评调整测试 |
| UI-GRD-05 | GRD | 成绩发布确认和发布记录 | 合并承载 | `/courses/:courseId/grades/manage/table` 发布区 — `TeacherGradeTableView.vue` | 发布与发布记录测试 |
| UI-GRD-06 | GRD | 学生个人已发布成绩页面 | 合并承载 | `/courses/:courseId/grades` — `StudentGradeView.vue` | `StudentGradeView.spec.ts` 已发布成绩测试 |
| UI-GRD-07 | GRD | 教学分析概览 | 合并承载 | `/courses/:courseId/grades/manage/table` 分析区 — `TeacherGradeTableView.vue` | `grade-analysis-panel`；教学分析测试 |
| UI-GRD-08 | GRD | 成绩变更记录 | 合并承载 | `/courses/:courseId/grades/manage/table` 变更记录区 — `TeacherGradeTableView.vue` | `TeacherGradeTableView.spec.ts` 变更记录测试 |
| UI-GRD-09 | GRD | 学生成绩异议申请和本人申请状态 | 合并承载 | `/courses/:courseId/grades` 异议区 — `StudentGradeView.vue` | `StudentGradeView.spec.ts` 异议提交测试 |
| UI-GRD-10 | GRD | 教师成绩复核处理 | 合并承载 | `/courses/:courseId/grades/manage/table` 复核区 — `TeacherGradeTableView.vue` | `TeacherGradeTableView.spec.ts` 复核处理测试 |
| UI-LRN-01 | LRN | 学习任务中心：聚合、筛选和跳转任务 | 已实现 | `/learning/tasks` — `LearningTaskCenterView.vue` | `router.ts` 的 `learning-tasks`；`LearningTaskCenterView.spec.ts` |
| UI-LRN-02 | LRN | 学习进度：课程、章节进度和继续学习 | 已实现 | `/learning/progress` — `LearningProgressView.vue` | `LearningProgressView.spec.ts` 学生进度和教师汇总测试 |
| UI-LRN-03 | LRN | 学习行为仪表盘：近七天趋势和学习指标 | 已实现 | `/learning/statistics` — `LearningStatisticsView.vue` | `LearningStatisticsView.spec.ts` |
| UI-LRN-04 | LRN | 消息通知中心：筛选、已读、删除和业务跳转 | 已实现 | `/notifications` — `NotificationCenterView.vue` | `NotificationCenterView.spec.ts` 通知列表和操作测试 |
| UI-LRN-05 | LRN | 提醒规则和通知偏好设置 | 已实现 | `/learning/reminders` — `ReminderRuleSettingsView.vue` | `ReminderRuleSettingsView.spec.ts` 加载和保存测试 |

## 尚存的可寻址限制

- AUTH 的用户、角色、权限、用户角色和审计五项责任仍集中在 `/admin/auth`；`UI-AUTH-05` 至 `UI-AUTH-09` 没有独立深链或独立分页状态。个人资料/修改密码、403/会话失效也分别复用同一组件。
- CRS 的章节、资源、公告和课程管理是 `/courses` 内的瞬时工作区，刷新、后退和直接链接不能恢复到对应工作区；成员管理嵌在 `/courses/:courseId` 详情中。`meta.uiIds` 只声明真实承载关系，不把这些工作区伪装成独立页面。
- GRD 的教师总表同时承载学生明细、调整、发布、分析、变更记录和复核；学生成绩页同时承载异议。当前功能有真实 API 与测试，但仍不是设计中的独立 URL。
- LAB/HWK 中标为“合并承载”的责任通过学生/教师分支或工作区与详情组件组合完成；后续若拆分路由，必须保持现有深链、课程权限和提交结果契约。

验收时应同时检查本总账、`router.spec.ts` 的 50-ID 集合、各模块行为测试和真实浏览器证据；只通过编号集合测试不能作为页面完整性的单一证明。
