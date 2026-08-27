# #262 原始测试输出

被测提交：`d66b7debaddde20fee2d097c7b8a6096ddd35d6d`
开发基线：`50b3b397941aa6879b010a03951d1d19c5a57250`

| 文件 | 对应验证 |
| --- | --- |
| `backend-target.log` | 19 个 Maven 目标测试类，101/101 PASS |
| `npm-ci.log` | 干净 `npm ci` 安装 300 个包后完成审计 |
| `frontend-unit.log` | 全量 Vitest：54 个文件，566/566 PASS |
| `e2e-contract.log` | 共享 E2E 契约，3/3 PASS |
| `doc-contract.log` | LRN 文档契约，4/4 PASS；15 个 Mermaid 图源均真实渲染 |
| `typecheck.log` | `npm run typecheck` PASS |
| `build.log` | `npm run build` PASS，190 modules |
| `diff-check.log` | `git diff --check origin/dev...<tested-sha>`，exit code 0 |
| `e2e-lrn.log` | 隔离式默认 4-worker LRN Playwright 第一轮，4/4 PASS（Playwright 9.4s） |
| `e2e-lrn-repeat.log` | 独立临时环境中的第二轮，4/4 PASS（Playwright 7.3s） |
| `e2e-lrn-nfr.log` | #295 一次性 NFR-LN-01/02 浏览器验收，1/1 PASS（Playwright 10.0s） |
| `backend-service*.log` / `frontend-service*.log` | 历史轮次服务输出；本轮一次性运行器的服务日志随已验证临时目录一并销毁，失败时才打印去敏尾部 |

日志提交前已移除 ANSI 控制字符，并将本机工作区和用户目录替换为占位符。未提交 Token、Cookie、密码、浏览器 trace、video 或本地数据库。
