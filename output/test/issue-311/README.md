# Issue #311 验证证据

## 范围

- Issue：`#311 [D6-SVC-AUTH] 抽取身份服务并完成独立交付`
- 初始基线：`2a3d355`
- 交付前同步基线：`origin/dev@1f7c890`
- 分支：`feature/311-auth-service`
- 测试 SHA：`7bba68e0580ac4fd648ebfe9fbba48f186e4003d`
- 合并后验证 SHA：`69d7485c1c014258ceae363eb6a34fdc5ebc71b0`
- 环境：Windows 11、Java 21.0.9、Maven 3.9.9、Docker Desktop 29.3.1（Linux Engine）

## 当前已验证

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 独立服务测试 | PASS | 39 tests，0 failures，0 errors，0 skipped |
| 独立打包 | PASS | `onlinejudge-auth-service-0.1.0-SNAPSHOT.jar` |
| Java 模块边界 | PASS | forbidden-java=0 |
| SQL 归属边界 | PASS | forbidden-sql=0，仅七张 `t_auth_*` 表 |
| Compose 静态展开 | PASS | `docker compose ... config --quiet` exit 0 |
| 单体 AUTH 兼容回归 | PASS | 33 tests，0 failures，0 errors，0 skipped；`raw/backend-auth-regression.txt` |
| 单体全量回归（测试 SHA） | 基线内 | 410 tests，1 failure，0 errors，7 skipped；唯一失败为当时已知 GRD/LRN 时序用例；`raw/backend-full-regression.txt` |
| 已知失败隔离重跑 | PASS | 1 test，0 failures；`raw/backend-known-flake-rerun.txt` |
| 同步最新 `dev` 后全量回归 | PASS | 410 tests，0 failures，0 errors，7 skipped |
| 镜像构建与 OCI 标签 | PASS | image `sha256:7076e3019370eaaf0012170d421071e7524004de927d7f2ebc18f02ac679a145`；revision/version 均等于测试 SHA；user=`10001:10001` |
| Compose 服务 | PASS | `auth-db` 与 `auth-service` 均 healthy；`raw/auth-compose-smoke.txt`、`raw/auth-runtime.txt` |
| HTTP 探针 | PASS | health/readiness=`UP`，version revision 等于测试 SHA；`raw/auth-probes-and-image.txt` |
| MySQL 迁移与权限 | PASS | 迁移重复执行 exit 0；七张 AUTH 表；账号仅获 `onlinejudge_auth` 权限；读取 `mysql.user` 被拒绝；`raw/auth-db-boundary.txt` |

## Red-Green 记录

- 仓库契约 RED：缺少 `services/auth-service/pom.xml`；补最小独立工程后 GREEN。
- 行为迁移 RED：独立模块缺少 AUTH 类型；迁移 AUTH 与最小公共契约后 30 条兼容测试 GREEN。
- 探针 RED：系统路径不存在；补 health/readiness/version 与安全依赖故障处理后 GREEN。
- 数据库 RED：缺少独立迁移且关闭 seed 仍产生 3 个账号；补迁移和条件配置后 GREEN。
- 容器 RED：独立 Dockerfile 不存在；补固定摘要、非 root 镜像与精确 SHA Compose 后 GREEN。
- MySQL 实机 RED：`CREATE INDEX IF NOT EXISTS` 在 MySQL 8.4 初始化时报 1064；改为建表内联 `KEY` 并补兼容断言后，空库初始化与迁移重复执行均 GREEN。
- 边界检查器自证：对 `backend` 执行时发现 380 个跨模块 Java 匹配并返回非零；对 `services/auth-service` 执行时返回 0。

## 最终原始记录

最终验证将写入以下文件：

- `raw/auth-service-test.txt`
- `raw/auth-service-package.txt`
- `raw/auth-boundary.txt`
- `raw/auth-image.txt`
- `raw/auth-compose-smoke.txt`
- `raw/auth-probes-and-image.txt`
- `raw/auth-runtime.txt`
- `raw/auth-db-boundary.txt`
- `raw/backend-auth-regression.txt`
- `raw/backend-full-regression.txt`
- `raw/backend-known-flake-rerun.txt`

## 结论

独立服务、AUTH 兼容、边界、打包、镜像、探针、数据库初始化、重复迁移和最小权限均通过。测试 SHA 上的单体全量回归相对初始基线新增 2 条 #311 仓库契约测试，已知 GRD/LRN 时序用例隔离重跑通过。交付前合入最新 `origin/dev@1f7c890` 后再次执行独立服务测试、边界检查和后端全量回归，最终 410 条后端测试全部通过（7 条按设计跳过）。

Docker 验证结束后已删除本次创建的 `onlinejudge-auth` 测试容器、网络和临时数据卷；没有删除用户已有的 `opengaussdb` 容器或其他 Docker 资源。
