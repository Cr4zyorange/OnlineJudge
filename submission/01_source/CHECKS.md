# CHECKS — Issue #378 验收矩阵

计数口径：每项检查记 PASS / FAIL / BLOCKED；`total = pass + fail + blocked`。原始输出全部在本目录 `evidence/`。

**总计：total=32，pass=32，fail=0，blocked=0。**

## AC-SOURCE-01 冻结对象与时间可审计，候选值未冒充最终值 — PASS

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| FINAL_SHA 解析（remote URL、commit/tree/parent/时间） | PASS | `evidence/freeze-audit.txt` |
| `monolith-start` 类型/对象/指向 commit/日期/祖先关系 | PASS（annotated tag `515bd6be…` → `78715f21…`，2026-08-25T14:50:18+08:00，IS-ANCESTOR=yes） | `evidence/freeze-audit.txt` |
| detached 干净 worktree `git status --porcelain` | PASS（0 行） | `evidence/worktree-porcelain.txt` |
| 候选值隔离：重做后 bundle 的 `refs/heads/dev` = `977338f4…`；`c56b16f9…`（2026-09-02 候选）只作历史基线存在 | PASS | `evidence/bundle-verify-clone.txt`、`snapshots/refs-all.txt` |
| 冻结于前置 PR 合并后的最新 origin/dev（含 #383/#395 等 D10 兄弟交付） | PASS | `freeze-audit.txt` 中 parent=`84fb6281` |

## AC-SOURCE-02 两版本可离线解压且构建入口齐全 — PASS

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| gzip 完整性（两版） | PASS ×2 | `evidence/archive-listing-check.txt` |
| 全量解压（`tar -xzf` 到 checkout 外短路径） | PASS ×2 | `evidence/archive-recheck.txt`、`evidence/archive-monolith-recheck.txt` |
| 构建入口存在：`backend/pom.xml`、`services/{course,assessment,grade,identity}/pom.xml`、`frontend/package.json`+`package-lock.json`、`database/mysql/compose-schema.sql`、`database/seeds/dev-ci.sql`、`database/migrations/manifest.txt`（30 个迁移 SQL）、`deploy/docker/compose.yml`、两个 Dockerfile、`scripts/dev/start-dev.sh`、`scripts/docker/build-images.sh` | PASS（16 项 OK） | `evidence/archive-recheck.txt` [6] |
| `services/gateway` 形态核对 | PASS（Nginx 形态：`Dockerfile`+`nginx.conf`+`entrypoint.sh`，无 pom.xml 属预期） | 同上 |
| `frontend/package.json` JSON 语法（node 解析） | PASS | `evidence/archive-recheck.txt` [7] |
| 依赖声明：`backend/pom.xml`、各服务 `pom.xml`、`frontend/package-lock.json` 均随归档分发 | PASS | 清单 `source-manifest-*.tsv` |

## AC-SOURCE-03 完整提交记录可离线读取，bundle 已实际 verify/clone — PASS

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| `git bundle verify`（97 refs → 重做后 clean refs；complete history） | PASS | `evidence/bundle-verify-clone.txt` |
| 实际 `git clone` 自 bundle：HEAD=`977338f4…`、`monolith-start` 可解析=`78715f21…`、`rev-list --all --count`=1065、checkout 状态干净 | PASS ×4 | 同上 |
| 文本快照可离线读取：1065 提交日志、91 引用、全部标签 | PASS | `snapshots/git-log-dev-full.txt`、`refs-all.txt`、`tags.txt` |

## AC-SOURCE-04 根 README 与 FINAL_SHA 一致 — PASS

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 新增「源码交付冻结基线（#378）」：两版 SHA/tree/时间/祖先关系/worktree 审计 | PASS | 根 `README.md` 该节，与本目录 INDEX 一致 |
| 环境/端口/健康/就绪/版本端点/账号/初始数据逐项与 FINAL 实物核对（compose 镜像 digest、`/api/v1/system/health`、`/readiness`、`/api/v1/system/version`、`OJ_HTTP_PORT` 8088、演示账号、`database/seeds/dev-ci.sql`） | PASS | 根 README「环境与服务事实」表；端点存在性由 `services/identity/src/main/java/.../AuthWebConfig.java:37` 与 `AuthSystemControllerTest` 佐证 |
| 过期段落修正：D3 段落原引用 `origin/dev@5cdbe853…`/run 33227922081 已改为冻结基线 + 指向 #379 ACTIONS-MANIFEST 正本，不再复制易漂移 run 链接 | PASS | 根 README「D3 CI/CD 与临时 Kind 状态」段 |

