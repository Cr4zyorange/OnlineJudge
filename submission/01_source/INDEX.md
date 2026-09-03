# INDEX — 01_source 总索引（Issue #378）

生成时间：2026-09-03（Asia/Shanghai，UTC+8）；冻结执行者：linkverb0510。
冻结基线：`origin/dev@977338f414a8cb72df157b139c8546d870e8bf23`；改造前基线：`monolith-start` → `78715f21288782a2c7ef1d9c23f933c46569b108`。

## 归档实物

### 仓库内交付（本目录 `archives/`，90MB 分卷）

两版全量源码归档本体以 90MB 分卷随仓库交付（规避 GitHub 单文件 100MB 限制），`cat` 顺序合卷后与原始归档字节一致（合卷 SHA-256 已复算核验，见 `evidence/archive-parts-reassembly.txt`）：

| 归档 | 分卷 | 合卷后字节 | 合卷后 SHA-256 |
| --- | --- | --- | --- |
| `archives/source-onlinejudge-monolith-start-78715f21288782a2c7ef1d9c23f933c46569b108.tar.gz.part-00～04` | 5 卷 | 381,091,605 | `a47866ac3add09020a13af9547347045fafb3de78261e69bd5f1d6e0310f5492` |
| `archives/source-onlinejudge-final-977338f414a8cb72df157b139c8546d870e8bf23.tar.gz.part-00～07` | 8 卷 | 667,008,167 | `dad14afad843b7f00ca0f1780c9be647cec4e44d3e3b525b9107664690d40847` |

合卷与校验：

```bash
cat source-onlinejudge-monolith-start-*.tar.gz.part-* > monolith-start.tar.gz
cat source-onlinejudge-final-*.tar.gz.part-* > final-977338f4.tar.gz
# 对合卷文件执行 sha256sum，应得到上表哈希；随后按 README「快速复核」解压回读
```

### 仓库外补充（checkout 外交付目录）

位置：`<repo 父目录>/d10-01_source-delivery/`（开发机路径 `OJSE\d10-01_source-delivery\`）；含同样两版归档的原始单文件与全历史 git bundle（约 0.8 GB，由总控 #321 随最终压缩包与云盘分发）。生成与复核命令见 `evidence/archive-commands.txt`。

| 文件 | 字节 | SHA-256 | 内容 |
| --- | --- | --- | --- |
| `source-onlinejudge-monolith-start-78715f21288782a2c7ef1d9c23f933c46569b108.tar.gz` | 381,091,605 | `a47866ac3add09020a13af9547347045fafb3de78261e69bd5f1d6e0310f5492` | 改造前全量源码（1038 文件），顶层目录 `onlinejudge-monolith-start-78715f21/` |
| `source-onlinejudge-final-977338f414a8cb72df157b139c8546d870e8bf23.tar.gz` | 667,008,167 | `dad14afad843b7f00ca0f1780c9be647cec4e44d3e3b525b9107664690d40847` | 改造后全量源码（4170 文件），顶层目录 `onlinejudge-final-977338f4/` |
| `history-onlinejudge-all-20260903.bundle` | 813,843,396 | `c49d044339dff8fd0142dd66da08b711fc75232b017150494d4edd671f92f106` | 全历史离线仓库（refs/heads/dev=FINAL、refs/tags/monolith-start，complete history） |

可复现性要点：归档必须以 `git -c core.autocrlf=false archive … | gzip -n` 生成。系统默认 `core.autocrlf=true` 会把文本文件转成 CRLF，导致解压字节与 git 树不一致（本次已实际发现、归因并重做，全过程见 `evidence/archive-crlf-investigation.txt`）。

## 仓库内索引与证据

| 文件 | 说明 |
| --- | --- |
| `README.md` | 目录定位与快速复核命令 |
| `SOURCE-MANIFEST.md` + 两个 `.tsv` | 逐文件清单：mode、blob SHA、字节、路径（FINAL 4170 行 / monolith 1038 行） |
| `REVISIONS.md` | 里程碑级 PR/Issue 版本说明（含 #368 被 revert 的诚实记录） |
| `CHECKS.md` | AC-SOURCE-01～07 验收矩阵：命令、计数、结论 |
| `SHA256SUMS` | 本目录全部文件的 SHA-256 |
| `snapshots/git-log-dev-full.txt` | dev 全部 1065 个提交（SHA/父/作者/时间/主题） |
| `snapshots/refs-all.txt`、`snapshots/tags.txt` | 91 个引用与全部标签 |
| `evidence/`（11 个文件） | freeze-audit、worktree-porcelain、archive-commands、archive-listing-check、archive-recheck、archive-monolith-recheck、content-hash-sampling（final+monolith）、archive-crlf-investigation、artifact-sha256、bundle-verify-clone |

## 验证结论摘要（详见 CHECKS.md）

- 两版归档解压回读：文件数 4170/1038 与 git 树完全一致；字节总量 726,533,045 / 388,881,293 精确一致；FINAL 逐文件大小 0 处不匹配；内容哈希抽验 final 28/28、monolith 11/11 PASS。
- bundle：verify 完整历史；实际 clone 后 HEAD=FINAL、monolith-start 可解析、1065 提交、工作区干净。
- 敏感信息扫描：仅 `scripts/ci/check-workflows.sh` 中 CI 自身的密钥扫描正则字面量（良性白名单）；无 Submodule/LFS/符号链接；>10MB 大文件 15 个均为课件/演示视频/前端背景视频/性能原始数据，非构建产物。
- BLOCKED：0。容器构建属“低成本离线检查”范围外（Docker 29.3.1 本机可用，如需可自行复建）。
