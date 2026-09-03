# 03 浏览器 / 端到端（#320）

## 内容

- `code/tests/e2e/`：FINAL_SHA 的共享 Playwright 场景代码（AUTH/CRS/GRD/HWK/LAB/
  LRN/shared + fixtures + representative-evidence helper），`code/playwright.config.ts`
  为统一配置，`code/tests/contracts/` 为共享入口契约。
- `scripts/`：真实三服务 disposable 运行器（`run-business-e2e-disposable.mjs`、
  `run-business-e2e-three-service.mjs` 及契约测试）。
- `ci/33712921299/`：FINAL_SHA（27eab668）run 33712921299 的
  `ci-browser-e2e-gate-33712921299` 原样解压：Playwright JUnit、test-summary、
  representative 证据 JSON/PNG、compose/环境/迁移证据、cleanup-summary。
- `history/`：两个历史 FAIL 窗口的原样 artifact（见下）。

## 最终计数（AC-TESTS-03）

```text
total=24  passed=24  failed=0  skipped=0
```

场景构成：AUTH 9 / CRS 2 / GRD 1 / HWK 2 / LAB 4 / LRN 4 / shared 2；运行于真实
9 workload / 4 migrations disposable 平台（`oj318-27eab66891bf-3351`，
loopback `http://127.0.0.1:33653`），命令为：

```bash
E2E_ARTIFACT_DIR=ci-artifacts/browser-e2e-gate PLAYWRIGHT_JUNIT_OUTPUT_FILE=… \
  npm run test:e2e:business:disposable
```

代表证据（`ci/33712921299/…/representative-evidence.json`）：

| 组 | proofId | 关联身份 |
| --- | --- | --- |
| AUTH-CRS | `course-2` | 登录 userId=2 → `GET /api/v1/courses/2` 200 → UI 管理入口 |
| ASSESSMENT-WORKER | `task-2b13c708-…` | POST submission 201 → worker terminal SUCCEEDED/ACCEPTED/100 → passive GET 200 |
| GRD-LRN | `notification-12` | publish 200 → `eventId=fae938b7-…` / `correlationId=71d8a32d-…` → 通知投影 200 |

## 历史 FAIL 窗口（保留，不作为交付判定）

- `history/33705748031/`：run 33705748031（#320 head `653116212e`）Browser job 因
  `docker scout … --format` 未知参数失败（code 125），无 Playwright 输出。
  复现/影响/Owner/retest：`INDEX.md` 第 3、4 节；修复为在 Browser job 安装校验
  SHA-256 的 Docker Scout 1.24.0（head `6338d4a3`）。
- `history/33706794044/`：run 33706794044（head `6338d4a3`）Playwright 24/24 全过，
  但 post-E2E 运行时证据按物理行号比较失败（ASSESSMENT-WORKER 需 prove submit →
  worker completion → passive GET）。修复为按 `--timestamps` 解析时间比较（head
  `c382cd56`，离线 replay PASS，用户明确不再整轮重跑）；随后 PR #377 合入 dev。
- FINAL_SHA run 33712921299 的 24/24 是合入后真实 CI 复跑结果，为本目录交付判定。
