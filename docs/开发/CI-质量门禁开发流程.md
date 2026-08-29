# CI 质量门禁开发流程（Issue #290）

本文件定义仓库 GitHub-hosted Actions 质量门禁的实现、边界与本地验收方式。任何修改 `.github/workflows/`、`scripts/ci/` 或本文件的行为，都必须先阅读并遵守本文件与 `AGENTS.md`。

## 1. 目标与边界

目标：PR 与 `dev` push 自动完成取代码、依赖安装、编译、单元测试、集成测试、跨模块契约验证与真实浏览器业务 E2E；任何前置门禁失败都会阻断后续镜像/部署阶段，并保留可审计证据。

范围：

- `.github/workflows/ci.yml`：PR 校验与 `dev` push 流水线入口。
- `scripts/ci/*.sh`：本地/CI 共用的门禁正本脚本与验收脚本。
- 版本固定：Java 21、Node 22、npm 10.9.2、Maven 3.9.x，启用可控缓存。
- 证据保留：测试报告、关键日志、环境与精确 SHA 清单在成功与失败时均上传。

不包含：

- 完整 Kubernetes 清单与镜像/部署实现（后续子任务以 `needs: [delivery]` 串联）。
- 在仓库保存 Actions secrets（只声明名称与注入位置，见第 7 节）。
- 弱化测试断言、跳过测试或伪造 PASS。

## 2. 触发与并发

```yaml
on:
  pull_request:
    branches: [dev]
    types: [opened, synchronize, reopened, ready_for_review]
  push:
    branches: [dev]
```

`concurrency` 以 `github.workflow + github.event_name + github.ref` 为组，`cancel-in-progress: true`：同 ref 的新提交会取消旧运行，避免旧提交的 PASS 覆盖新提交状态。

## 3. 作业链与依赖

```text
validate-workflows（无前置，静态校验 + dry-run 链路模拟）
        │
        ├── backend-gate    compile + 单元测试 + 集成测试
        ├── frontend-gate   npm ci + typecheck + 单元测试 + build + 运行器契约
        ├── contracts-gate  shell 契约 + 公共基础设施契约 + D7 workload manifest
        │
        ▼
browser-e2e-gate（依赖三个质量门禁；一次性 H2 + Spring Boot + Vite + Chromium）
        │
        │
        ▼
      delivery（needs 全部五个门禁；后续镜像/Kind 部署 job 以 needs: [delivery] 挂接）
```

规则：

- 每个 job 有显式 `timeout-minutes`。
- 门禁 job 只能调用 `scripts/ci/` 正本脚本，不允许内联构建命令。
- 不使用 `continue-on-error`；前置失败时依赖它的 job 自动 `skipped`。
- `if: always()` 只允许出现在证据/诊断步骤（上传、汇总、收集环境）；`delivery` 步骤一律禁用。

## 4. 版本固定与缓存

| 工具 | 固定版本 | 注入方式 | 缓存 |
| --- | --- | --- | --- |
| Java | 21（Temurin） | `actions/setup-java` | Maven 缓存，key 基于 `backend/pom.xml` |
| Node | 22 | `actions/setup-node` | npm 缓存，key 基于 `frontend/package-lock.json` |
| npm | 10.9.2 | `frontend-verify.sh` 版本断言 + 全局安装兜底 | 同上 |
| Maven | 3.9.x | `backend-verify.sh` 版本断言 | 同上 |

`frontend/package.json` 声明 `engines`（Node `>=22 <25`、npm `>=10 <11`）与 `packageManager: npm@10.9.2`，作为可复现性记录；CI 由脚本做严格版本断言。

## 5. 证据保留

每个门禁 job 在 `ci-artifacts/<job>/` 下保留：

- `environment.json`：event、repository、workflow、run id、ref、base_ref、精确 head SHA、runner OS/arch、Java/Maven/Node/npm 版本、UTC 时间戳。
- `gate.log`：正本脚本完整输出（含每个命令回显）；`contracts-gate/consumer/gate.log` 同时保留 D7 workload manifest 的 schema/语义校验和 Python mutation regression 原始输出。
- surefire/vitest/Playwright JUnit XML 报告（按单元/集成/契约/E2E 分目录）、Playwright HTML 报告与 `test-summary.txt`（tests/failures/errors/skipped 计数）。
- `check-result.txt`（validate-workflows）与 `checkpoint.txt`（delivery）。

