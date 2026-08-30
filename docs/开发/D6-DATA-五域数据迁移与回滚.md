# D6-DATA 五域迁移、校验、切换与回滚

> 实现 Issue：#341。数据归属正本：[D6-DATA 五域数据所有权与账号隔离契约](D6-DATA-五域数据所有权契约.md)；服务/事件正本：[D6/D7 五服务共享契约（v2）](D6-D7-五服务共享契约-v2.md)。

本文件是将当前模块化单体的 46 张业务表迁至 `oj_identity`、`oj_course`、`oj_assessment`、`oj_grade`、`oj_learning` 的可执行运行手册。它不重新决定数据 owner，也不把当前单体或旧四 schema 方案伪装成五服务部署。

## 1. 迁移模型与安全边界

迁移控制器是 [migrate-five-domain-schemas.mjs](../../database/mysql/migrate-five-domain-schemas.mjs)。ownership ledger 当前有 59 行：其中 46 行是 legacy 业务表，13 行是 #337 已实现的可靠消息运行时表。控制器只复制和逐表核对前 46 张业务表；后 13 张只能从已静止的 source schema `CREATE TABLE ... LIKE` 重建，绝不复制 lease、retry、dead-letter 或已消费状态，并在管理员控制面执行以下可恢复阶段：

1. 创建五个 schema 的 `schema_migrations` 与 `migration_checkpoints`，登记不可修改的 `V20260831_01__five_domain_data_migration` SHA-256。
2. 用 `CREATE TABLE ... LIKE` 建表并以按主键 upsert 复制。只有源库 owner 内 FK 会由元数据恢复；逻辑外部 ID 永远不会变为跨 schema FK、view 或 join。
3. 以 #337 的真实 owner-local 表名初始化可靠运行时：`assessment_event_outbox`、`course_event_outbox`、`grade_event_outbox`、各服务具体 inbox，以及 Learning 的 member projection、watermark、deferred/DLQ/reconciliation 状态表。重建 Grade 的 `t_grade_record`，再以 `PUBLISHED` producer record 和 `APPLIED` consumer record 保存合法 v2 的 `assessment.source-grade.changed.v2` 历史事实；从 Course 重建完整 membership projection、每课程 v1 watermark、`course.member.changed.v2` 与完整 `course.membership.snapshot.v2` 历史事实。它们绝不重新入队，因而不在切换时重复外发。
4. 对 46 张表逐一做行数、聚合 CRC、`CHECKSUM TABLE EXTENDED`、主键定义与双向主键集合校验；对每条可物理验证的跨域 logical ID 做孤儿校验，并确认全部 FK 仍只在 owner schema 内。
5. 以五个运行时账号验证本 schema 的 `SELECT/INSERT/UPDATE/DELETE` 允许，四个 foreign schema 的读取和自身 DDL 均被 MySQL 拒绝。原始 `ERROR 1142` 被写入 evidence，密码不会被记录。

控制器拒绝没有 `--source-read-only-ack` 的 copy/replay。它在复制前后计算完整 59 行 ownership 状态的指纹，并对 46 张业务表作逐表行数与 checksum 校验；任意源库变化都会阻止切换。管理员账号只用于建库、复制和权限控制，不能下发给业务服务。

## 2. 运行与检查点

在一次有维护窗口、已停止 legacy 写入的迁移 Job 中执行。各 password 都只以环境变量注入；下面的值是变量名，不是可提交的 Secret。

```sh
export OJ_MYSQL_ADMIN_PASSWORD='...'
export OJ341_RUNTIME_PASSWORD_IDENTITY='...'
export OJ341_RUNTIME_PASSWORD_COURSE='...'
export OJ341_RUNTIME_PASSWORD_ASSESSMENT='...'
export OJ341_RUNTIME_PASSWORD_GRADE='...'
export OJ341_RUNTIME_PASSWORD_LEARNING='...'

node database/mysql/migrate-five-domain-schemas.mjs \
  --action migrate \
  --admin-user migration_admin \
  --source-schema onlinejudge \
  --source-read-only-ack \
  --evidence ci-artifacts/issue341/migrate.json
```

