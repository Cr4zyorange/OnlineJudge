# Issue #340 三服务韧性验收证据

## 范围

本证据以 `scripts/test/issue-340-resilience-matrix.json` 为唯一场景清单，覆盖冻结的 `AC-340-01` ～ `AC-340-05`：Course 授权延迟、Assessment API 停机、Worker kill、Grade 停机、RabbitMQ 停机、Identity 停机，以及重复/乱序/缺口/DLQ。

每个场景必须分别记录故障前、故障中、恢复后三段断言；报告必须保留 `taskId`、`eventId`、`revision`、outbox/inbox/DLQ 状态或明确记录不适用原因。报告只允许脱敏内容，并披露共享 MySQL 的物理单点风险。

## RED

实现前执行：

```text
bash scripts/test/verify-issue-340-resilience.test.sh
```

结果为 `FAIL: missing runner: scripts/test/verify-issue-340-resilience.sh`，证明 #340 的统一执行入口尚不存在。

## GREEN：契约与本地可执行测试

```text
bash scripts/test/verify-issue-340-resilience.test.sh
```

结果：`PASS`。

```text
bash scripts/test/verify-issue-340-resilience.sh --contract-only --output-dir <evidence-dir>
```

结果：`RESILIENCE_MATRIX_PASS issue=#340 scenarios=7 passed=7 execution=contract-only`。该模式只证明矩阵字段、AC 覆盖、三段断言和脱敏报告格式，不宣称真实停机已完成。

已在 Java 24 本地复跑的可靠性测试：

| 范围 | 命令 | 结果 |
| --- | --- | --- |
| Course 授权延迟/零写入 | `mvn -B -ntp test -DargLine=-Dnet.bytebuddy.experimental=true -Dtest=LabCourseProjectionFallbackTest,HomeworkCourseProjectionFallbackTest` | 5/5 通过 |
| Assessment Worker、投影、Rabbit 契约、Identity JWKS | `mvn -B -ntp test -DargLine=-Dnet.bytebuddy.experimental=true -Dtest=JwksCacheRefreshTest,WorkerAndProjectionReliabilityTest,RabbitConsumerRecoveryContractTest` | 5/5 通过 |
| Grade 来源成绩投影/缺口恢复 | `mvn -B -ntp test -DargLine=-javaagent:<byte-buddy-agent.jar> -Dnet.bytebuddy.experimental=true -Dtest=SourceGradeProjectionServiceTest,SourceGradeReconciliationWorkerTest` | 5/5 通过 |
| Course/Learning 重复、缺口、DLQ、监听器 | `mvn -B -ntp test -DargLine=-Dnet.bytebuddy.experimental=true -Dtest=LearningReliableEventConsumerTest,RabbitMqLearningReliableListenerTest` | 12/12 通过 |

## 真实环境执行

GitHub Actions 的 `.github/workflows/issue-340-resilience.yml` 提供同一入口的真实执行：

```text
bash scripts/test/verify-issue-340-resilience.sh --bootstrap-318 --output-dir ci-artifacts/issue-340
```

该命令先构建并启动 #318 的 9 workload/4 migration 隔离 Compose 环境，再执行停机、恢复、数据库计数和健康探针，最后生成 `report.json` 并上传证据。成功通知格式为：

```text
RESILIENCE_MATRIX_PASS issue=#340 scenarios=7 passed=7 execution=live sha=<sha> report=<path>
```

Assessment 停机场景的独立 Grade 查询探针使用 `GET /api/v1/courses/1/grade-items`。该接口只读取 Grade 本地成绩项并执行课程权限判断，不会读取 Assessment 来源成绩；因此不会把 Grade 对 Assessment 的正常来源依赖误报成服务停机故障。Course 查询仍使用 `GET /api/v1/courses`，提交目标使用 Assessment 的写入路径，三者的状态与数据库计数分别记录。

本工作树当前主机的 Docker Desktop 未向 WSL 集成（`docker info` 返回 “Docker could not be found in this WSL 2 distro”），因此本地没有伪造 `execution=live` 结果；真实停机证据以 Actions artifact 为准。运行失败或缺少 Docker 时，脚本退出非零，不会把 BLOCKED 误判为 PASS。

## 边界披露

测试证明四个逻辑 schema/运行账号和九 workload 的隔离、事实/outbox/inbox 幂等与恢复；#318 环境仍使用一个 MySQL workload，物理数据库单点不在本 Issue 的修复范围内，已在 `report.json` 中显式披露。
