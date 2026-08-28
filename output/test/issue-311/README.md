# Issue #311 验证证据

## 范围

- Issue：`#311 [D6-SVC-AUTH] 抽取身份服务并完成独立交付`
- 基线：`2a3d355`
- 分支：`feature/311-auth-service`
- 测试 SHA：最终验证后填写
- 环境：Windows 11、Java 21.0.9、Maven 3.9.9、Docker Desktop

## 当前已验证

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 独立服务测试 | PASS | 39 tests，0 failures，0 errors，0 skipped |
| 独立打包 | PASS | `onlinejudge-auth-service-0.1.0-SNAPSHOT.jar` |
| Java 模块边界 | PASS | forbidden-java=0 |
| SQL 归属边界 | PASS | forbidden-sql=0，仅七张 `t_auth_*` 表 |
| Compose 静态展开 | PASS | `docker compose ... config --quiet` exit 0 |
| 单体 AUTH 兼容回归 | 待最终验证 | `raw/backend-auth-regression.txt` |
| 单体全量回归 | 待最终验证 | `raw/backend-full-regression.txt` |
| 镜像构建与 OCI 标签 | 待 Docker Engine | `raw/auth-image.txt` |
| Compose、探针和数据库授权 | 待 Docker Engine | `raw/auth-compose-smoke.txt` |

## Red-Green 记录

- 仓库契约 RED：缺少 `services/auth-service/pom.xml`；补最小独立工程后 GREEN。
- 行为迁移 RED：独立模块缺少 AUTH 类型；迁移 AUTH 与最小公共契约后 30 条兼容测试 GREEN。
- 探针 RED：系统路径不存在；补 health/readiness/version 与安全依赖故障处理后 GREEN。
- 数据库 RED：缺少独立迁移且关闭 seed 仍产生 3 个账号；补迁移和条件配置后 GREEN。
- 容器 RED：独立 Dockerfile 不存在；补固定摘要、非 root 镜像与精确 SHA Compose 后 GREEN。
- 边界检查器自证：对 `backend` 执行时发现 380 个跨模块 Java 匹配并返回非零；对 `services/auth-service` 执行时返回 0。

## 最终原始记录

最终验证将写入以下文件：

- `raw/auth-service-test.txt`
- `raw/auth-service-package.txt`
- `raw/auth-boundary.txt`
- `raw/auth-image.txt`
- `raw/auth-compose-smoke.txt`
- `raw/backend-auth-regression.txt`
- `raw/backend-full-regression.txt`

镜像验证未实际完成前，不得把对应条目标记为 PASS。单体全量回归需与基线 `408 tests / 1 failure / 7 skipped` 比较；已知无关失败为 `GrdLrnIntegrationTest.grdGradeEventsCreateLrnNotificationsForPublishChangeAndReviewFlow` 的异步通知顺序波动。
