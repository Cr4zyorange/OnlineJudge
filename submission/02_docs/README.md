# 02_docs 文档归档

本目录是 Issue #368 在固定 `origin/dev@c56b16f916b4a4c3d33915aa37beab6b05c72888` 上生成的最终文档归档。开发正本仍位于 `docs/最终提交`、`docs/过程` 与 `docs/diagrams`；冻结副本、PDF/SVG、追溯清单、渲染日志和哈希由脚本统一生成，禁止手工制造 PASS。

复现：

```powershell
node scripts/delivery/build-issue-368-docs.mjs --base c56b16f916b4a4c3d33915aa37beab6b05c72888
pdftoppm -png -r 96 submission/02_docs/rendered/pdf/<文档>.pdf output/issue-368/pdf-pages/<文档>/page
python scripts/delivery/audit-issue-368-pdf-pages.py --pages output/issue-368/pdf-pages --report submission/02_docs/evidence/pdf-page-audit.json --contacts output/issue-368/pdf-contact-sheets --expected 545 --manual-inspection-note "22 contact sheets visually inspected; no clipping, overlap, missing glyphs, broken tables, black blocks, blank pages, or missing diagrams"
node scripts/delivery/refresh-issue-368-checksums.mjs
node scripts/delivery/verify-issue-368-docs.mjs
```

PDF 页图与联系表是本地复核中间产物，不进入归档；本次对 545 页和 22 张联系表的检查结论见 `evidence/pdf-page-audit.json` 与 `evidence/verification.log`。

入口见 [INDEX.md](INDEX.md)。上游 #319、#320、#340 未形成合并到固定基线的最终证据，均保留为 BLOCKED。
