# 01_source 源代码交付（Issue #378）

本目录是源码冻结交付的正本索引：改造前 `monolith-start` 与改造后 `origin/dev@977338f414a8cb72df157b139c8546d870e8bf23` 两个版本的可复现源码归档、完整提交记录、逐文件清单、哈希与全部验证证据。

## 冻结身份

| 项 | 值 |
| --- | --- |
| 改造前 | `monolith-start`（annotated tag `515bd6be…` → commit `78715f21288782a2c7ef1d9c23f933c46569b108`，2026-08-25T14:50:18+08:00，PR #260） |
| 改造后（FINAL） | `977338f414a8cb72df157b139c8546d870e8bf23`（tree `90acd5ca…`，2026-09-03T17:03:42+08:00，PR #383 合并） |
| 冻结环境 | Windows 10 MINGW64 / git 2.47.1.windows.2 / GNU tar 1.35；detached 干净 worktree `status --porcelain` 为空 |
| 审计关系 | `monolith-start` 已验证为 FINAL 的祖先；候选 SHA（如 `c56b16f9…`）未冒充最终值，仅作历史基线出现在历史证据与 #379 归档中 |

## 本目录内容

| 文件/目录 | 说明 |
| --- | --- |
| `INDEX.md` | 总索引：外部归档实物位置、哈希、验证结论汇总 |
| `SOURCE-MANIFEST.md` | 清单说明；逐文件清单见两个 `.tsv`（mode/blob SHA/大小/路径） |
| `source-manifest-final-977338f4.tsv` | FINAL 版 4170 个跟踪文件的逐文件清单 |
| `source-manifest-monolith-start-78715f21.tsv` | monolith-start 版 1038 个跟踪文件的逐文件清单 |
| `REVISIONS.md` | 里程碑级 PR/Issue 版本说明 |
| `CHECKS.md` | 全部验收检查的命令、计数与 PASS/BLOCKED 结论 |
| `SHA256SUMS` | 本目录全部文件的 SHA-256 |
| `evidence/` | 冻结审计、归档命令、解压复算、哈希抽验、敏感信息扫描、bundle verify/clone 原始输出 |
| `snapshots/` | `git-log-dev-full.txt`（1065 提交）、`refs-all.txt`（91 引用）、`tags.txt` |

大体积归档实物（两版 tar.gz 共约 1.0 GB 与全历史 `git bundle` 约 0.8 GB）不进入 git 仓库，存放在 checkout 外的交付目录 `../d10-01_source-delivery/`，由总控 #321 随最终压缩包与云盘分发；其文件名、字节数与 SHA-256 固化在 `INDEX.md` 与 `evidence/artifact-sha256.txt`。

## 快速复核

```bash
# 1. 校验归档完整性（在交付目录内，对照 evidence/artifact-sha256.txt 中的哈希）
sha256sum source-onlinejudge-final-*.tar.gz source-onlinejudge-monolith-start-*.tar.gz history-onlinejudge-all-20260903.bundle
gzip -t source-onlinejudge-final-977338f4*.tar.gz

# 2. 解压后回读（FINAL 应得 4170 个文件、726,533,045 字节）
tar -xzf source-onlinejudge-final-977338f4*.tar.gz
find onlinejudge-final-977338f4 -type f | wc -l

# 3. 提交记录离线可读
git bundle verify history-onlinejudge-all-20260903.bundle
git clone history-onlinejudge-all-20260903.bundle check && git -C check rev-parse HEAD
```

逐条验收结论（AC-SOURCE-01～07）见 `CHECKS.md`。
