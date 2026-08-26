# #262 原始测试输出

被测提交：`8b69a7241db6bdf585db532522abec041a3e4160`
开发基线：`50a5dccd35ddc6b0c8936df20217575f18303a4f`

| 文件 | 对应验证 |
| --- | --- |
| `backend-target.log` | 19 个 Maven 目标测试类，101/101 PASS |
| `frontend-unit.log` | 13 个 Vitest 文件，119/119 PASS |
| `e2e-contract.log` | 共享 E2E 契约，3/3 PASS |
| `doc-contract.log` | LRN 文档契约，4/4 PASS |
| `typecheck.log` | `npm run typecheck` PASS |
| `build.log` | `npm run build` PASS，189 modules |
| `e2e-lrn.log` | 默认 4-worker LRN Playwright 第一轮，4/4 PASS（17.9s） |
| `e2e-lrn-repeat.log` | 同一命令第二轮，4/4 PASS（19.2s） |
| `backend-service*.log` / `frontend-service*.log` | 两轮 E2E 对应的本地服务输出 |

日志提交前已移除 ANSI 控制字符，并将本机工作区和用户目录替换为占位符。未提交 Token、Cookie、密码、浏览器 trace、video 或本地数据库。
