# 归档校验记录

执行目录：issue #379 隔离 worktree；canonical source 快照来自
`c56b16f916b4a4c3d33915aa37beab6b05c72888`，PR 候选为
`82dd58d10eb49f1ceacec7965f7932c123891a1a`。

| 检查 | 结果 | 说明 |
| --- | --- | --- |
| workload manifest validator | PASS | `9 workloads; 4 ordered migration jobs; schema, ports, dependencies, migrations, promotion, and D3 retirement are valid` |
| PR candidate CI/D3-adjacent delivery | PASS (candidate only) | run `33707236357` all quality gates and the integrated Disposable delivery job succeeded; it is not `d3-delivery` |
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

## 测试环境限制

仓库平台单元测试共发现 47 个测试，其中 46 个通过，1 个因本机没有 `node`
可执行文件而在测试启动阶段报 `FileNotFoundError`：
`test_disposable_runtime_generates_a_public_jwks_bootstrap_bundle`。这不是该归档
修改引入的断言失败；GitHub final SHA 的 CI 原始结果仍以
[ci-quality-gate run 33698399654](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33698399654)
为准，并且该 run 的失败原因已归档。候选 run `33707236357` 成功，但只有合入 `dev` 后的
push 才能触发 issue #379 要求的 `d3-delivery`。

## 敏感值处理

原始 Kubernetes 诊断只显示 Secret key 引用，不包含值。workflow 中的
`${{ secrets.* }}` 和 Compose 的 `${VAR:?required}` 仍保留，因为它们是契约源代码，
不是秘密本身。镜像 inspect 只保留 tag、digest 和 OCI revision。
