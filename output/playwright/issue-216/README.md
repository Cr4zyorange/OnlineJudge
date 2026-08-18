# Issue #216 LAB 学生端闭环验收证据

## 验收环境

- 基线：`origin/dev@2e8a43e285469bc2e8ab63504f2c6c1f3e302d21`
- 分支：`feature/216-lab-student-flow`
- 浏览器：Playwright Chromium，1440 x 900 与 390 x 844
- 后端：本地 Spring Boot + 内存 H2 + fake sandbox
- 账号：仓库公开演示学生 `student001`
- 验收数据：仅存在于本次 H2 会话，未写入种子数据或生产数据库

## 重构前共同基线

重构前 LAB 学生端是一个单页工作台，列表、详情、提交、结果和历史没有独立路由，因此以同一组旧页面截图作为拆分后五个页面的共同 before 基线：

- [重构前 1440](../../../docs/开发/assets/frontend-refactor-baseline/2026-08-14/01-lab-student-1440.png)
- [重构前 390](../../../docs/开发/assets/frontend-refactor-baseline/2026-08-14/02-lab-student-390.png)

## 重构后截图

| 页面 / 状态 | 1440 | 390 |
| --- | --- | --- |
| 实验列表 | [01-list-after-1440.png](01-list-after-1440.png) | [07-list-after-390.png](07-list-after-390.png) |
| 实验详情 | [02-detail-after-1440.png](02-detail-after-1440.png) | [08-detail-after-390.png](08-detail-after-390.png) |
| 源文件提交 + 报告上传 | [03-submit-after-1440.png](03-submit-after-1440.png) | [09-submit-after-390.png](09-submit-after-390.png) |
| 成绩发布前结果 | [04-result-unpublished-after-1440.png](04-result-unpublished-after-1440.png) | 参见发布后同页移动端证据 |
| 两版提交历史 | [05-history-after-1440.png](05-history-after-1440.png) | [11-history-after-390.png](11-history-after-390.png) |
| 成绩发布后结果 | [06-result-published-after-1440.png](06-result-published-after-1440.png) | [10-result-published-after-390.png](10-result-published-after-390.png) |
| 成绩发布后不可再提交 | 参见发布后详情 / 结果 | [09-submit-blocked-after-390.png](09-submit-blocked-after-390.png) |

## 真实业务链路

1. 学生从课程实验列表进入详情，仅看到 1 个公开用例；隐藏用例未出现在学生 API 或页面。
2. 先用在线 Python 代码提交第 1 版，再用真实 `acceptance.py` 提交第 2 版。Chromium 上传的 `text/x-python-script` MIME 已被后端正常接受。
3. 两个版本均通过 fake sandbox 评测；聚合结果保留全量 `2 / 2` 统计，学生页面只显示 1 个公开用例明细。
4. 使用真实 `lab-report.pdf` 上传报告并绑定当前提交；页面显示报告版本与受控下载操作。
5. 发布前最终得分、报告评分和教师评语不可见；发布后显示最终得分 96、报告评分 20 和两类教师评语。
6. 历史页保留两版提交，明确标记最新版本、当前有效版本与评分依据。

## 响应式、键盘与控制台

- 列表、详情、提交、结果、历史五条 390 路由均实测 `documentElement.scrollWidth === window.innerWidth === 390`。
- 列表键盘顺序实测为：搜索框 -> 状态筛选 -> 第 1/2/3 个实验主操作，均是可访问原生控件。
- 不可提交时主操作使用真正的 disabled button，不会被键盘激活；当前流程步骤使用 `aria-current="step"`。
- 最终浏览器会话：0 console errors，0 console warnings。

## 自动验证

- 前端 LAB 定向：8 个文件，142 项测试通过。
- 前端全量：42 个文件，357 项测试通过。
- `npm run typecheck`：通过。
- `npm run build`：157 个模块构建通过。
- 后端 LAB/CRS 相关：6 个测试类，48 项 Controller、Service、迁移与集成测试通过。
- `git diff --check`：通过。

## 显式拆分的后续契约

UI-LAB-02 的实验说明附件仍只有 `attachmentIds`，无法在不泄露存储 ID 的前提下展示元数据与真实下载链接。本次未伪造接口，已拆分为 [Issue #217](https://github.com/Cr4zyorange/OnlineJudge/issues/217)，复用 CRS 资源元数据/受控下载完成该跨模块契约。
