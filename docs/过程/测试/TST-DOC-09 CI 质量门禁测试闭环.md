# TST-DOC-09 CI 质量门禁测试闭环（Issue #290）

| 文档编号 | TST-DOC-09 |
| --- | --- |
| 对应 Issue | #290 GitHub-hosted Actions 质量门禁 |
| 所属阶段 | 开发期 CI/CD 基础设施 |
| 涉及模块 | 基础设施（AUTH/CRS/LAB/HWK/GRD/LRN 公共构建与测试链路） |
| 文档依据 | `docs/开发/CI-质量门禁开发流程.md`、`AGENTS.md` |

## 1. 验证目标

1. PR 与 `dev` push 自动触发质量门禁；并发策略避免旧提交覆盖新提交状态。
2. checkout、依赖安装、编译、单元测试、集成测试、前端构建与跨模块契约验证全部执行。
3. 前置任一步失败时，后续交付 job（`delivery`）不执行。
4. 失败运行仍保留测试报告、日志、环境与精确 SHA；成功运行记录通过/失败/跳过数量。
5. workflow 静态校验与受控失败阻断可重复执行。

## 2. RED：受控失败验证

验收脚本在 fixture 克隆中注入真实编译错误（`backend/src/main/java/com/onlinejudge/ci/CiControlledFailure.java` 语法错误），通过链路模拟器运行 `backend-gate`：

```text
RUN backend-gate (bash scripts/ci/backend-verify.sh)
[ERROR] ... 编译失败
FAIL backend-gate
SKIPPED delivery (dependency failed: backend-gate)
gate-chain: FAIL
```

结论：编译门禁失败后 `delivery` 被跳过，链路退出码非零，后续交付 job 未执行。

RED 基线（实现前）：`verify-workflow-gates.test.sh` 因 `check-workflows.sh`、`verify-gate-chain.sh` 与 `.github/workflows/ci.yml` 缺失而失败，证明测试先行。

## 3. GREEN：全套质量门禁通过

修复注入缺陷后重跑同一链路：

```text
PASS validate-workflows（check-workflows: PASS 50 checks）
PASS backend-gate（compile + 单元 371 tests + 集成 15 tests）
PASS frontend-gate（typecheck + 单元 556 tests + build + runner contracts 3 tests）
PASS contracts-gate（shell contract 19 scripts + CommonInfrastructureContractTest）
RUN delivery
PASS delivery
gate-chain: PASS
```

测试汇总（`test-summary.txt`）：

```text
backend unit: files=54 tests=371 failures=0 errors=0 skipped=5
backend integration: files=6 tests=15 failures=0 errors=0 skipped=0
backend contract: files=1 tests=1 failures=0 errors=0 skipped=0
frontend unit: files=1 tests=556 failures=0 errors=0 skipped=0
frontend runner contracts: tests 3 / pass 3 / fail 0 / skipped 0
```

## 4. REFACTOR：本地/CI 共用脚本

门禁逻辑全部收敛到 `scripts/ci/*.sh`，workflow 只负责调度与证据上传；`check-workflows.sh` 强制每个门禁 job 调用正本脚本，防止 CI 与本地行为漂移。重构后重新验证：

- 静态校验：50 项检查全部 PASS。
- 9 个变异 workflow（`continue-on-error`、权限放大、未固定 Action、`delivery` 使用 `if: always()`、缺超时、缺 needs、缺并发、`cancel-in-progress: false`、内联命令绕过脚本）全部被拒绝。
- dry-run 全 PASS 链路到达 `delivery`；注入失败时 `delivery` 被跳过且退出码非零。

## 5. 可重复执行方式

```bash
bash scripts/ci/verify-workflow-gates.test.sh
```

验证范围说明：脚本在 Git Bash/WSL bash 下均可执行；本机 Java 25/Node 24 与 CI 固定版本（21/22）不一致时，脚本自动按本机工具链覆盖预期版本，CI 的版本固定由 workflow `env:` 与门禁脚本默认值严格保证。

## 6. 残余风险

- 未在本机执行真实 GitHub Actions 调度；`needs` 语义通过 `verify-gate-chain.sh` 按同一份 `ci.yml` 解析模拟验证，GitHub 侧首次运行时需人工确认 job 依赖与 skipped 表现。
- MySQL 真库并发测试（`CrsMysqlConcurrencyTest`）与 Docker 沙箱测试（`DockerSandboxExecutorTest`）由环境变量显式启用，不纳入默认门禁；需要时由后续部署子任务在独立 job 中补充。
- Playwright 全量 E2E 需要 Compose/MySQL 与浏览器安装，不在本门禁默认范围；共享运行器契约（含“断言失败必须非零退出”）已纳入 `frontend-gate`。
