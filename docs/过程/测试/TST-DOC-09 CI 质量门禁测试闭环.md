# TST-DOC-09 CI 质量门禁测试闭环（Issue #290）

| 文档编号 | TST-DOC-09 |
| --- | --- |
| 对应 Issue | #290 GitHub-hosted Actions 质量门禁 |
| 所属阶段 | 开发期 CI/CD 基础设施 |
| 涉及模块 | 基础设施（AUTH/CRS/LAB/HWK/GRD/LRN 公共构建与测试链路） |
| 文档依据 | `docs/开发/CI-质量门禁开发流程.md`、`AGENTS.md` |

## 0. 证据范围与仓库说明（重要）

本 PR 为 **`Cr4zyorange/OnlineJudge#298`**（base=`dev@2a3d355`；非草稿、mergeable）。
第 3 节记录的是撤回无关 HWK 生产改动后的目标仓库真实绿灯；第 4 节保留此前 fork 的
真实受控失败验证。
以下事实于 2026-08-28 通过 GitHub REST API 核查：

- 目标仓库 `Cr4zyorange/OnlineJudge` 已启用私有 fork PR 的只读、无 secrets workflow
  权限。清理范围后的 head `8d8a4ff` 已在目标仓库触发真实 PR run
  [`33154839931`](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33154839931)，
  5 个 job 均为 `success`；其 environment artifact 记录目标 repository、目标 base
  和精确 head SHA（详见第 3 节）。
- 本文第 3、4 节引用的**真实 GitHub Actions 运行全部发生在镜像仓库
  `MontesquieuE/OnlineJudgeForSE`（PR #1）**，运行环境清单记录
  `repository=MontesquieuE/OnlineJudgeForSE`、`base_sha=50a5dccd`（镜像 dev）。
  这些运行明确属于 **fork/镜像验证**：验证的是与目标 PR 相同的分支内容在
  GitHub-hosted runner 上的门禁行为，**不冒充目标仓库 PR 的 check/merge 证据**。
- 镜像 dev（`50a5dccd`）落后于目标 dev（`2a3d355`），因此镜像运行记录的
  `base_sha=50a5dccd` 不是目标基线；目标基线以第 0 节首行为准。
  本分支已 rebase 到目标 `dev@2a3d355`（`git merge-base HEAD target/dev` =
  `2a3d355`），与最新 dev 无冲突。
- 本次证据文档提交不改变 workflow、脚本或测试代码；其后续目标仓库 check 作为最终
  head 的再验证，仍按第 3 节核对 environment artifact 的
  `repository/base_sha/head_sha`。

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

真实 GitHub Actions 上的受控失败见第 4 节（fork 验证）。

## 3. GREEN：目标仓库真实 GitHub Actions 通过

权威目标仓库绿灯：run
[`33154839931`](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33154839931)
（event=pull_request，目标仓库 PR #298），环境清单记录：

```json
{
  "repository": "Cr4zyorange/OnlineJudge",
  "base_sha": "2a3d355804cc585cb9c2e52ad60e9e02a8a38b21",
  "head_sha": "8d8a4ff483cd70956df821f72ba812186fa8d0d4"
}
```

`head_sha` 是撤回无关 HWK 生产改动后的精确 PR head，`base_sha` 是目标 `dev` 基线。
全部 5 个 job 结论 `success`，`delivery` 执行并通过。run 内各 job 的
`test-summary.txt` 原始输出：

```text
backend unit: files=57 tests=391 failures=0 errors=0 skipped=7
backend integration: files=7 tests=17 failures=0 errors=0 skipped=0
frontend unit: files=1 tests=566 failures=0 errors=0 skipped=0
frontend runner contracts: # tests 3 / # pass 3 / # fail 0 / # skipped 0
```

另有 `check-workflows: PASS (50 checks)`、`contract-verify: PASS`（shell contract +
`CommonInfrastructureContractTest` tests run 1，Failures 0）与
`delivery checkpoint: PASS`（artifacts：`ci-*-33154839931`，含 environment.json 与
各 gate 日志）。

> 说明：`environment.json` 的 `head_sha` 取自 pull_request 事件的
> `pull_request.head.sha`（精确 PR head）；`delivery/checkpoint.txt` 的 `head_sha`
> 取自 `GITHUB_SHA`（pull_request 事件为 merge ref 提交），环境清单以
> `environment.json` 为准。

> 修订说明：run `33154839931` 验证了所有 CI-only 代码改动。本文档随后只补充该 run
> 的链接与原始结果，不修改 workflow、脚本或测试代码；该文档提交触发的目标仓库 run
> 用于最终 head 的再验证。

