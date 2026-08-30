# D6-MSG：可靠消息运行时（#337）

本文件记录 `#337` 对 [v2 共享契约](D6-D7-五服务共享契约-v2.md) 的运行时实现边界。它不替代 `contracts/v2/asyncapi/events.asyncapi.json` 的 typed payload，也不把现有单体数据库账号误写成五服务的生产账号隔离。

## 交付语义

- 只承诺 **at-least-once**：confirmed publish 之后 broker 仍可能在 consumer ack 前重投。
- 生产者在本地事务内写入业务事实和其拥有的 outbox；RabbitMQ/Learning 不可用不会回滚成功的本地业务事实。
- publisher 用短租约领取 `PENDING`/`RETRY` 行；失败按有限指数退避写回 `RETRY`，到达上限写 `FAILED`，绝不无限静默重试。
- consumer 在同一事务中写 inbox 和本地副作用。重复 `eventId` ACK 为 no-op；旧 aggregateVersion 记录为 `IGNORED_OLD`；版本缺口记录 `GAP` 和 reconciliation 请求，不能猜测投影。
- schema/契约错误为不可重试，直接留下审计 DLQ；可重试错误计数达到上限后也进入 DLQ。受控 replay 按原始 `eventId` 重发，只有 broker confirm 后才标为 `replayed`。

## 当前运行时接口与边界

| 事实拥有者 | 表 | 运行时代码 | 说明 |
| --- | --- | --- | --- |
| Assessment | `assessment_event_outbox` | `AssessmentEventOutboxRepository`、`AssessmentOutboxPublisher` | API-HWK-03 已将 `Homework=PUBLISHED` 与 `assessment.homework.published.v2` outbox 同事务落库。 |
| Course | `course_event_outbox` | 同一 outbox 状态模型的预置 owner table | Course 服务事件接入由对应服务 Issue 消费；#337 不从 Assessment/Learning 跨表代写。 |
| Grade | `grade_event_outbox` | 同一 outbox 状态模型的预置 owner table | Grade 服务事件接入由对应服务 Issue 消费；#337 不从 Assessment/Learning 跨表代写。 |
| Assessment | `assessment_event_inbox` | 预置 owner table | 消费者接入由 Assessment 服务 Issue 消费。 |
| Grade | `grade_event_inbox` | 预置 owner table | 消费者接入由 Grade 服务 Issue 消费。 |
| Learning | `learning_event_inbox`、`learning_event_dead_letter`、`learning_event_reconciliation_request`、`learning_course_member_projection` | `LearningReliableEventConsumer`、`LearningDeadLetterReplayService` | 当前接入 Homework 发布事件；成员投影解析 `COURSE_ACTIVE_STUDENTS`，消息没有学生 roster。 |

迁移文件是 `database/migrations/20260830_01_create_reliable_event_storage.sql`，并同步进 `database/mysql/compose-schema.sql` 与基线历史。真正的五 schema、每服务 runtime account 和数据库授权只能在 #309/#341 的合并设计与迁移完成后启用；在此之前测试环境的单一账号不是隔离证据。

## RabbitMQ 拓扑

当 `onlinejudge.reliability.rabbitmq.enabled=true`：

- `onlinejudge.events.v2` 是 durable topic exchange；routing key 是 `onlinejudge.<eventType>`。
- `onlinejudge.learning.events.v2` 是 durable main queue，消费端使用 manual ack。
- 可重试消息投给 durable `onlinejudge.learning.retry.v2`，TTL 1 秒后回到 homework routing key；这是延迟重投，不是 busy loop。
- 拒绝或不可重试消息由 `onlinejudge.events.dlx.v2` 路由到 durable `onlinejudge.learning.dlq.v2`。数据库 DLQ 还保存 envelope、attempt、失败分类、`eventId` 与 `correlationId`，供审计和重放。

生产发布使用 Rabbit publisher confirms 并标记 persistent delivery；拒绝、超时、网络错误都会抛出 `BrokerUnavailableException`，outbox 不会错误地改成 `PUBLISHED`。

## 观测与故障证据

`ReliabilityMetricsService` 返回 Assessment backlog/PENDING/RETRY/FAILED、最老自动投递消息的 `eventId`/`correlationId`/年龄，以及 Learning 未重放 DLQ 总数和最老 DLQ 的关联 ID。它是供受保护的运行平台接出的结构化指标源，不公开未鉴权的重放 HTTP 入口。

自动化证据包括：

- `AssessmentOutboxPublisherTest`：broker 不可用时保留 outbox，恢复后 confirmed publish；重复 scan 不会再次发布。
- `LearningReliableEventConsumerTest`：同一消息 10 次重投只产生一个通知，版本缺口对账，毒消息隔离后健康消息继续，受控 replay 只在 confirm 后标记。
- `RabbitMqReliabilityConfigurationTest`：durable main/retry/DLQ 声明和延迟路由。
- `scripts/test/verify-reliable-messaging-live.sh`：使用 disposable RabbitMQ 完整执行 confirmed publish、broker pause 的明确失败、恢复后的再次 confirmed publish；日志输出 event/correlation ID。

运行真实 RabbitMQ 验收前，先确认 Docker 能拉取 `rabbitmq:4.1-management`；该镜像不可用时不得把 H2/mock 测试称作 broker 故障验收通过。
