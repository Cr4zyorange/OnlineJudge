# D3-DATABASE 数据库启动与迁移契约

本文档是 Issue #287 的数据库正本说明。部署消费者只能引用 `database/mysql/compose-schema.sql`、`database/migrations/manifest.txt`、`database/migrations/*.sql` 与 `database/seeds/dev-ci.sql`，不得在 Compose、Kind 或 CI 中维护第二份表结构或测试数据副本。

## 1. 正本与责任边界

- `compose-schema.sql` 是空数据库的最新 MySQL 8.4 快照，并内置与迁移文件 SHA-256 对应的 `schema_migrations` 基线。
- `manifest.txt` 是唯一迁移顺序；文件系统顺序和文件名自然排序都不是迁移顺序。
- `.gitattributes` 将 `database/**/*.sql` 的 checkout 行尾固定为 LF；即使本机启用 `core.autocrlf=true`，迁移文件的原始字节 SHA-256 仍必须与基线一致，runner 不做静默行尾规范化。
- `migrate.sh` 负责已有数据库升级、版本记录、checksum 漂移拒绝和失败迁移定位。
- `database/seeds/dev-ci.sql` 只提供两个禁用登录的数据库健康身份和一门可辨识课程；它不提供真实凭据，不替代 `AuthSeedDataInitializer` 或 `IntDemoDataInitializer` 的可登录账号与完整演示闭环数据。
- `database/seeds/clean-dev-ci.sql` 只清理 `db_ci_*_287` 和 `D3-DATABASE-287` 所属记录。

## 2. 空数据库

Compose DEV 环境设置必需的 MySQL Secret 后，一条命令创建 MySQL、完整表结构、迁移基线和 DEV/CI 测试数据：

```sh
docker compose -f deploy/docker/compose.yml up -d --wait mysql
```

MySQL 官方入口只在空数据目录执行 `/docker-entrypoint-initdb.d`。保留卷重启不会重放 schema 或 seed；这不是迁移机制。Kind/CI 应从上述相同仓库文件创建初始化 ConfigMap/挂载或执行 Job，不得复制 SQL 内容。

## 3. 已有基线数据库

已有 `schema_migrations` 的 Compose 数据库按清单执行全部待处理版本：

```sh
./database/mysql/migrate.sh --adapter compose
```

历史 Compose 卷若已有业务表但没有版本表，脚本会拒绝猜测。操作者必须先按建库时使用的 `compose-schema.sql` 精确确认基线，再显式登记。例如，2026-08-22 快照使用：

```sh
./database/mysql/migrate.sh --adapter compose \
  --baseline-through 20260822_03_create_hwk_submission_attachment.sql
```

当前最新快照但尚无版本表的历史卷，应在确认所有 27 个版本均已包含后基线到 `20260825_02_add_grd_analysis_source_version.sql`。不得用较新的 `--baseline-through` 掩盖缺失表、字段、索引或约束。

Kind 使用同一脚本和 SQL 正本，只替换执行适配器：

```sh
./database/mysql/migrate.sh --adapter kubectl --namespace <namespace> --pod <mysql-pod>
```

## 4. 失败、重跑与回滚边界

- checksum 与已登记值不同、未知清单条目、缺文件、未声明的历史基线或 SQL 执行失败都返回非零退出码。
- SQL 失败会输出 `failed migration: <filename>`；失败版本不会写入 `schema_migrations`。
- MySQL DDL 会隐式提交，不能承诺跨整个迁移文件自动回滚。失败后应保留数据库和原始输出，检查已执行到的 DDL；修复必须使用新的前向迁移或经评审的手工恢复步骤。
- 不得删除持久化卷来规避迁移失败。只有明确属于 DEV/CI 的一次性环境，且数据无需保留时，才可按部署文档重建。
- 已登记迁移不可原地修改；checksum mismatch 应通过新增迁移解决。当前版本表首次引入前的历史文件以本 PR 的 SHA-256 作为冻结基线。

## 5. Seed 限制

`dev-ci.sql` 只允许 DEV/CI。两个 `db_ci_*_287` 身份均为 `DISABLED`，密码字段是不可登录标记而不是秘密。API/E2E 登录继续由应用启动器创建公开演示账号；生产、UAT 或含真实数据的库不得执行本 seed。重复执行 seed 只刷新其自有固定记录；清理使用 `clean-dev-ci.sql`。

## 6. 自动验证

MySQL 8.4 两条路径与重复执行：

```sh
./database/tests/verify-database-bootstrap.sh
```

该脚本使用两个精确命名的临时容器，分别验证空库初始化和 2026-08-22 基线升级，断言关键表、唯一约束、外键、组合索引、27 条 checksum 记录及测试身份/课程；任一步失败均非零，并在退出时只删除本次创建的临时容器。
