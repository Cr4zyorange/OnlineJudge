# TST-355 RED/GREEN 记录

> Issue：#355（Course 独立交付，CRS+LRN 落位）。本文件记录新增 LRN 折叠行为的
> RED/GREEN 回放证据，原始日志归档于 `ci-artifacts/issue355-red-green/`。

## 1. 基线 SHA

| 阶段 | SHA | 说明 |
| --- | --- | --- |
| RED（base） | `f948869799e2e561d6cfa2208acaf26627aa1ba1` | #306 三服务基线合并点，尚未包含 #355 实现 |
| GREEN（head） | `670df4979c31d39c5f9418527382108b65fd4528` | #355 全部实现提交后 |

## 2. RED 回放（base 上验证新增行为缺失）

### 2.1 测试级 RED

将 `LrnFoldServiceTest.java`（#355 新增验收测试）单独放入 base 工作副本后执行：

```text
mvn -f services/course/pom.xml -Dtest=LrnFoldServiceTest test
```

结果：**编译失败，BUILD FAILURE**（原始日志：`ci-artifacts/issue355-red-green/red-lrn-test.log`）。

```text
COMPILATION ERROR :
  LrnFoldServiceTest.java:[4,46] 程序包com.onlinejudge.courseservice.learning不存在
  LrnFoldServiceTest.java:[49,13] 找不到符号：LrnEventProjection
[ERROR] 2 errors
```

结论：base 上不存在 Course 的 LRN 投影包，新增测试按预期先失败（RED）。

### 2.2 HTTP 级 RED

打包并独立启动 base 服务（`java -jar --server.port=18083`，H2 默认配置）后探测
（原始日志：`ci-artifacts/issue355-red-green/red-http.log`）：

| 探测端点 | base 结果 | 说明 |
| --- | --- | --- |
| `GET /version` | `500 COURSE_INTERNAL_ERROR` | 版本号端点不存在 |
| `GET /api/v1/notifications` | `500 COURSE_INTERNAL_ERROR` | LRN 通知 API 不存在 |
| `GET /api/v1/learning/tasks` | `500 COURSE_INTERNAL_ERROR` | LRN 任务 API 不存在 |
| `GET /api/v1/reminder-rules` | `500 COURSE_INTERNAL_ERROR` | 提醒规则 API 不存在 |
| `GET /api/v1/courses` | `401 AUTHENTICATION_REQUIRED` | 既有 Course API 正常（基线未被破坏） |

结论：新增端点/行为在 base 上缺失（无处理器返回 500），证明 RED 成立；既有课程 API
保持 401 鉴权语义，说明 RED 范围仅限 #355 增量。

## 3. GREEN 回放（head 上验证行为就绪）

| 验证项 | 命令/证据 | 结果 |
| --- | --- | --- |
| 单元/契约测试 | `mvn -f services/course/pom.xml test` | 54 run / 0 failures / 0 errors / 10 skipped |
| 新增 LRN 折叠测试 | `LrnFoldServiceTest` | 6/6 通过（含幂等、水位线单调、缺口 fail-closed、内部端点） |
| 后端契约套件 | consumer+producer 契约测试 | 41 run / 0 failures |
| 实机验收 | `scripts/test/verify-course-355-live.ps1`（MySQL 8.4 + RabbitMQ 4.1） | `PASS`：迁移 7/7、跨 schema 拒绝、readiness UP、事实幂等 tasks=1/notifications=1/inbox=1、停机保留 retainedFacts=2 |
| v2 契约 | `verify-microservice-contract-v2.mjs` | PASS（4 OpenAPI / 10 AsyncAPI） |
| #306 基线 | `verify-three-service-baseline-306.mjs` + 回归测试 | PASS（9 workloads / 4 migrations / 4 accounts；7/7 用例） |
| 平台 manifest | `validate_workload_manifest.py`（Linux 容器权威执行） | PASS + 单测 20/20 OK |

实机验收完整日志：`ci-artifacts/issue355-course-live-1788232635-104576/`。

## 4. 说明

- RED 阶段因负责人指示（两小时窗口内先实现后验证）未在实现前单独提交；本记录以 base
  工作副本回放新增测试与端点探测，等价证明“功能缺失时行为先失败”。
- base 上的 `500 COURSE_INTERNAL_ERROR` 为 Spring 无处理器时的统一错误包络，用于证明
  端点不存在；head 上同一端点返回文档化契约响应。
- Windows 本机运行 `python -m unittest scripts.platform.tests...` 会因 `st_mode`
  无执行位产生误报，权威结果以 Linux 容器（git archive 保执行位）为准。
