# 共享 E2E 入口

本目录是 AUTH、CRS、LRN、LAB、HWK、GRD 共用的唯一 Playwright 端到端测试入口。模块用例只在 `tests/e2e/<module>/` 下增加场景，不新建 runner、配置、报告目录或账号夹具。

## 前置环境

默认验证真实 Compose 应用入口 `http://127.0.0.1:8088`，不启动静态测试页或假 API。在仓库根目录执行：

```bash
docker compose -f deploy/docker/compose.yml up -d --build
cd frontend
npm ci
npm exec playwright install chromium
```

也可使用本地 `Spring Boot + Vite` 真实服务联调：后端监听 `8080`，Vite 监听 `5173` 并代理 `/api`，运行时设置 `E2E_BASE_URL=http://127.0.0.1:5173`。

## 运行与筛选

```bash
# 默认无头执行全部 E2E
npm run test:e2e

# 指定真实应用入口
E2E_BASE_URL=http://127.0.0.1:8088 npm run test:e2e

# 单文件、标签/名称筛选
npm run test:e2e -- tests/e2e/shared/application.smoke.spec.ts
npm run test:e2e -- --grep @smoke
```

GRD 成绩生命周期会创建用户、课程、LAB/HWK、提交、成绩、复核和通知，因此禁止直接指向默认 Compose/MySQL 或任意共享环境运行。只能使用仓库提供的 disposable 包装命令；该命令构建后端、在临时目录启动独立 H2 文件库、运行共享 Playwright spec，并通过 `trap` 在成功、失败或中断时停止后端和删除全部临时数据：

```bash
# Chromium 已由 Playwright 安装时
npm run test:e2e:grd:disposable

# 本机只安装了 Chrome 时
E2E_BROWSER_CHANNEL=chrome npm run test:e2e:grd:disposable
```

直接通过 `npm run test:e2e`、手工设置运行标志或传入任意 `E2E_BASE_URL` 时，变异型 GRD 生命周期用例会跳过。包装脚本会在权限收紧的临时目录中生成一次性随机 token 和 proof 文件；用例同时校验 proof 归属、权限、token、loopback URL 与仍存活的隔离后端 PID，不能只靠一个可继承的环境标志放行。可用 `E2E_GRD_PORT` 覆盖 disposable 后端端口；端口已被服务占用时包装脚本会拒绝启动，不会复用现有应用。

公共运行器契约与“断言失败必须非零退出”可重复验证：

```bash
npm run test:e2e:contract
npm run test:e2e:verify-failure
```

## 公共夹具边界

`tests/e2e/fixtures.ts` 提供：

- 每条用例前后清理 Cookie、`localStorage` 和 `sessionStorage`，不依赖上一条用例的会话。
- `loginAs(role)` 从环境变量或部署文档的公开演示账号约定读取凭据。
- `logout()` 通过真实页面操作退出并等待返回登录页。
- `waitForBusinessState(locator, expected)` 等待页面的可观察业务状态，不用固定 sleep。
- `failureEvidenceName(suffix)` 产生不含账号或凭据的稳定失败证据名称。

可覆盖的账号变量为 `E2E_STUDENT_ACCOUNT/PASSWORD`、`E2E_TEACHER_ACCOUNT/PASSWORD` 和 `E2E_ADMIN_ACCOUNT/PASSWORD`。不得将真实个人账号、Token、Cookie 或本机环境文件提交到仓库。

## 报告与敏感信息

- 人类可读 HTML 报告：`frontend/playwright-report/`。
- 失败截图、trace 和 video：`frontend/test-results/`；仅失败时保留。
- 两个目录均被 Git 忽略，不使用已跟踪的 `output/playwright/` 人工证据目录。
- 查看报告：`npm exec playwright show-report`。
- 失败产物可记录表单输入和网络请求。除公开演示账号外，使用外部敏感凭据时必须设置 `E2E_FAILURE_ARTIFACTS=off`，且用例标题、断言和附件不得包含凭据值。

命令返回非零表示测试失败；报告中应记录完整 SHA、OS/Node/npm/Playwright/浏览器版本、文件数、总数、通过、失败、跳过和耗时。

## 故障排查

- `ERR_CONNECTION_REFUSED`：确认 Compose 三个服务均为运行/健康，或 `E2E_BASE_URL` 与实际端口一致。
- 缺少 Chromium：执行 `npm exec playwright install chromium`，或在已安装 Chrome 的机器上设置 `E2E_BROWSER_CHANNEL=chrome`。
- 健康接口成功但页面失败：检查 Nginx/Vite 前端入口和 SPA fallback。
- 页面成功但健康接口失败：检查 `/api/` 反向代理及后端健康状态。
