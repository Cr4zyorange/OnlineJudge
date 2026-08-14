# 2026-08-14 前端重构前基线

本记录对应 GitHub issue #209。它固定了页面实现前的功能、视觉和本机性能证据；不是生产性能、并发容量或真实 Docker 沙箱验收结论。

## 采集环境

| 项目 | 值 |
| --- | --- |
| 基线提交 | `0a82e27558f67fbdb450f39d04af0d960fe2d376` |
| 采集时间 | 2026-08-14 17:07 CST |
| 系统 | macOS Darwin 25.5.0, arm64 |
| Java / Maven | Java 25 / Maven 3.9.11 |
| Node.js / npm | Node.js 25.8.2 / npm 11.11.1 |
| 后端 | Spring Boot, 干净 H2 内存库, `onlinejudge.demo-data.enabled=true` |
| 评测 | `fake` sandbox；Docker CLI 存在，但 daemon 不可用 |
| 前端 | Vite 开发服务 `127.0.0.1:5173`；生产资源重新构建 |
| 视口 | 桌面 `1440 x 900`；移动 `390 x 844` |

Java 25 是本机实际执行环境；项目约定的交付基线仍为 Java 21。后续 UAT 需在 Java 21 再复验。

## 可重复演示数据

初始化器保留已有完成样例，用于成绩、历史和通知回归；另增开放样例用于重复提交。固定 ID 避免重复插入，时间窗口在每次启动时滚动刷新。

| 对象 | ID | 状态 / 时间窗口 | 用途 |
| --- | ---: | --- | --- |
| 课程 | `9501` | `today - 7` 至 `today + 90`，`ACTIVE` | 统一演示入口 |
| 开放 LAB | `950211` | `PUBLISHED`，截止 `today + 30` | 学生提交、评测、教师查看 |
| LAB 测试用例 | `950212` | 公开 | 输入 `0`，期望 `EMPTY` |
| 开放 HWK | `950311` | `PUBLISHED`，截止 `today + 30` | 学生文本提交、教师查看 |
| HWK 题目 | `950312` | 文本题 | 复杂度说明 |
| 学习任务 | `950611` / `950612` | `IN_PROGRESS`，截止 `today + 30` | 任务中心回归 |
| 历史 LAB / HWK | `950201` / `950301` | 保持 `SCORE_PUBLISHED` | 成绩、评阅、通知回归 |

重复运行初始化器后，上述开放对象数量不增加，历史完成样例状态不改写。

## RED → GREEN

1. RED：新增 `rollingDemoWindowKeepsCourseAndOpenTasksUsableWithoutChangingCompletedExamples` 后，课程结束日仍为 `2026-07-01`，未达到当日要求的 `2026-10-13`，测试按预期失败。
2. GREEN：课程窗口改为 `today - 7` 至 `today + 90`，开放 LAB/HWK 截止改为 `today + 30`，同时保留完成样例；目标测试通过。
3. 回归：初始化器重复执行不会复制开放课程数据，学生 API 可读到两项开放任务。

## 自动化与实机流程结果

| 验证 | 结果 |
| --- | --- |
| `mvn -q -Dtest=IntDemoDataInitializerTest test` | 4 通过，0 失败 |
| `mvn -q test` | 263 通过，0 失败，0 error，1 skipped |
| `npm run test:unit` | 33 个文件、190 项测试全部通过 |
| `npm run typecheck` | 通过 |
| `npm run build` | 通过 |
| `collect-frontend-baseline.test.sh` | 离线桩测通过，输出未泄漏口令或 token |
| 学生 LAB 失败评测 | 提交 HTTP 201，状态 `WRONG_ANSWER` |
| 学生 LAB 再提交 | 提交 HTTP 201，状态 `ACCEPTED` |
| 学生 HWK 提交 | HTTP 201 |
| 教师读取开放 LAB / HWK 提交 | HTTP 200；分别读到 2 / 1 条 |
| 学生访问教师成绩总表 | HTTP 403，`ERR-AUTH-05` |

实机写操作运行在本次进程的 H2 内存库中，服务退出后自动丢弃，不污染仓库数据。

## 页面证据与分级

截图使用同一浏览器会话、同一演示数据和同一背景。它们记录重构前现状，不代表目标设计。

