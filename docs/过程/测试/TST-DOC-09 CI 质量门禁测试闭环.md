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

权威证据（真实 GitHub Actions）：run
[`33139279562`](https://github.com/MontesquieuE/OnlineJudgeForSE/actions/runs/33139279562)
（event=pull_request，ubuntu-24.04，Java 21.0.12、Node 22.23.2、npm 10.9.2、Maven 3.9.x），
PR head `b1311709ae74a8d7d913d4cc12bcaeafe6bebd5b`（rebase 至含 #299/#302 的
`678570a` 基线后），全部 5 个 job 结论 `success`，`delivery` 执行通过；PR check
rollup 全部 `SUCCESS`。run 内各 job 的 `test-summary.txt` 原始输出：

```text
backend unit: files=56 tests=383 failures=0 errors=0 skipped=7
backend integration: files=7 tests=17 failures=0 errors=0 skipped=0
frontend unit: files=1 tests=566 failures=0 errors=0 skipped=0
frontend runner contracts: # tests 3 / # pass 3 / # fail 0 / # skipped 0
```

`environment.json`（同一 artifact）记录 `head_sha: b1311709ae74a8d7d913d4cc12bcaeafe6bebd5b`
（pull_request 事件取 `pull_request.head.sha`，而非 merge 提交 SHA）。

补充（隔离 checkout 重跑，2026-08-27 @ `68b4ee70ed6d2fae3f29a288d80a8bb3afa4ed47`）：

```text
PASS validate-workflows（check-workflows: PASS 50 checks）
PASS backend-gate（compile + 单元 373 tests（skipped 7）+ 集成 15 tests）
PASS frontend-gate（typecheck + 单元 563 tests + build + runner contracts 3 tests）
PASS contracts-gate（shell contract 21 tracked scripts + CommonInfrastructureContractTest 1 test）
RUN delivery（bash scripts/ci/delivery-checkpoint.sh）
PASS delivery
gate-chain: PASS
```

> 计数说明：合并 dev 前（`a2fbec4`）记录为单元 371 tests/skipped 5、前端 556 tests、
> shell contract 19 scripts；合并 dev 并 rebase 到 `678570a` 后新增测试使计数变化
> （单元 383/7、集成 17、前端 566）。计数差异来自基线变化而非行为回归；以真实
> Actions run `33139279562` 的计数为准。

## 4. 真实 Actions 受控失败证据

在 PR head 上临时注入编译错误（`backend/src/main/java/com/onlinejudge/ci/CiControlledFailure.java`
语法错误，commit `91eb146`）并推送，真实运行
[`33138034066`](https://github.com/MontesquieuE/OnlineJudgeForSE/actions/runs/33138034066) 结论：

```text
Validate workflow contracts: success
Frontend typecheck + unit + build + runner contracts: success
Repo contract checks: failure（mvn 编译同样被阻断）
Backend compile + unit + integration: failure
Delivery checkpoint: skipped
run conclusion: failure
```

证据保留方式：受控失败提交取证后已从分支还原（head 回到 `c83092b`），运行记录与 artifact
按 SHA 保留在 Actions 中，PR 历史保持干净。

门禁还拦截并修复了两次真实缺陷（同样导致 delivery skipped）：

| 运行 | head SHA | 失败 job | 根因与修复 |
| --- | --- | --- | --- |
| `33054458192` | `2ac6ec0` | frontend（8 个 `LabStudentAttachments` 用例） | 测试夹具 `publishAt` 无时区，UTC runner 下按本地解析导致“未发布”；改为显式 `+08:00` |
| `33055289509` | `b275a41` | backend（`LearningRecordControllerTest` 限流用例） | 异步写线程池先于循环落库，第 10 次请求被误判 429；测试内可控 executor 挂起写入使语义确定 |
| `33057373593` | `587e537` | backend（基础设施） | Maven Central 429 + Actions 缓存服务 400，冷缓存依赖解析失败；门禁脚本增加仅针对依赖传输失败的 3 次有界重试 |
| `33138722525` | `a1f5577` | backend（`GrdLrnIntegrationTest`） | 变更/复核通知由异步 executor 投递，断言时首条仍为发布通知；测试内增加 5s 有界轮询后再断言 |

## 5. REFACTOR：本地/CI 共用脚本

门禁逻辑全部收敛到 `scripts/ci/*.sh`，workflow 只负责调度与证据上传；`check-workflows.sh` 强制每个门禁 job 调用正本脚本，防止 CI 与本地行为漂移。重构后重新验证：

- 静态校验：50 项检查全部 PASS。
- 9 个变异 workflow（`continue-on-error`、权限放大、未固定 Action、`delivery` 使用 `if: always()`、缺超时、缺 needs、缺并发、`cancel-in-progress: false`、内联命令绕过脚本）全部被拒绝。
- dry-run 全 PASS 链路到达 `delivery`；注入失败时 `delivery` 被跳过且退出码非零。
- 可移植性：`scripts/ci/*.sh` 已标记 100755，验收脚本统一用 `bash` 显式调用，
  变异编辑改用 BSD/GNU 兼容的 `sed -i.bak`，版本比较不依赖 GNU `sort -V`；
  macOS Bash 3.2 兼容（移除 `declare -A`，改用 case 函数与间接展开），
  文档命令在全新 clone 中完整 PASS，不再出现 Permission denied。

## 6. 可重复执行方式

```bash
bash scripts/ci/verify-workflow-gates.test.sh
```

最后全量执行：

- 全新 clone 中 `bash scripts/ci/verify-workflow-gates.test.sh`
  完整 PASS（静态校验、9 个变异全被拒绝、受控编译失败阻断 `delivery`、GREEN 到达并
  通过 `delivery`、环境清单精确记录 PR head/base SHA）。
- 真实 Actions run `33139279562` @ head `b131170` 全 job success（含 `delivery`），
  PR check 全绿；受控失败 run `33138034066` 验证失败阻断（见第 4 节）。

验证范围说明：脚本在 Git Bash/WSL bash 下均可执行（不依赖脚本可执行位）；本机 Java
25/Node 24 与 CI 固定版本（21/22）不一致时，脚本自动按本机工具链覆盖预期版本，CI 的
版本固定由 workflow `env:` 与门禁脚本默认值严格保证。

## 7. 残余风险

- 未在本机执行真实 GitHub Actions 调度；`needs` 语义通过 `verify-gate-chain.sh` 按同一份 `ci.yml` 解析模拟验证，GitHub 侧首次运行时需人工确认 job 依赖与 skipped 表现。
- MySQL 真库并发测试（`CrsMysqlConcurrencyTest`）与 Docker 沙箱测试（`DockerSandboxExecutorTest`）由环境变量显式启用，不纳入默认门禁；需要时由后续部署子任务在独立 job 中补充。
- Playwright 全量 E2E 需要 Compose/MySQL 与浏览器安装，不在本门禁默认范围；共享运行器契约（含“断言失败必须非零退出”）已纳入 `frontend-gate`。