成功后每个目标 schema 都具有相同的 migration version，以及 `SCHEMA_AND_DATA_COPIED`、`LOCAL_ARTIFACTS_INITIALIZED`、`VERIFIED` checkpoints。copy 使用确定性 upsert；中断后在 legacy 仍然只读的前提下重跑同一命令，会从已存在表和 checkpoint 安全收敛，而不以删除数据卷作为“恢复”。

投影重放可单独重做，仍要求 legacy 只读：

```sh
node database/mysql/migrate-five-domain-schemas.mjs \
  --action replay --admin-user migration_admin --source-schema onlinejudge \
  --source-read-only-ack --evidence ci-artifacts/issue341/replay.json
```

Grade replay 使用 `assessment.source-grade.changed.v2` 的 legacy 事实，Learning replay 使用 `course.member.changed.v2` 加每课程完整 `course.membership.snapshot.v2`；有效载荷保存在实际 Assessment/Course outbox 的 `payload_json`，相应 Grade/Learning inbox 都登记为 `APPLIED`。这些是历史已投影事实，outbox 状态固定为 `PUBLISHED`，不会被 publisher 再次投递。

## 3. 切换与回滚

只有 `--action verify` 成功、全部 46 表校验与账号矩阵通过后，才允许编排层把 gateway 目标切到五域。控制器生成的是可消费的切换 state，不替代 #318 的实际路由/工作负载部署：

```sh
node database/mysql/migrate-five-domain-schemas.mjs \
  --action cutover --admin-user migration_admin --source-schema onlinejudge \
  --cutover-state ci-artifacts/issue341/cutover-state.json \
  --evidence ci-artifacts/issue341/cutover.json
```

回滚不删除五域 schema，也不触碰 source 数据。它先查询旧库的账号和课程表，再把同一个 state 写回 `LEGACY_MONOLITH`，供网关切回已记录的 legacy digest：

```sh
node database/mysql/migrate-five-domain-schemas.mjs \
  --action rollback --admin-user migration_admin --source-schema onlinejudge \
  --cutover-state ci-artifacts/issue341/cutover-state.json \
  --evidence ci-artifacts/issue341/rollback.json
```

这保证旧系统恢复是可逆流量操作，不把删 schema/volume 当作回滚。回滚后可再次执行 `migrate`、`replay`、`verify`；所有结果都必须以新的 source fingerprint 和 evidence 为准。

`--cutover-state` 可以是尚不存在的嵌套部署目录（例如上面的 `ci-artifacts/issue341/`）。控制器会先创建父目录，再以同目录临时文件加 rename 原子发布 JSON；因此首次切换和传入另一 fresh rollback-state 路径的恢复都不会因 CI workspace 的 `ENOENT` 失败，也不会向读取方暴露半写入的流量状态。

## 4. 必须归档的 evidence

`--evidence` JSON 包含源 schema migration 计数/版本、`baseSha`、`testedSha`、迁移 SHA-256、46 表的源/目标行数、CRC、checksum、主键集合差异、59 条可物理检查的 logical-reference orphan 结果、33 条 owner 内 FK 结果、13 张具体可靠运行时表的 owner 计数、Grade/Learning 投影与已应用 replay 计数，以及五账号的 45 项矩阵（20 项本域 DML allow、25 项 foreign/DDL deny）的原始错误。每个 `PASS`（包括 `rollback`）都含完整 45 条 `verification.permissions`；没有五个 runtime password 的操作在写入 PASS evidence 前失败。Secret、DSN 和 JWT 不写入结果。

本仓库的真实 MySQL 回归命令如下；它创建精确命名、带 `--rm` 的 MySQL 8.4 容器和临时目录，并在退出时只删除该容器：

```sh
bash database/tests/verify-five-domain-migration.sh
```

若本地执行环境对单个进程设有很短的时限，四段可在**彼此独立的
disposable MySQL** 中分别重跑，结果仍写入各自的 artifact 目录：

```sh
bash database/tests/verify-five-domain-migration.sh empty-cutover
bash database/tests/verify-five-domain-migration.sh empty-recovery
bash database/tests/verify-five-domain-migration.sh seed
bash database/tests/verify-five-domain-migration.sh bad
```

覆盖路径是：空库迁移、空库切换、旧库回滚、再迁移、投影 replay、带课程/成员/来源成绩夹具的迁移与重复 verify，以及一个 `crs_course.teacher_id` 孤儿数据反例。任何 `FAIL` 或 `BLOCKED` 均为非零退出，不能进入流量切换。
