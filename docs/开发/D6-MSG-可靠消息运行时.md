# D6-MSG：可靠消息运行时（#337）

本文件记录 `#337` 对 [v2 共享契约](D6-D7-五服务共享契约-v2.md) 的运行时实现边界。它不替代 `contracts/v2/asyncapi/events.asyncapi.json` 的 typed payload，也不把现有单体数据库账号误写成五服务的生产账号隔离。

## 交付语义

- 只承诺 **at-least-once**：confirmed publish 之后 broker 仍可能在 consumer ack 前重投。
- 生产者在本地事务内写入业务事实和其拥有的 outbox；RabbitMQ/Learning 不可用不会回滚成功的本地业务事实。
- publisher 用短租约领取 `PENDING`/`RETRY` 行或原 owner 已失效的 `IN_FLIGHT` 行；失败按有限指数退避写回 `RETRY`，到达上限写 `FAILED`，绝不无限静默重试。过期 owner 不可回写。
- consumer 在同一事务中写 inbox 和本地副作用。重复 `eventId` ACK 为 no-op；旧 aggregateVersion 记录为 `IGNORED_OLD`；版本缺口或尚未就绪的成员投影会持久化原 envelope 到 deferred state machine，再由 reconciliation worker 收敛，不能 ACK 后永久遗忘。
- schema/契约错误为不可重试，直接留下审计 DLQ；可重试错误计数达到上限后也进入 DLQ。受控 replay 按原始 `eventId` 重发，只有 broker confirm 后才标为 `replayed`。

## 当前运行时接口与边界

| 事实拥有者 | 表 | 运行时代码 | 说明 |
| --- | --- | --- | --- |
| Assessment | `assessment_event_outbox` | `AssessmentEventOutboxRepository`、`AssessmentOutboxPublisher` | API-HWK-03 已将 `Homework=PUBLISHED` 与 `assessment.homework.published.v2` outbox 同事务落库。 |
| Course | `course_event_outbox`、`course_membership_reconciliation_checkpoint` | `CourseEventOutboxRepository`、`CourseMembershipBootstrapper` | Course 以本地事务写 membership snapshot/change outbox；checkpoint 由 Course 定时扫描并原子推进下一完整 snapshot，不读取或同步回调 Learning。 |
| Grade | `grade_event_outbox` | 同一 outbox 状态模型的预置 owner table | Grade 服务事件接入由对应服务 Issue 消费；#337 不从 Assessment/Learning 跨表代写。 |
| Assessment | `assessment_event_inbox` | 预置 owner table | 消费者接入由 Assessment 服务 Issue 消费。 |
| Grade | `grade_event_inbox` | 预置 owner table | 消费者接入由 Grade 服务 Issue 消费。 |
| Learning | `learning_event_inbox`、`learning_event_dead_letter`、`learning_event_reconciliation_request`、`learning_deferred_event`、`learning_course_member_projection`、`learning_course_membership_watermark` | `LearningReliableEventConsumer`、`LearningCourseMemberChangedHandler`、`LearningCourseMembershipSnapshotHandler`、`LearningReconciliationWorker`、`LearningDeadLetterReplayService` | `course.member.changed.v2` 仅更新单成员投影；`course.membership.snapshot.v2` 原子替换完整 roster 并推进课程级 watermark。Homework 在水位不存在或 gap 未追平时保持 durable deferred，追平后 worker 按原 eventId 只收敛一次，消息没有学生 roster。 |

迁移文件是 `database/migrations/20260830_01_create_reliable_event_storage.sql`、`20260831_02_create_learning_membership_watermark.sql` 与 `20260831_03_create_course_membership_reconciliation_checkpoint.sql`，并同步进 `database/mysql/compose-schema.sql` 与基线历史。真正的五 schema、每服务 runtime account 和数据库授权只能在 #309/#341 的合并设计与迁移完成后启用；在此之前测试环境的单一账号不是隔离证据。

## RabbitMQ 拓扑

当 `onlinejudge.reliability.rabbitmq.enabled=true`：

- `onlinejudge.events.v2` 是 durable topic exchange；routing key 是 `onlinejudge.<eventType>`。
- `onlinejudge.learning.events.v2` 是 durable main queue，消费端使用 manual ack。
- 可重试消息投给 durable `onlinejudge.learning.retry.v2`，TTL 1 秒后回到 homework routing key；retry copy 获 publisher confirm 前绝不 ACK 原消息。这是延迟重投，不是 busy loop。
- 拒绝或不可重试消息由 `onlinejudge.events.dlx.v2` 路由到 durable `onlinejudge.learning.dlq.v2`。数据库 DLQ 还保存 envelope、attempt、失败分类、`eventId` 与 `correlationId`，供审计和重放。

生产发布使用 Rabbit publisher confirms 并标记 persistent delivery；拒绝、超时、网络错误都会抛出 `BrokerUnavailableException`，outbox 不会错误地改成 `PUBLISHED`。

## 观测与故障证据

`ReliabilityMetricsService` 返回 Assessment backlog/PENDING/RETRY/FAILED、最老自动投递消息的 `eventId`/`correlationId`/年龄，以及 Learning 未重放 DLQ 和未收敛 deferred 事件的总数、最老关联 ID。它是供受保护的运行平台接出的结构化指标源，不公开未鉴权的重放 HTTP 入口。

自动化证据包括：

- `AssessmentOutboxPublisherTest`：broker 不可用时保留 outbox，恢复后 confirmed publish；重复 scan 不会再次发布。
- `LearningReliableEventConsumerTest`：同一消息 10 次重投只产生一个通知，版本缺口对账，毒消息隔离后健康消息继续，受控 replay 只在 confirm 后标记。
- `RabbitMqReliabilityConfigurationTest`：durable main/retry/DLQ 声明和延迟路由。
- `scripts/test/verify-reliable-messaging-live.sh`：使用 disposable RabbitMQ 完整执行 confirmed publish、broker pause 的明确失败、恢复后的再次 confirmed publish；日志输出 event/correlation ID。
- `scripts/test/verify-course-membership-reliable-live.sh`：使用 disposable MySQL/Rabbit 覆盖无历史 outbox 的存量 Course bootstrap，以及旧 roster 已 `PUBLISHED` 后 Learning 投影清空时的 Course checkpoint 对账。后者只写 `vN+1` 完整 snapshot；重复 trigger 不产生第二个 outbox 副作用，Learning 以该完整替换事实恢复 watermark 并按原 homework eventId 一次收敛。

运行真实 RabbitMQ 验收前，先确认 Docker 能拉取 `rabbitmq:4.1-management`；该镜像不可用时不得把 H2/mock 测试称作 broker 故障验收通过。
