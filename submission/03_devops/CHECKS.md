# 归档校验记录

执行目录：issue #379 隔离 worktree；canonical source 快照来自
`3a26ed2fe9399305b5e44eeae581911e6d32710e`，PR 候选 head 为
`2552a2a2d3f30ed7c48770469c161ce3b42554e0`；同步后的 PR quality-gate run 为
`33727688910`，其 Disposable artifact 的内部构建 SHA 与 PR head/final dev SHA
分开记录，不能冒充 final SHA。

| 检查 | 结果 | 说明 |
| --- | --- | --- |
| workload manifest validator | PASS | `9 workloads; 4 ordered migration jobs; schema, ports, dependencies, migrations, promotion, and D3 retirement are valid` |
| PR candidate CI/D3-adjacent delivery | PASS (candidate only) | run `33727688910` 的 workflow contracts/backend/frontend/repo contracts/browser E2E/Disposable delivery 全部成功；它不是 `d3-delivery` |
| final SHA CI/D3 provenance | BLOCKED | current `origin/dev` SHA is `3a26ed2f…`; #388 已修复前一 Grade MySQL count，正式 final CI/D3 仍待合入后的 `dev` push |
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
的 Grade MySQL contract 静态计数；同步后的候选 run `33727688910` 全部成功，但仍是
PR 事件，不能替代合入 `dev` 后由 push 触发的 issue #379 要求的 `d3-delivery`。

## 敏感值处理

原始 Kubernetes 诊断只显示 Secret key 引用，不包含值。workflow 中的
`${{ secrets.* }}` 和 Compose 的 `${VAR:?required}` 仍保留，因为它们是契约源代码，
不是秘密本身。镜像 inspect 只保留 tag、digest 和 OCI revision。
