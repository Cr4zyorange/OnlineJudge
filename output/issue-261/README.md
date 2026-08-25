# Issue #261 D2-AUTH 文档与测试闭环证据

本目录归档 Issue #261（[D2-AUTH] 补齐 AUTH 业务场景文档与测试闭环）的可复现测试证据与页面证据，只记录真实执行结果。

## 环境与基线

| 项目 | 内容 |
| --- | --- |
| 基线 | `origin/dev` merge commit `758afd98ba2caad5a00fb6e12413c48f0156b2fb`（含共享 E2E PR #268） |
| 分支 | `test/261-auth-doc-test-closure`（#261）；`fix/271-auth-status-navigation`（#271 修复，PR #274） |
| 操作系统 | Windows 11 家庭版（10.0.26200） |
| 后端 | JDK 25；Maven 3.9.9；Spring Boot 3.4.5（H2 内存库） |
| 前端 | Node v22.19.0；npm 10.9.3；Vue 3.5 / Vite 6.3 / Vitest 3.2.4；Playwright 1.62.1；Chrome（`E2E_BROWSER_CHANNEL=chrome`） |
| 应用入口 | 本地真实服务 Spring Boot :8080 + Vite :5173（`/api` 代理），`E2E_BASE_URL=http://127.0.0.1:5173` |

## 测试原始输出

| 文件 | 内容 | 结果 |
| --- | --- | --- |
| `backend-auth-tests.log` | 后端 AUTH 目标测试（5 类 36 条） | PASS 36 / FAIL 0 / ERROR 0 / SKIP 0 |
| `frontend-unit-tests.log` | 前端 AUTH/API + App 根组件（6 文件 34 条） | PASS 34 / FAIL 0 |
| `typecheck.log` | `vue-tsc --noEmit` | PASS |
| `build.log` | `vite build` | PASS |
| `e2e-contract.log` | 共享 E2E 契约（`node --test`） | PASS 3/3 |
| `e2e-auth.log` | 完整 E2E 套件（共享 smoke 2 + AUTH 9，共 11 条） | 最终复测结果，见下方说明 |

## 页面证据（`screenshots/`）

| 文件 | 场景 |
| --- | --- |
| `01-student-login-landing.png` | 学生登录成功并进入学习任务中心 |
| `02-teacher-login-landing.png` | 教师登录成功并进入课程中心 |
| `03-admin-login-landing.png` | 管理员登录成功并进入认证与权限管理 |
| `04-wrong-credentials.png` | 错误凭据登录失败提示（账号或密码错误） |
| `05-disabled-account-redirect.png` | 禁用账号登录被拒，URL 与视图一致跳转 `/account-disabled`（#271 修复后） |
| `06-locked-account-redirect.png` | 连续失败锁定账号登录被拒，跳转 `/account-disabled`（#271 修复后） |
| `07-session-expired-redirect.png` | 无效会话访问受保护页面跳转登录失效页 |
| `08-forbidden-redirect.png` | 学生越权访问管理员页面跳转无权限页 |

## 说明

- 禁用/锁定两条 E2E（AUTH-E2E-05/06）原实现存在手工 `page.goto("/account-disabled")` 绕过，终审打回；已删除绕过并直接断言登录提交后的 URL 与状态页视图。该断言依赖 #271 修复（PR #274）合入 `dev` 后复测，最终 `e2e-auth.log` 在合入后于最新 `dev` 上重新生成。
- `frontend/scripts/verify-e2e-failure`（共享框架自检）在本机 Windows 上因绝对路径参数报 `No tests found`，已手工等效验证“故意断言失败 → `1 failed` + 非零退出码”通过（DEF-001）。
- Compose :8088 入口因本机 Docker Hub 证书校验失败暂不可用（DEF-002），E2E 按共享 E2E 文档的本地真实服务方案执行。
