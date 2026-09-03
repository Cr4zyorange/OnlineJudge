# SOURCE-MANIFEST 说明

逐文件清单以两个 TSV 固化，直接由 `git ls-tree -r -l <revision>` 生成，列为：`mode`、`type`、`blob SHA-1`、`size(bytes)`、`path`。

| 文件 | 版本 | 行数（含 4 行头） |
| --- | --- | --- |
| `source-manifest-final-977338f4.tsv` | `977338f414a8cb72df157b139c8546d870e8bf23` | 4170 文件 |
| `source-manifest-monolith-start-78715f21.tsv` | `78715f21288782a2c7ef1d9c23f933c46569b108`（tag 对象 `515bd6be…`） | 1038 文件 |

复核方式：解压任一归档后，对任意文件执行 `git hash-object <file>`，应与清单中对应 `blob SHA-1` 一致；字节应与 `size` 一致。抽查结果见 `evidence/content-hash-sampling.txt` 与 `evidence/content-hash-sampling-monolith.txt`（合计 39/39 PASS）。

外部归档实物（tar.gz/bundle）的文件名、字节与 SHA-256 见 `INDEX.md` 与 `evidence/artifact-sha256.txt`；本目录自身文件的哈希见 `SHA256SUMS`。