上传步骤使用 `if: always()` 与 `if-no-files-found: warn`，保证失败运行同样可审计；`delivery` 只在全部门禁通过后运行并上传证据。

## 6. 权限与第三方 Action 固定

- workflow 级 `permissions: contents: read`，不使用 `write-all`。
- 第三方 Action 固定到受控 SHA 并带版本注释（`check-workflows.sh` 内维护白名单）：

| Action | 版本 | SHA |
| --- | --- | --- |
| `actions/checkout` | v4.2.2 | `11bd71901bbe5b1630ceea73d27597364c9af683` |
| `actions/setup-java` | v4.5.0 | `8df1039502a15bceb9433410b1a100fbe190c53b` |
| `actions/setup-node` | v4.4.0 | `49933ea5288caeca8642d1e84afbd3f7d6820020` |
| `actions/upload-artifact` | v4.6.2 | `ea165f8d65b6e75b540449e92b4886f43607fa02` |

升级 Action 时必须同时更新 `ci.yml` 与 `check-workflows.sh` 的白名单，并跑通验收脚本；`.github/dependabot.yml` 会以月频提出升级 PR，合并前必须完成同样验证。

## 7. Secrets 声明

仓库不保存 Actions secrets。当前质量门禁不需要任何 secret；未来镜像/部署 job 若需要认证，必须在 GitHub Settings → Secrets 中配置，并通过 `env:`/`with:` 注入，同时在本节登记：

| Secret 名称 | 注入位置 | 用途 |
| --- | --- | --- |
| （当前无） | — | — |

`check-workflows.sh` 会拒绝任何未经声明、或硬编码到 workflow 中的凭据。

## 8. 本地运行与验收

正本脚本均可独立运行（`bash scripts/ci/<script>.sh [checkout]`）：

```bash
bash scripts/ci/check-workflows.sh
bash scripts/ci/verify-gate-chain.sh --dry-run
bash scripts/ci/backend-verify.sh
bash scripts/ci/frontend-verify.sh
bash scripts/ci/contract-verify.sh
bash scripts/ci/browser-e2e-verify.sh
bash scripts/ci/collect-environment.sh
bash scripts/ci/summarize-tests.sh
bash scripts/ci/delivery-checkpoint.sh
```

本地开发机工具链与 CI 不一致时，通过环境变量覆盖预期版本（CI 仍由 workflow `env:` 严格固定）：

```bash
OJ_CI_JAVA_MAJOR=25 OJ_CI_NODE_MAJOR=24 OJ_CI_NPM_VERSION=11.12.1 bash scripts/ci/backend-verify.sh
```

可重复验收（静态校验 + 受控失败阻断 + GREEN）：

```bash
bash scripts/ci/verify-workflow-gates.test.sh
```

该脚本会：对真实 checkout 跑静态校验；以 dry-run 验证注入失败时 `delivery` 被跳过；用 9 个变异 workflow 断言硬性规则不可绕过；在 fixture 克隆中注入真实编译错误验证失败阻断；移除缺陷后验证 GREEN 链路到达并通过 `delivery`。

## 9. 与 Issue #290 验收标准映射

| 验收标准 | 落地位置 |
| --- | --- |
| PR 与 `dev` push 自动触发，并发避免旧提交覆盖 | `ci.yml` 的 `on:`/`concurrency` |
| checkout、依赖安装、编译、单元/集成测试、前端构建 | `backend-verify.sh`、`frontend-verify.sh` |
| 真实浏览器业务 E2E | `browser-e2e-verify.sh` → `run-business-e2e-disposable.mjs`；一次性 H2、Spring Boot、Vite 与 Chromium 覆盖 AUTH、CRS、LAB、HWK、GRD、LRN 维护中的业务场景 |
| 前置失败阻断后续交付 job | `needs` 链 + 无 `continue-on-error` |
| 失败运行保留报告/日志/环境/精确 SHA | `if: always()` 上传 + `collect-environment.sh` |
| 成功运行记录通过/失败/跳过数量 | `summarize-tests.sh` + surefire/vitest XML |
| workflow/job/step 超时，缓存 key 对齐 lockfile | `timeout-minutes`、setup-java/setup-node 缓存 |
| 权限最小化，secret 只从 GitHub Secrets 注入 | `permissions: contents: read` + 第 7 节 |
| 静态检查与受控失败可重复执行 | `check-workflows.sh` + `verify-workflow-gates.test.sh` |
