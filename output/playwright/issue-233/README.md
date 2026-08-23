# Issue #233 前端集成回归与部署烟测证据

## 环境

- 日期：2026-08-23（Asia/Shanghai）
- 基线：`origin/dev@1d9130ab247bb0996100cc4c40bdbdfb0717e26e`
- 分支：`test/233-frontend-integration-acceptance`
- 浏览器：Chromium；`00`～`09` 由 Codex Browser 采集，`10`～`16` 与键盘证据由用户授权后的 Playwright CLI 采集
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
| [10-teacher-login-1440.png](10-teacher-login-1440.png) | 1440 × 900 | `/login` | 教师真实登录入口与未填充凭据状态 |
| [11-teacher-lab-queue-1440.png](11-teacher-lab-queue-1440.png) | 1440 × 900 | `/courses/9501/labs/950211/manage/submissions` | 教师提交摘要、筛选、真实提交队列与键盘焦点环 |
| [12-teacher-lab-queue-1024.png](12-teacher-lab-queue-1024.png) | 1024 × 900 | 同上 | 教师工作台平板断点、双列摘要与单列筛选 |
| [13-teacher-lab-queue-390.png](13-teacher-lab-queue-390.png) | 390 × 844 | 同上 | 移动端完整“通知/退出”标签、单列工作流、无横向溢出 |
| [14-teacher-grade-table-1440.png](14-teacher-grade-table-1440.png) | 1440 × 900 | `/courses/9501/grades/manage/table` | 成绩筛选、教学分析、89.6 总评、成绩表与焦点环 |
| [15-student-grade-notification-1440.png](15-student-grade-notification-1440.png) | 1440 × 900 | `/notifications?type=GRADE` | 真实成绩通知、归一化详情地址与键盘焦点环 |
| [16-student-grade-detail-1440.png](16-student-grade-detail-1440.png) | 1440 × 900 | `/courses/9501/grades` | 从通知按 Enter 到学生成绩页，H1 聚焦且总评 89.6 |

任务列表与教师提交队列三视口均实测 `document.documentElement.scrollWidth === window.innerWidth`；作业提交移动端实测 `390 === 390`。每个已加载业务页只有一个 H1，客户端路由完成后 H1 获得焦点。教师提交队列的 9 个快捷入口/筛选控件及提交卡、成绩管理的 9 个首屏控件、成绩通知详情链接均由键盘获得 `3px solid rgb(43, 122, 112)`、offset `2px` 的 `:focus-visible` 焦点环。

## 真实学生闭环

1. `student001` 通过真实登录表单进入学习任务中心，读取资源、LAB、HWK 三类任务。
2. 在开放作业 `950311` 提交文本答案，生成提交 `950304`、版本 1，结果页显示“已提交 / 未评测 / 待批阅”。
3. 在开放实验 `950211` 提交 Python 确定性样例，fake sandbox 自动评测为 `ACCEPTED`，自动得分 100，通过用例 1/1。
4. 学生成绩页返回课程总评 89.6；通知中心返回 LAB、HWK、GRD 三模块通知。

## 真实教师闭环

1. `teacher001` 通过真实登录表单进入 `/courses`；`/courses` 与 `/courses/` 均由 Nginx 返回 200，不再发生丢失映射端口的目录重定向。
2. 教师提交队列读取实验 `950211` 的真实提交，显示学生、版本、评测通过、自动分 100 和待教师评分状态。
3. 教师成绩管理页读取课程总表与教学分析；均分、最高分、最低分均为 89.6，完成率与及格率均为 100%。
4. 学生切回通知中心后，以键盘聚焦“课程成绩已发布”的详情链接并按 Enter；最终 URL 精确为 `/courses/9501/grades`，无遗留 `page` / `role` 参数，H1 为“我的成绩”。
5. 当前 Playwright 会话记录 0 条 console warning、0 条 console error；真实 API 请求均返回 200。

## RED → GREEN 阻断修复

真实 GRD 通知使用旧式地址 `/courses/{id}?page=grades&role=student`。修复前，前端仅删除 `role`，点击“查看详情”会落到课程主页而非学生成绩页。

- RED：`NotificationCenterView.spec.ts` 使用真实旧式地址后为 1 failed / 6 passed；实际 href 是 `/courses/101?page=grades`。
- GREEN：`sanitizeInternalActionUrl` 将合法课程旧式成绩地址归一化为 `/courses/{id}/grades`，再删除 `role`；通知测试 7/7，LRN 定向回归 12/12。
- 其他查询参数和 hash 保留；非白名单应用路径仍被拒绝。

Compose 生产入口还暴露出两个验收缺口，并在同一 Issue 内按 TDD 收口：

- SPA 深链：旧 Nginx `try_files $uri $uri/ /index.html` 会把真实存在的 `dist/courses/` 当目录，将 `/courses` 301 到丢失 `8088` 的地址。RED 为新增部署契约 1/1 失败；GREEN 改为 `try_files $uri /index.html`，部署契约 8/8、扩展定向 14/14 通过。镜像重建后 `/courses`、`/courses/` 均为 200 且无 `Location`。
- 390 顶栏：紧凑导航原本主动显示单字“知/退”，不满足移动端可辨识度。RED 为组件测试实际 `知`、期望 `通知`；GREEN 改为完整“通知/退出”，组件测试 2/2，390px 实测无横向溢出。
- 测试时钟：HWK FILE 附件夹具在墙钟越过固定 `2026-08-23 10:00 +08:00` 后触发 13 个过期连锁失败。保留专门的 100 ms 过期测试，普通夹具改为相对当前测试时钟 1 小时后过期；定向 46/46、全量 546/546 通过，不再依赖执行日期。

## 自动化验证

| 门禁 | 结果 |
| --- | --- |
| `mvn test` | 340 tests，0 failures，0 errors，1 个 Docker-only 用例跳过 |
| Compose / 健康 / 演示数据定向 Maven 测试 | 14/14 通过 |
| `npm --prefix frontend run test:unit` | 53 files / 546 tests 通过 |
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

## 未阻断项与 Backlog

- [Issue #234](https://github.com/Cr4zyorange/OnlineJudge/issues/234)：`npm audit --omit=dev` 的 Vite、PostCSS、nanoid 共 3 个 high，进入项目 Todo；本轮未执行强制自动升级。
- [Issue #235](https://github.com/Cr4zyorange/OnlineJudge/issues/235)：Google Fonts 与 Bootstrap Icons 仍由外部 CDN 加载，未计入上述本地首屏估算；需本地化后形成完整离线 / HAR 预算。
- [Issue #237](https://github.com/Cr4zyorange/OnlineJudge/issues/237)：通知卡同页多个“查看详情/删除”仍缺少标题上下文，已进入项目 Todo（P2/S）；本轮已验证键盘顺序、焦点环与详情路由，不把后续语义增强混入验收修复。
- Issue #233 当前无活动阻塞。上述三项均有独立边界，不影响学生/教师主闭环、生产构建或 Compose 烟测结论。