## AC-SOURCE-05 归档不含产物/本地数据/缓存/Secret/缺失对象 — PASS

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 跟踪文件泄漏扫描：`node_modules/`、`target/`、`dist/`、`.idea`、`.vscode`、`backend/data/`（两版） | PASS（0 命中，git archive 只含跟踪文件） | 会话执行记录；`source-manifest-*.tsv` 可复查 |
| 符号链接 | PASS（0） | `evidence/archive-recheck.txt` [5]、`archive-monolith-recheck.txt` |
| Submodule / Git LFS | PASS（均未使用） | `evidence/archive-recheck.txt` [8] |
| 敏感模式扫描（私钥/AKIA/ghp_/github_pat_/xox） | PASS（唯一命中为 `scripts/ci/check-workflows.sh` 内 CI 自身的扫描正则字面量，良性白名单） | `evidence/archive-recheck.txt` [11] |
| 配置中口令字面量 | PASS（`application.yml`/`application.properties` 中 `password` 均为空值占位，真实值由环境变量注入） | 同上 |
| >10MB 大文件（15 个） | PASS（`OnlineJudge.pptx` 95.7MB、`演示视频.mp4` 86.8MB、`frontend/src/assets/live-back3.mp4` 78.4MB（被 `backgroundOptions.ts` 引用，前端构建必需）、12 个性能原始 `json.gz`；无构建产物） | `evidence/archive-recheck.txt` [9] |

## AC-SOURCE-06 INDEX/清单/哈希完整，解压回读复算一致 — PASS

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 文件数：final 4170=4170；monolith 1038=1038 | PASS | `evidence/archive-recheck.txt`、`archive-monolith-recheck.txt` |
| 字节总量：final 726,533,045=726,533,045；monolith 388,881,293=388,881,293 | PASS | 同上 |
| FINAL 逐文件大小对比（4170 个） | PASS（0 mismatch） | `evidence/archive-recheck.txt` |
| 内容哈希抽验（git blob 身份）：final 28/28、monolith 11/11（关键入口 + 每隔 197/100 个文件） | PASS | `evidence/content-hash-sampling.txt`、`content-hash-sampling-monolith.txt` |
| 清单可复核：`source-manifest-*.tsv` 直接由 `git ls-tree -r -l` 生成，可与任意解压文件对账 | PASS | 两个 `.tsv` |

说明：`tar -t` 列表行数（5043/1247）大于文件数是 tar 元数据条目表示差异；以解压后磁盘复核为准。该差异在 `evidence/archive-listing-check.txt` 中如实保留。

## AC-SOURCE-07 环境、SHA、命令、计数与原始日志 — PASS

- 环境：Windows 10（26200）MINGW64 x86_64 / git 2.47.1.windows.2 / GNU tar 1.35 / Docker 29.3.1（未用于本 Issue 的“低成本离线检查”范围）。
- base=head：两版检查均在 `977338f4…`（final）与 `78715f21…`（monolith）上进行；PR 分支为 `feature/378-source-freeze`。
- 命令：`evidence/archive-commands.txt`（含 `core.autocrlf=false` 必要性说明）。
- 原始日志：`evidence/` 11 个文件 + `snapshots/` 3 个文件，全部为命令真实输出。

## 复现注意事项

1. 必须使用 `git -c core.autocrlf=false archive … | gzip -n` 生成归档，否则 Windows 默认 `autocrlf=true` 会产生 CRLF 污染（本次实际踩坑并重做，见 `evidence/archive-crlf-investigation.txt`）。
2. Windows 上解压/clone 需 `git config core.longpaths true`（#379 深层证据路径超 260 字符）；Linux/macOS 无此问题。
3. tar 列表计数含元数据条目，复核一律以解压后 `find -type f | wc -l` 与逐文件 `stat` 对账为准。
