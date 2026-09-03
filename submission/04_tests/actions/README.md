# Actions artifacts（离线归档）

`artifacts/` 保存本 Issue 依赖的 Actions artifact zip 原样文件；解压后的证据分别
放在各分册目录（`01-unit-integration/ci`、`03-e2e/ci`、`03-e2e/history`、
`05-resilience/ci`）。

清单（run、artifact id/name、SHA-256、下载时间、过期时间）见
[`manifest.json`](manifest.json)。过期风险：GitHub 侧 retention 为 14 天
（`expires_at` 来自 GitHub API），归档后以本目录 zip 为准。

未归档的 run（仅元数据引用，不依赖其 artifact 做 #380 判定）：d3-delivery
`33714164312`（#379/03_devops 范围）、#340 PR 冗余门禁 `33708861783`、#307 head
CI `33696824293`、#320 合入 push `33708404785` 等；其 URL 与状态见 `../INDEX.md`
第 3 节。
