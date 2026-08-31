# D6-DATA 四域数据所有权与账号隔离（#306）

| Owner | Schema | 运行账号 | 负责数据 |
| --- | --- | --- |
| IDENTITY | `oj_identity` | `oj_identity_rw` | 账号、会话、权限、安全版本 |
| COURSE | `oj_course` | `oj_course_rw` | CRS 与 LRN：课程、成员、资源、学习记录、任务、提醒、通知 |
| ASSESSMENT | `oj_assessment` | `oj_assessment_rw` | LAB/HWK、提交、评测和来源成绩 |
| GRADE | `oj_grade` | `oj_grade_rw` | 成绩投影、计算、发布、复核和分析 |

每个运行账号只允许对本 schema `SELECT/INSERT/UPDATE/DELETE`。其他三个 schema、任意 DDL、GRANT、跨 schema view/join/FK 一律拒绝。引用外域事实只保存逻辑 ID，并通过 `contracts/v2` OpenAPI/AsyncAPI 或 Identity JWT/JWKS 同步；不共享 Repository、Entity、Mapper 或数据库连接。

Course 吸收所有 LRN 表与投影，`oj_learning` 和独立 Learning 账号不属于当前拓扑。`database/ownership/*.csv`、四个迁移目录和 `deploy/platform/workloads.json` 是可执行清单；任何名称/账号/owner 漂移都由 #306 契约测试拒绝。
