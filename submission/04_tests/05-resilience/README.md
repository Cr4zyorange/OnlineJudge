# 05 故障处理 / 恢复（#340）

## 内容

- `scripts/`：矩阵 JSON（7 场景 + AC 映射 + 断言）、验收脚本与其契约测试
  （FINAL_SHA 树副本）。
- `evidence/Issue-340-三服务韧性验收证据.md`：#340 过程验收说明正本副本。
- `ci/`：Actions run 33708861734 的 `issue-340-resilience-33708861734` 原样解压：
  `report.json`（7/7 PASS）、每场景 before/during/recovery/evidence/status、
  `worker-rabbit-recovery-evidence.log`、`query-login-meta.json`、`evidence-scan.json`。

## 最终口径（AC-TESTS-04 后半）

```text
scenarioCount=7  passed=7  failed=0  blocked=0  status=PASS
testedSha=cb53f265ba726c132f541156f41c84a5b18b7d05
runId=20260903T024648Z-2412
environment=isolated #318 disposable Compose: 9 workloads / 4 migrations
```

场景与关键断言（详见 `ci/report.json`）：

| 场景 | AC | 故障/恢复断言（含原始证据文件） |
| --- | --- | --- |
| course-delay | 340-01/02/05 | 投影 gap 种子；随机端口 Course stub 延迟；提交 503；恢复后正常提交，超时请求零事实写入 |
| assessment-api-down | 340-01/02/05 | 目标提交 504；独立 Course 读 200、Grade 读 403；恢复 11s；db-before=0 db-after=0 |
| worker-kill | 340-01/02/03/04 | RUNNING lease fenced；替代 generation 完成一次；source-grade/outbox 计数稳定 |
| grade-down | 340-01/02/04 | 源事实/outbox 持久；事件 `11011534-…` revision=1；恢复 18s；APPLIED/projection=1、duplicate=no-op、DLQ=0 |
| rabbitmq-down | 340-01/02/03/04 | 本地事实提交、outbox 行保留；恢复后同 eventId 精确一次投递 |
| identity-down | 340-01/04/05 | 缓存 JWT 校验 200；新登录/refresh 504；恢复 11s；零域写 |
| duplicate-gap-dlq | 340-03/04/05 | 重复/乱序/gap/毒消息去重、延迟或 DLQ；replay 后 revision 最新、计数精确 |

复现命令：

```bash
bash scripts/test/verify-issue-340-resilience.test.sh              # 无 Docker 契约
bash scripts/test/verify-issue-340-resilience.sh --contract-only    # 矩阵形状
bash scripts/test/verify-issue-340-resilience.sh --bootstrap-318 --output-dir ci-artifacts/issue-340
```

残余风险：disposable 中 MySQL 为单一物理 workload；实验证明逻辑服务边界与恢复
行为，不宣称跨物理数据库故障域。
