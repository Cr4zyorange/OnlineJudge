# 检查结果

本目录记录 #380 工作内容 10 的检查输出：

- `SHA256SUMS.txt`：`submission/04_tests/` 全文件 SHA-256（相对本目录路径，
  `sha256sum --check` 可复算）。
- `filecount.txt`：文件数与总体积。
- `empties.txt`：0 字节/空占位扫描结果。
- `links.txt`：Markdown 相对链接存在性检查结果。
- `secretscan.txt`：敏感信息模式扫描结果（Bearer/token/私钥/口令等）。
- `diff-check.txt`：`git diff --check` 结果。

若上述文件在最终编辑后未同步，以实际重新生成的输出为准并再次复核。
