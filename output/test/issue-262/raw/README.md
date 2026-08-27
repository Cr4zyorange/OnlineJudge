# #262 原始测试输出

被测提交：`6141f4c13374b14c943f0ff75b7e4c4b18a88ce4`
开发基线：`844029628a616f233597da5842b9905e4941a81f`

| 文件 | 对应验证 |
| --- | --- |
| `backend-target.log` | 19 个 Maven 目标测试类，101/101 PASS |
| `frontend-unit.log` | 13 个 Vitest 文件，119/119 PASS |
| `e2e-contract.log` | 共享 E2E 契约，3/3 PASS |
| `doc-contract.log` | LRN 文档契约，4/4 PASS；15 个 Mermaid 图源均真实渲染 |
| `typecheck.log` | `npm run typecheck` PASS |
| `build.log` | `npm run build` PASS，189 modules |
| `diff-check.log` | `git diff --check origin/dev...<tested-sha>`，exit code 0 |
| `e2e-lrn.log` | 隔离式默认 4-worker LRN Playwright 第一轮，4/4 PASS（Playwright 8.5s；命令 19.5s） |
| `e2e-lrn-repeat.log` | 独立临时环境中的第二轮，4/4 PASS（Playwright 11.4s；命令 22.7s） |
| `backend-service*.log` / `frontend-service*.log` | 历史轮次服务输出；本轮一次性运行器的服务日志随已验证临时目录一并销毁，失败时才打印去敏尾部 |

日志提交前已移除 ANSI 控制字符，并将本机工作区和用户目录替换为占位符。未提交 Token、Cookie、密码、浏览器 trace、video 或本地数据库。
