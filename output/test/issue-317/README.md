# Issue #317 验证记录

## 基线

- 基线：`origin/dev@1f7c890`
- 被测分支：`feature/317-gateway-routing`
- 当前实现提交：在本文件更新前运行 `git rev-parse HEAD` 获取

## 已执行

| 范围 | 命令 | 结果 |
| --- | --- | --- |
| 渲染器输入与 Header 清除 | `bash scripts/gateway/tests/render-gateway-config.test.sh` | PASS；非法上游值被拒绝 |
| 默认配置 | `bash scripts/gateway/tests/gateway-default-config.test.sh` | PASS；镜像默认配置与 all-monolith 渲染结果一致 |
| 路由与错误边界 | `bash scripts/gateway/tests/gateway-routing-contract.test.sh` | PASS；课程子路径优先、Bearer、清除 Header、502/504、SPA fallback 已检查 |
| 切流/回滚 | `bash scripts/gateway/tests/switch-gateway-target.test.sh` | PASS；AUTH 选择可切换，冒烟失败恢复上次目标并以 1 退出 |
| 冒烟凭据保护 | `bash scripts/gateway/tests/verify-gateway.test.sh` | PASS；Bearer 只经 mode-600 header 文件传入，标准输出不含 token |
| Kind 网关配置 | `bash scripts/gateway/tests/kind-gateway-config.test.sh` | PASS；ConfigMap 与 read-only subPath 挂载已检查 |
| 现有 Compose 契约 | `mvn -q -Dtest=DockerComposeContractTest,ComposeProfilePropertiesTest test` | PASS；Maven 3.9.16 / Oracle JDK 24 |
| 现有 Kubernetes 清单契约 | `bash scripts/test/verify-k8s-manifests.test.sh` | PASS |

## 阻塞的运行时验证

| 范围 | 复现 | 原因 | Owner | 复测条件 |
| --- | --- | --- | --- | --- |
| Nginx 容器语法、Compose 切流和 502/504 运行时响应 | `docker version` | Docker Desktop Linux 引擎管道不存在；`com.docker.service` 为 Stopped，当前会话无权启动服务 | wyx | Docker Desktop Linux 引擎处于 Running |
| Kind 脚本测试 | `bash scripts/test/verify-kind-scripts.test.sh` | WSL 无法解析该 Windows worktree 的 `.git` 指针路径 | wyx | 在原生 Git Bash/PowerShell 或 Linux checkout 中复测 |
| 真正四服务切流 | 以独立服务地址作为目标执行 switch 脚本 | CRS、Assessment、Learning & Grade 独立服务尚未交付到当前 `dev` | 各 D6 服务 Owner | 服务镜像、Service 与健康探针可用 |

本记录未包含密码、真实会话令牌、私有镜像凭据或未脱敏 HTTP Header。