| 页面 | 桌面 | 移动 | 功能健康度 | 主分类 | 主要观察 |
| --- | --- | --- | --- | --- | --- |
| LAB 学生详情 | [1440](./01-lab-student-1440.png) | [390](./02-lab-student-390.png) | 健康 | 表单过长 | 信息与提交表单纵向堆叠，首屏看不到提交动作 |
| HWK 学生提交 | [1440](./03-hwk-student-1440.png) | [390](./04-hwk-student-390.png) | 健康 | 表单过长 | 元数据、题目、答案和附件串成单列，移动端操作位于长滚动末端 |
| LAB 教师工作台 | [1440](./05-lab-teacher-1440.png) | [390](./06-lab-teacher-390.png) | 部分可用 | 表单过长、数据展示粗糙 | 创建表单先于列表；移动端表格操作列被横向裁切 |
| HWK 教师工作台 | [1440](./07-hwk-teacher-1440.png) | [390](./08-hwk-teacher-390.png) | 部分可用 | 表单过长、数据展示粗糙 | 大型创建表单压过发布管理；现有样例无 `DRAFT`，不能进入已填充编辑态 |
| CRS 课程管理 | [1440](./09-crs-manage-1440.png) | [390](./10-crs-manage-390.png) | 健康 | 层级混乱、表单过长 | 移动端完整侧栏占据首屏，管理主区需继续下滚 |
| GRD 教师总表 | [1440](./11-grd-teacher-1440.png) | [390](./12-grd-teacher-390.png) | 部分可用 | 数据展示粗糙、状态缺失 | 移动表格右侧列不可同时可见；课程导航“成绩分析”实际进入成绩项配置，总表只能直达 URL |

教师课程内导航当前链接到 `/courses/9501/grd/grade-items?role=teacher`，而本页总表实际位于 `/courses/9501/grades?role=teacher`。这是一条已复现的断链基线，不在本 issue 内改 UI。

## 本机单用户性能

采集脚本对每项执行 5 次请求。p95 采用 nearest-rank；表中参考线只用于本地 smoke 对照。

| 角色 | 接口 | p95 | 参考线 |
| --- | --- | ---: | ---: |
| 学生 | 登录 | 163.179 ms | 3000 ms |
| 学生 | 课程详情 | 2.491 ms | 2000 ms |
| 学生 | LAB 列表 | 2.142 ms | 3000 ms |
| 学生 | HWK 列表 | 2.573 ms | 3000 ms |
| 学生 | 学习任务 | 3.040 ms | 1500 ms |
| 学生 | 通知 | 2.412 ms | 1000 ms |
| 学生 | 本人成绩 | 2.340 ms | 3000 ms |
| 教师 | 登录 | 167.353 ms | 3000 ms |
| 教师 | 成绩总表 | 2.177 ms | 5000 ms |
| 教师 | 成绩分析 | 2.563 ms | 5000 ms |

60 条 API 样本全部为 2xx。当前结果仅说明本机单用户 smoke 未超过设计参考线，不能外推到 50 在线、20 并发或生产网络环境。

前端口径必须分开：

- 运行入口 HTML：5/5 为 2xx，p95 `1.323 ms`，最大响应 `479 B`；这里只是 HTML。
- 生产 `index.html` 直接引用的本地未压缩资源：`401,378 B`。
- 完整生产 `dist` 未压缩体积：`101,559,797 B`；它不是首屏传输量。
- 最大单项为 `live-back3-*.mp4`：`82,261,673 B`，是后续资源治理的首要对象。
- 主 JS `310,011 B`（gzip `90,014 B`），主 CSS `90,711 B`（gzip `14,967 B`）。

真实浏览器首屏传输量仍需 Network/HAR；fake sandbox 只证明状态流转，不能作为真实 Docker 评测 `<= 60s` 的性能证据。

## 复现

```bash
cd backend
mvn -q -Dtest=IntDemoDataInitializerTest test
mvn -q test

cd ../frontend
npm run test:unit
npm run typecheck
npm run build

cd ..
./scripts/test/collect-frontend-baseline.test.sh
ONLINEJUDGE_EVALUATION_SANDBOX_MODE=fake \
  SKIP_FRONTEND_BUILD=1 \
  SAMPLES=5 \
  ./scripts/test/collect-frontend-baseline.sh
```

采集结果默认写入已忽略的 `test-results/frontend-baseline/<timestamp>/`。需要保留原始 TSV 时，应作为 CI / 验收附件保存，不应把 token 或登录响应加入仓库。

## 残余风险

- 前端仍没有 Playwright/Cypress 端到端测试入口；本轮使用真实浏览器截图、API smoke 与现有 Vitest 组合替代。
- Docker daemon 未运行，真实容器编译、运行、资源限制和 60 秒评测门槛未复核。
- `npm ci` 报告 5 项依赖漏洞（4 high、1 critical）；本 issue 不升级依赖，需单独评估。
- HWK 演示数据没有固定草稿，教师工作台只能记录空白创建表单和已发布列表，不能记录已填充编辑态。
- 移动端教师 LAB/HWK/GRD 表格存在横向裁切；这是后续视觉原型和页面迁移的 P0 输入。
