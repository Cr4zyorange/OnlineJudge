# Issue #233 前端集成回归与部署烟测证据

## 环境

- 日期：2026-08-23（Asia/Shanghai）
- 基线：`origin/dev@1d9130ab247bb0996100cc4c40bdbdfb0717e26e`
- 分支：`test/233-frontend-integration-acceptance`
- 浏览器：Codex 应用内 Browser（Chromium）
- 部署：Docker Compose，MySQL 8.4 + Spring Boot + Nginx；评测器为 compose 默认 fake sandbox
- 演示身份：仓库集成种子中的 `student001`、`teacher001`
- 课程：`9501`（数据结构全流程演示课）

## 浏览器证据

| 文件 | 视口 | 路由 / 状态 | 验收重点 |
| --- | --- | --- | --- |
| [00-login-1440.jpg](00-login-1440.jpg) | 1440 × 900 | `/login` | 统一登录入口、桌面布局 |
| [01-student-tasks-1440.jpg](01-student-tasks-1440.jpg) | 1440 × 900 | `/learning/tasks` | LAB/HWK/资源聚合、筛选和任务状态 |
| [02-student-tasks-1024.jpg](02-student-tasks-1024.jpg) | 1024 × 768 | `/learning/tasks` | 平板断点、双栏信息密度 |
| [03-student-tasks-390.jpg](03-student-tasks-390.jpg) | 390 × 844 | `/learning/tasks` | 移动端单列、主要导航和无横向溢出 |
| [04-student-homework-submit-1440.jpg](04-student-homework-submit-1440.jpg) | 1440 × 900 | `/courses/9501/homeworks/950311/submit` | 文本答案、提交主操作、任务上下文 |
| [05-student-homework-submit-1024.jpg](05-student-homework-submit-1024.jpg) | 1024 × 768 | 同上 | 1024 断点的作答与说明并列布局 |
| [06-student-homework-submit-390.jpg](06-student-homework-submit-390.jpg) | 390 × 844 | 同上 | 移动端固定主操作栏、单列内容、无横向溢出 |
| [07-student-homework-receipt-1440.jpg](07-student-homework-receipt-1440.jpg) | 1440 × 900 | HWK 提交成功 | 提交 `950304`、版本 1、待批阅回执 |
| [08-student-lab-accepted-1440.jpg](08-student-lab-accepted-1440.jpg) | 1440 × 900 | LAB 自动评测成功 | 实验 `950211`、版本 1、100 分、1/1 用例通过 |
| [09-student-grades-1440.jpg](09-student-grades-1440.jpg) | 1440 × 900 | `/courses/9501/grades` | 课程总评 89.6、LAB/HWK 成绩来源与折算分 |

任务列表三视口均实测 `document.documentElement.scrollWidth === window.innerWidth`；作业提交移动端实测 `390 === 390`。每个已加载业务页只有一个 H1，客户端路由完成后 H1 获得焦点。

## 真实学生闭环

1. `student001` 通过真实登录表单进入学习任务中心，读取资源、LAB、HWK 三类任务。
2. 在开放作业 `950311` 提交文本答案，生成提交 `950304`、版本 1，结果页显示“已提交 / 未评测 / 待批阅”。
3. 在开放实验 `950211` 提交 Python 确定性样例，fake sandbox 自动评测为 `ACCEPTED`，自动得分 100，通过用例 1/1。
4. 学生成绩页返回课程总评 89.6；通知中心返回 LAB、HWK、GRD 三模块通知。

## RED → GREEN 阻断修复

真实 GRD 通知使用旧式地址 `/courses/{id}?page=grades&role=student`。修复前，前端仅删除 `role`，点击“查看详情”会落到课程主页而非学生成绩页。

- RED：`NotificationCenterView.spec.ts` 使用真实旧式地址后为 1 failed / 6 passed；实际 href 是 `/courses/101?page=grades`。
- GREEN：`sanitizeInternalActionUrl` 将合法课程旧式成绩地址归一化为 `/courses/{id}/grades`，再删除 `role`；通知测试 7/7，LRN 定向回归 12/12。
- 其他查询参数和 hash 保留；非白名单应用路径仍被拒绝。

## 自动化验证

| 门禁 | 结果 |
| --- | --- |
| `mvn test` | 339 tests，0 failures，0 errors，1 个 Docker-only 用例跳过 |
| Compose / 健康 / 演示数据定向 Maven 测试 | 13/13 通过 |
| `npm --prefix frontend run test:unit` | 53 files / 545 tests 通过 |
| `npm --prefix frontend run typecheck` | 通过 |
| `npm --prefix frontend run build` | 通过，189 modules transformed |
| `./scripts/test/collect-frontend-baseline.test.sh` | PASS |
| `docker compose ... config -q` | 通过 |
| 容器内 `nginx -t` | 通过 |
| `GET /` | HTTP 200，2.223 ms 单次烟测 |
| `GET /api/v1/system/health` | `status=UP` |

Compose 三个服务均为 healthy。真实 API 断言覆盖学生登录/当前用户/课程/任务/LAB/HWK/评测/成绩/通知，以及教师登录/成绩项/成绩表/分析，学生、教师两组断言均为 PASS。

## 接口耗时基线

`scripts/test/collect-frontend-baseline.sh` 在 Compose 环境以 5 个样本采集。所有接口 p95 均低于设计参考线：

- 学生：登录 136.394 ms；当前用户 6.635 ms；课程 15.533 ms；HWK 15.668 ms；LAB 13.173 ms；学习任务 19.545 ms；成绩 10.944 ms；通知 9.323 ms。
- 教师：登录 24.526 ms；成绩项 6.203 ms；成绩表 8.131 ms；成绩分析 14.603 ms。
- 入口 HTML：5/5 为 2xx，p95 2.020 ms。

这些数据是本机单用户 smoke，不是 FAT/UAT 或负载测试结论。

## 资源预算

| 预算项 | 实测 | 阈值 | 结果 |
| --- | ---: | ---: | --- |
| 主入口 JS gzip | 45.46 KiB | 250 KiB | 通过 |
| 全局 CSS gzip | 5.76 KiB | 80 KiB | 通过 |
| 最大异步路由 chunk gzip | 14.63 KiB | 200 KiB | 通过 |
| 最大视觉资源 | 1.205 MiB | 1.5 MiB | 通过 |
| 本地首屏 HTML + JS + CSS + 背景图 | 约 1.29 MiB | 2 MiB | 通过（见边界） |
| 完整 `dist` 未压缩 | 2,098,149 B | 信息项 | — |

当前编辑器为原生文本域，没有单独编辑器核心 / 语言包 chunk；工作页首屏无视频。

## 未阻断项与当前阻塞

- [Issue #234](https://github.com/Cr4zyorange/OnlineJudge/issues/234)：`npm audit --omit=dev` 的 Vite、PostCSS、nanoid 共 3 个 high，进入项目 Todo；本轮未执行强制自动升级。
- [Issue #235](https://github.com/Cr4zyorange/OnlineJudge/issues/235)：Google Fonts 与 Bootstrap Icons 仍由外部 CDN 加载，未计入上述本地首屏估算；需本地化后形成完整离线 / HAR 预算。
- 当前唯一阻塞：应用内 Browser 在教师登录成功后拒绝工作台导航，并明确禁止用其他浏览器表面绕过。教师端 1440 / 1024 / 390 截图和键盘焦点证据必须在获得用户授权切换 Playwright CLI 后补齐；在此之前 Issue #233 与 Notion Action 保持进行中。
