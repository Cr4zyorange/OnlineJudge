# 归档校验记录

执行目录：issue #379 隔离 worktree；canonical source 快照来自
`3a26ed2fe9399305b5e44eeae581911e6d32710e`，PR 候选 head 为
`82dd58d10eb49f1ceacec7965f7932c123891a1a`；候选 Disposable artifact 内部构建 SHA
为 `7402fc614933242f7982c2b68c44cb40dfa67045`。

| 检查 | 结果 | 说明 |
| --- | --- | --- |
| workload manifest validator | PASS | `9 workloads; 4 ordered migration jobs; schema, ports, dependencies, migrations, promotion, and D3 retirement are valid` |
| PR candidate CI/D3-adjacent delivery | PASS (candidate only) | run `33707236357` all quality gates and the integrated Disposable delivery job succeeded; it is not `d3-delivery` |
| final SHA CI/D3 provenance | BLOCKED | current `origin/dev` SHA is `3a26ed2f…`; #388 has fixed the former Grade MySQL count, but the new final CI/D3 evidence is not yet available |
| workflow static checks | PASS | `check-workflows: PASS (67 checks)` |
| shell syntax | PASS | `bash -n` 覆盖归档的 delivery/kind/platform/docker shell files |
| JSON syntax | PASS | 归档的 `workloads.json` 与 `workload-manifest.schema.json` |
| YAML syntax | PASS with limitation | Kubernetes YAML 可被通用 YAML loader 读取；Compose overlay 使用 Docker Compose 专用 `!reset` 标签，不能被未注册该标签的 PyYAML 解析器读取 |
| symlink check | PASS | `source/` 与 `evidence/` 无符号链接 |
| empty placeholder check | PASS | `source/` 与 `evidence/` 无空文件 |
| local Markdown links | PASS | 归档内相对链接均可解析；外部链接保留为 GitHub URLs |
| `git diff --check` | PASS | tracked diff 无 whitespace error |
| authored text whitespace | PASS | 索引、SOURCE-MAP、验收和契约快照无尾随空白；Actions 原始诊断保留其原始字节，不做格式化 |
| secret-value scan | PASS with exclusions | 只保留 Secret key/ref；未归档 `.env`、运行时 Secret 和含字面量密码的非 canonical `compose.assessment.yml` |
| source build-input closure | PASS | Gateway Dockerfile 引用的 `scripts/gateway/**` 已纳入快照；业务源码仍明确由 final SHA 正本提供 |
| D8 cross-issue evidence linkage | PASS with provenance note | #319/PR #374 已提供 HPA 配置与 Round 8 原始证据；#379 只引用 canonical evidence，不把 `cf2979dc…` 宣称为当前 final SHA |

## 测试环境

平台单元测试共发现 67 个测试，在仓库开发容器提供的 Node 22 环境中全部通过；主机
直接运行时缺少 `node`，因此本地用容器复演该命令，不将主机工具缺失记为产品失败。
旧基线 run `33698399654` 和前一 final SHA 的失败链已归档。#388 已修复前一 final SHA
的 Grade MySQL contract 静态计数；当前 final SHA 为 `3a26ed2f…`，等待新的 CI run。
候选 run
`33707236357` 成功，但其 PR head `82dd…` 与 integrated delivery artifact 记录的
构建 SHA `7402…` 不同；两者均不能替代合入 `dev` 后由 push 触发的 issue #379 要求的
`d3-delivery`。

## 敏感值处理

原始 Kubernetes 诊断只显示 Secret key 引用，不包含值。workflow 中的
`${{ secrets.* }}` 和 Compose 的 `${VAR:?required}` 仍保留，因为它们是契约源代码，
不是秘密本身。镜像 inspect 只保留 tag、digest 和 OCI revision。
