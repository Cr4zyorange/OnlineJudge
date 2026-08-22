# Issue #225 浏览器验收证据

## 环境

- 日期：2026-08-22（Asia/Shanghai）
- 后端：本地 Spring Boot，H2 内存库 `issue225`，演示数据开启，fake sandbox
- 前端：本地 Vite `http://127.0.0.1:5173`
- 浏览器：Playwright Chromium
- 作业：课程 `9501`，作业 `950312`，满分 50 分
- 名单：3 名当前活跃学生；2 人已提交，1 人未提交

## 验收结果

| 证据 | 视口 | 验收点 |
| --- | --- | --- |
| [01-statistics-unsubmitted-1440.png](01-statistics-unsubmitted-1440.png) | 1440px | 统计页固定五档、全部计数、未提交 Tab 与真实姓名 |
| [02-statistics-evaluation-pending-1440.png](02-statistics-evaluation-pending-1440.png) | 1440px | 待评测 Tab 与名单 |
| [03-evaluation-queue-1440.png](03-evaluation-queue-1440.png) | 1440px | `attention=EVALUATION_PENDING` 提交队列深链 |
| [04-review-return-contract-1440.png](04-review-return-contract-1440.png) | 1440px | 批阅页返回队列链接保留原 attention |
| [05-statistics-review-pending-390.png](05-statistics-review-pending-390.png) | 390px | 待批阅 Tab、固定五档和窄屏布局 |
| [06-review-queue-390.png](06-review-queue-390.png) | 390px | `attention=REVIEW_PENDING` 服务端名单 |
| [07-review-pending-390.png](07-review-pending-390.png) | 390px | 待批阅详情、姓名与返回契约 |
| [08-student-forbidden-390.png](08-student-forbidden-390.png) | 390px | 学生直达教师统计页被重定向到 403 |
| [09-name-service-degraded-1440.png](09-name-service-degraded-1440.png) | 1440px | 定向模拟姓名服务 503 后，统计继续可用且不显示裸学生编号 |

名义路径在 1440px 和 390px 下的 `innerWidth`、`documentElement.scrollWidth`、`body.scrollWidth` 分别相等，无横向溢出；控制台为 0 errors / 0 warnings。故障路径通过 Playwright 仅拦截 `GET /api/v1/learning/progress/teacher` 并返回 503，控制台出现 1 条预期的资源加载错误，没有应用脚本错误。

深链刷新保持 `attention`；批阅页按 Tab 后焦点落到“返回提交队列”，按 Enter 返回对应名单；浏览器前进/后退恢复原深链。实际打开一次待评测提交会触发现有评测读取/刷新链，因此统计从 `total=3, submitted=2, unsubmitted=1, pendingEvaluation=1, pendingReview=1, scored=1` 变为 `pendingEvaluation=0, pendingReview=1, scored=2`；两次状态均与真实后端响应一致。

本证据使用 fake sandbox 验证 HWK 页面/API/数据库闭环，不代表真实 Docker 评测沙箱专项或 Compose MySQL 实机迁移验收。

同一最终工作树的自动化结果：后端 `mvn test` 为 283 tests / 0 failures / 0 errors / 1 Docker-only skip；迁移专项 10/10；前端 `npm run test:unit` 为 53 files / 506 tests，`typecheck` 与 189 modules 生产构建通过。Docker daemon socket 不存在，因此真实 MySQL 8.4 首次迁移、重复迁移和 EXPLAIN 留到部署环境复核；当前由 H2 执行测试、MySQL 脚本静态契约与 shell 语法检查覆盖。
