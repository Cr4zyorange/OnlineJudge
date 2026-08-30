# D6-DATA 五域数据所有权与账号隔离契约

> 决策 Issue：#309。实现输入：#338 的五服务 v2 正本
> `docs/开发/D6-D7-五服务共享契约-v2.md` 与 ADR-006。实际迁移、回滚、校验和切换：#341。

## 1. 范围与正本

本文件冻结 Identity、Course、Assessment、Grade、Learning 五域的数据库边界；它不创建 Schema、账号、迁移脚本、回滚脚本或生产 Repository 改造。当前模块化单体仍使用一个物理 MySQL；`database/ownership/*.csv` 描述目标 Schema 的契约，#341 才能实施并验证数据库变更。

`table-ownership.csv` 是现有 46 张业务表的唯一 owner 正本。它保留真实主键、外部 ID、owner 内约束、现有 owner 内索引的保留策略和 #341 的迁移策略。源表集合来自 `database/mysql/compose-schema.sql`，不得凭业务名称新增、遗漏或重复登记表。`service-local-tables.csv` 则预留每个 Schema 自己的 inbox/outbox，并冻结 Grade 的 `t_grade_record` 为来源成绩投影、Learning 的 `learning_course_projection` 为课程最小投影；这些表的创建仍属于 #337/#341。

## 2. 五域、Schema 与运行时账号

| 域 | Schema | 运行时账号 | 唯一事实 | 禁止拥有 |
| --- | --- | --- | --- | --- |
| Identity | `oj_identity` | `oj_identity_rw` | 账号、会话、安全版本、全局角色权限 | 课程、评测、成绩、通知事实 |
| Course | `oj_course` | `oj_course_rw` | 课程、成员、章节、资源、公告与课程授权 | 全局账号、评测、成绩、通知事实 |
| Assessment | `oj_assessment` | `oj_assessment_rw` | LAB/HWK、提交、评测、附件、来源成绩事实 | 课程成员、成绩总评、通知投影 |
| Grade | `oj_grade` | `oj_grade_rw` | 来源成绩投影、成绩、计算、发布、复核与分析 | 评测明细、课程成员、通知 |
| Learning | `oj_learning` | `oj_learning_rw` | Course 最小投影、学习任务、进度、记录、提醒与通知 | 课程/评测/成绩的权威事实 |

账号允许的权限固定为本 Schema 的 `SELECT/INSERT/UPDATE/DELETE`；所有其他四个 Schema、DDL、`GRANT` 与跨 Schema view 均为拒绝。账号矩阵位于 `schema-account-matrix.csv`，不含密码、Token 或真实 `GRANT` 语句。共享物理 MySQL 仅是课程环境的部署折中，不能降低五账号的权限边界；生产环境可以把五个 Schema 移至独立实例，但不改变 owner、ID 或接口/事件契约。

## 3. 禁止跨 Schema 与替代路径

禁止跨 Schema 外键、join、跨服务 Repository、任意 `schema.table` SQL、共享 ORM Entity/Mapper、数据库 view 和以管理员账号规避运行时权限。owner 内的主外键、唯一约束和索引可保留；对 owner 外的关联只保存不带物理 FK 的 ID，并遵循 `cross-domain-references.csv`。

| 消费需求 | 唯一替代路径 | 一致性与失败语义 |
| --- | --- | --- |
| 当前用户、账号状态与全局权限 | Identity JWT/JWKS；安全版本事件 | 业务服务本地验签；未知 key 仅受限刷新，绝不读 Identity 库 |
| 课程存在、成员和教师授权 | Course `/internal/v2/**` 与成员/公告事件 | 依赖不可用返回 `COURSE_AUTHORIZATION_UNAVAILABLE`，不写本地业务事实 |
| 已发布来源成绩 | Assessment source-grade API 与 `assessment.source-grade.changed.v2` | Grade 原子中止本次同步并保留旧投影；不读 Assessment Schema |
| 学习任务与通知 | Course/Assessment/Grade 事件 + Learning 本地投影 | at-least-once、inbox 去重、版本缺口进入对账/DLQ；不反向 join |

逻辑引用均为最终一致：写入命令在授权事实不可用时拒绝，历史事实在删除/禁用事件后保留 ID 和必要快照，不以跨库查询补偿。所有 HTTP 和事件细节以 #338 的 `contracts/v2/` 为准；本文件不重新定义 OpenAPI/AsyncAPI。

## 4. owner 内约束、索引与迁移边界

每张表只能恢复同 owner 的 FK、唯一键和索引；owner 外 `courseId`、`userId`、`sourceId`、`chapterId` 等字段保留为逻辑 ID。#341 必须从源 Schema 的父子依赖顺序复制数据，校验主键集合、owner 内唯一约束、行数、聚合与孤儿逻辑 ID，并通过事件回放重建 Grade/Learning 投影。它不得编辑本清单来逃避验证。

真正的数据库账号创建、空库/升级/重复/失败/回滚、五账号允许/拒绝访问以及切换后禁止跨 Schema 查询，均是 #341 的验收；#309 的可执行验证只证明输入清单完整、一致且不会把第二个 owner 或跨域授权写进契约。

## 5. 可执行契约门禁

```bash
node --test scripts/test/verify-data-ownership-contract.test.mjs
node scripts/ci/verify-data-ownership-contract.mjs
git diff --check
```

门禁从当前 `compose-schema.sql` 读取表和主键，要求 46 张业务表恰有一个五域 owner，五个 Schema/账号一一对应，四个外域均被拒绝，owner 外 FK 被拒绝，cross-domain ledger 必须指向 `contracts/v2/` 替代路径，并以受控 mutation 证明账号越权和 owner 漂移会失败。
