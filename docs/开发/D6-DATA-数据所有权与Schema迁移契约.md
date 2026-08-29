# D6-DATA 数据所有权与 Schema 迁移契约

## 1. 正本与边界

`database/ownership/table-ownership.csv` 是逐表所有权正本，必须与
`database/migrations/manifest.txt` 和 `database/mysql/compose-schema.sql` 同步。AUTH、CRS、
Assessment（LAB/HWK）与 Learning & Grade（LRN/GRD）分别使用 `oj_auth`、`oj_crs`、
`oj_assessment`、`oj_learning_grade`。一个表只能有一个 owner；其他服务只能经 API、事件或
本地快照消费其数据。

`logical-references.csv` 是跨服务 ID 的契约。逻辑引用不创建数据库外键；写入时同步确认存在性，
删除采用归档/软删除事件，消费者保留历史快照并异步处理孤儿。事件至少包含 eventId、entityId、
version、occurredAt，消费者按 eventId 幂等并以 version 拒绝倒序覆盖。

## 2. 最小权限

`split-schemas.sql` 创建四个 Schema 与四个无登录角色。部署系统在 Secret Manager 中创建带密码账号，
每个账号只授予对应角色；迁移账号单独管理，运行时角色不含 DDL、GRANT、跨 Schema 权限。不得把密码、
Token 或完整授权输出写入仓库或交付日志。

## 3. 迁移顺序与兼容窗口

1. 冻结写入并记录源库版本、manifest checksum、每表行数、主键范围、唯一约束与业务不变量。
2. 执行 `split-schemas.sql`，按所有权清单和 manifest 顺序在目标 Schema 建表。`CREATE TABLE LIKE` 不复制
   外键；目标只恢复 owner 内部外键。
3. 按父表到子表复制全量数据；每批事务失败立即停止，不更新迁移水位。
4. 运行 `verify-split-schemas.ps1`，并校验主键集合、唯一键冲突数为 0、孤儿逻辑引用清单及关键不变量。
5. 先切只读流量，再逐服务切写；兼容窗口内源库保持只读且禁止双写。观察至少一个完整业务周期后再归档。
6. 所有步骤以同一 migration run id 记录，重复执行必须只校验已完成批次或继续未完成批次。

MySQL DDL 会隐式提交，因此失败不能承诺事务级自动撤销。失败时保留源库和目标库，停止后续服务切换；
修复只能新增前向迁移。不得编辑已登记迁移或手工改生产业务行。

## 4. 回滚

切写前直接丢弃目标运行批次并从未变更的源库重试。切写后停止四服务写入，确认没有目标独有写入，执行
`rollback-split-schemas.sql` 所述路由回切；若已有目标独有写入，必须先用审计事件反向补偿并再次执行
全量校验，不能直接覆盖源库。拆分 Schema 在兼容窗口结束前作为备份保留，删除需单独审批。

## 5. 自动门禁

运行：

```powershell
& scripts/test/verify-data-ownership-contract.test.ps1
```

门禁检查实际 schema 中每张业务表恰有一个 owner、迁移属于 manifest、清单无重复、无跨 owner 外键。
任何违规均非零退出。运行时代码的跨 owner SQL 必须在拆分切流前降为 0；严格审计命令为：

```powershell
& scripts/ci/verify-data-ownership.ps1 -RuntimeAudit
```

## 6. 实现与验证状态

基线 `5cdbe85` 中发现的 LRN 跨 owner 查询已改为 `LearningCourseClient`、`LearningUserClient` 和
`LearningAssessmentClient` 契约，由数据 owner 提供 JDBC 实现；演示数据初始化也已拆成 CRS、Assessment、
Learning & Grade 三个 owner 内 Seeder。严格运行时扫描结果为 0，且演示数据幂等测试与完整后端测试通过。

迁移验收使用 `database/tests/run-ephemeral-mysql.ps1` 启动隔离 MySQL 实例，并运行：

```powershell
& database/tests/verify-data-ownership-migration.ps1 `
  -Mysql <mysql.exe> -HostName 127.0.0.1 -Port <port> -AdminUser root
```

已在 MySQL 8.0.45 上实测 fresh、upgrade、repeat、failure、rollback、四账号最小权限，以及 46 张业务表的
行数、扩展校验摘要、主键、唯一约束和关键业务不变量，共 11 项、通过 11、失败 0、跳过 0。每次运行将
环境、基线 SHA、被测 SHA、命令、统计、退出码和原始日志写入被 Git 忽略的
`ci-artifacts/data-ownership/<run-id>/`。当前机器未安装可用的 MySQL 8.4/Docker 运行环境，因此 8.4 兼容性
不是本次已核实事实；如部署基线指定 8.4，应在对应 CI 镜像用同一脚本复测，不能用本次 8.0.45 结果替代。
