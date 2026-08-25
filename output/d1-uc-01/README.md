# Issue #242 D1-UC-01 第一轮验收证据（UC-AUTH-01、UC-GR-03）

## 环境

- 日期：2026-08-25（Asia/Shanghai）
- 基线：`origin/dev@3a802574415658df98a5df787a31f2c7590897f7`
- 环境：Windows；JDK 25（编译 `--release 21`）；Maven 3.9.9；Node 22.19.0；H2 文件库（临时）；Spring Boot 8080 + Vite 5173；评测器 `fake`
- 浏览器：Playwright + 系统 Chrome（1440 × 900）；0 console error

## 截图清单

| 文件 | 路由 / 状态 | 验收重点 |
| --- | --- | --- |
| `01-login-1440.png` | `/login` | 统一登录入口 |
| `02-student-login-success-1440.png` | 学生登录成功 | 角色落点链接 `/learning/tasks`（学生工作台） |
| `03-student-tasks-1440.png` | `/learning/tasks` | 学生工作台落点 |
| `04-student-grades-1440.png` | `/courses/9501/grades` | 学生查询本人已发布成绩，总评 89.60、LAB/HWK 明细 |
| `05-student-403-1440.png` | 学生访问 `/admin/auth` | 角色越权进入统一 403 状态页 |
| `06-admin-login-success-1440.png` | 管理员登录成功 | 角色落点链接 `/admin/auth`（管理员工作台） |
| `07-admin-auth-1440.png` | `/admin/auth` | 管理员认证与权限管理页 |
| `08-teacher-login-success-1440.png` | 教师登录成功 | 角色落点链接 `/courses`（教师工作台） |
| `09-teacher-grade-table-1440.png` | `/courses/9501/grades/manage/table` | 教师成绩表：89.60 总评与明细可见 |

## API 断言要点（详见 Issue #242 评论）

- UC-AUTH-01：三类账号登录/me/角色入口；错误密码 401 ERR-AUTH-01；禁用 403 ERR-AUTH-03；会话失效 401 ERR-AUTH-04；登出后 token 失效；5 次失败锁定；学生越权访问管理接口 403 ERR-AUTH-05。
- UC-GR-03：student001 已发布成绩 89.60；未发布/无成绩 400 ERR-GRD-04；部分发布仅已发布学生可见（发布记录 950432）；非成员 403 ERR-GRD-02；未登录 401 ERR-AUTH-04。

## 自动化统计

- 后端全量：341 total = 340 passed + 1 Docker-only skipped，0 failures / 0 errors
- 后端定向（AUTH+GRD）：74/74
- 前端全量：53 files / 546 tests
- 前端定向（auth+grd）：9 files / 47 tests
- `npm run typecheck`、`npm run build` 通过

## 缺陷记录

- D1-2 复现：`POST /api/v1/courses/{courseId}/join` 无请求体且不带 Content-Type 返回 500，应为 400/415；显式 JSON 时 200 ACTIVE。与 #243 记录一致，建议 CRS 模块另建 Issue/PR 修复。
