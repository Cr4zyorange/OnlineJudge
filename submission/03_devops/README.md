# 03_devops 部署与运维证据索引

本目录是 issue #379 的冻结归档。正本仍在仓库原路径；`source/` 是
`FINAL_SHA` 的只读快照，`evidence/actions/` 是从 GitHub Actions 下载的原始
artifact。归档不包含 Secret 实值，也不把历史成功运行当作当前 SHA 的通过证明。

| 入口 | 内容 |
| --- | --- |
| [INDEX.md](INDEX.md) | 当前 SHA、配置覆盖、证据状态与未满足项 |
| [SOURCE-MAP.md](SOURCE-MAP.md) | Docker/Compose、workflow、Kubernetes、迁移、脚本与契约映射 |
| [ACTIONS-MANIFEST.md](ACTIONS-MANIFEST.md) | run、artifact ID、SHA、时间、大小、保留期与下载路径 |
| [ACCEPTANCE.md](ACCEPTANCE.md) | issue #379 验收矩阵与 BLOCKED 原因 |
| [evidence/actions/](evidence/actions/) | 当前最终 SHA 和历史成功/失败运行的原始输出 |
| [SHA256SUMS](SHA256SUMS) | 快照及原始证据文件完整性校验 |

当前交付的结论是 `BLOCKED`：归档基线为 `origin/dev` 的
`3a26ed2fe9399305b5e44eeae581911e6d32710e`。前一基线的 Grade MySQL 静态契约
误计数已由 #388 修复并合入该基线；新基线的 CI/D3 尚未完成，不能声明最终部署成功。
应在新基线上运行质量门禁和 D3，并在本目录追加新的最终 SHA 归档，而不是覆盖历史
原始证据。