计数说明（随基线变化，非回归）：

| 基线 | 后端单元 | 后端集成 | 前端单元 | 绿灯 run |
| --- | --- | --- | --- | --- |
| 合并 dev 前（`a2fbec4`） | 371（skipped 5） | 15 | 556 | — |
| rebase 到目标 `678570a` 后 | 383（skipped 7） | 17 | 566 | `33139279562` @ `b131170` |
| rebase 到目标 `2a3d355` 后（目标仓库验证） | 391（skipped 7） | 17 | 566 | `33154839931` @ `8d8a4ff` |

`383 → 391` 的 +8 后端单元用例来自目标 dev 从 `678570a` 前进到 `2a3d355` 时并入的
数据库引导提交（PR #301）。最终 head 的计数以目标仓库重新运行产生的 artifact 为准。

## 4. 真实 Actions 受控失败证据（fork 验证）

在分支上临时注入编译错误（`backend/src/main/java/com/onlinejudge/ci/CiControlledFailure.java`
语法错误，commit `91eb146`）并推送，镜像仓库真实运行
[`33138034066`](https://github.com/MontesquieuE/OnlineJudgeForSE/actions/runs/33138034066) 结论：

```text
Validate workflow contracts: success
Frontend typecheck + unit + build + runner contracts: success
Repo contract checks: failure（mvn 编译同样被阻断）
Backend compile + unit + integration: failure
Delivery checkpoint: skipped
run conclusion: failure
```

证据保留方式：受控失败提交取证后已从分支还原（当前分支不含注入提交），
运行记录与 artifact 按 SHA 保留在镜像仓库 Actions 中，PR 历史保持干净。

门禁还拦截并修复了四次真实缺陷（同样导致 delivery skipped，均为镜像仓库 fork 运行）：

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
  macOS Bash 3.2 兼容（移除 `declare -A`，改用 case 函数与间接展开）；
  `.github/workflows/*.yml` 通过 `.gitattributes` 固定 `eol=lf`，`core.autocrlf=true`
  的 Windows 全新 clone 也能通过锚定正则的静态校验（修复前 24/50 FAIL）。

## 6. 可重复执行方式

```bash
bash scripts/ci/verify-workflow-gates.test.sh
```

最后全量执行：

- 本地（当前 head）`bash scripts/ci/verify-workflow-gates.test.sh`：静态校验 50/50、
  9 个变异 workflow 全被拒绝、dry-run 全 PASS 链路到达 `delivery`、注入失败后
  `delivery` 被跳过且退出码非零、环境清单精确记录 PR head/base SHA，全部 PASS。
- 编译/测试较重的 RED/GREEN 小节会真正执行 Maven/Node 门禁，需要 Java 21、Node 22
  与 Maven 3.9+ 位于 PATH（与 CI runner 同构）；本开发机 bash 无法解析 Windows
  工具链，故 GREEN 以目标仓库真实 run `33154839931`、RED 以受控失败 fork run
  `33138034066` 为准。在具备完整工具链的全新 clone 上，该命令按第 5 节相同的脚本与
  变异集合端到端执行。
- 真实 GitHub Actions：目标仓库 run `33154839931` @ `8d8a4ff` 全 job success（含
  `delivery`，见第 3 节）；受控失败 run `33138034066` 验证失败阻断（见第 4 节）。
- 目标仓库 PR #298 的 check 与 artifact 已产生；本文档提交后触发的 run 用于最终 head
  的再验证。

验证范围说明：脚本在 Git Bash/WSL bash 下均可执行（不依赖脚本可执行位）；本机 Java
25/Node 24 与 CI 固定版本（21/22）不一致时，脚本自动按本机工具链覆盖预期版本，CI 的
版本固定由 workflow `env:` 与门禁脚本默认值严格保证。

## 7. 残余风险

- **第三方 Action runtime 弃用提示**：run `33154839931` 中 GitHub 提示
  `actions/checkout@v4` 和 `actions/upload-artifact@v4` 的 Node 20 runtime 将被强制
  迁移到 Node 24；本次 run 未受影响。后续应在独立维护变更中升级并重新固定相应 SHA。
- MySQL 真库并发测试（`CrsMysqlConcurrencyTest`）与 Docker 沙箱测试
  （`DockerSandboxExecutorTest`）由环境变量显式启用，不纳入默认门禁；需要时由后续
  部署子任务在独立 job 中补充。
- Playwright 全量 E2E 需要 Compose/MySQL 与浏览器安装，不在本门禁默认范围；共享
  运行器契约（含“断言失败必须非零退出”）已纳入 `frontend-gate`。
