# Issue #320 三业务服务真实 E2E 设计

## 1. 状态与目标

- Issue：#320 `[D9-E2E] 复用现有 24 场景验证真实三服务环境`
- 分支：`feature/320-three-service-e2e`
- 基线：`origin/dev@84e017dd466e330cea723441979842d0633c14eb`
- 目标：在一次性、九工作负载、四 schema 的真实三业务服务环境中运行现有 24 条 AUTH/CRS/LAB/HWK/GRD/LRN/shared Playwright 场景，并产生可复演证据。

本设计以 `deploy/platform/workloads.json` 为唯一拓扑正本。平台工作负载固定为 Gateway、Identity、Course、Assessment API、Assessment Worker、Grade、Frontend、RabbitMQ、MySQL；Course 承载 CRS+LRN，Assessment 承载 LAB+HWK，Grade 承载 GRD。Identity 是身份支撑，不增加独立 Learning 服务。

## 2. 当前基线与缺口

`frontend` 已有一个共享 Playwright 配置、八个目标文件和 24 条业务场景。`npm run test:e2e:business:disposable` 当前由 `scripts/test/run-business-e2e-disposable.mjs` 启动单体 Spring Boot、H2、Vite 和 RabbitMQ。该入口能证明场景断言，但不能证明请求经过 Gateway、独立业务服务、Assessment Worker、RabbitMQ 和四个独立 schema。

#318 已提供 `scripts/platform/run_disposable_environment.sh`。它从同一 manifest 渲染 Compose，创建唯一 project、一次性凭据和独立 volumes，顺序运行四个迁移并等待九个 workload Ready，退出时按 project 精确清理。但它在报告 Ready 后立即退出并清理，尚不能在同一受控生命周期内运行 #320 的浏览器场景。

因此 #320 不重写部署拓扑，也不复制 #318 的 Compose 生命周期；它扩展 #318 的受控 Ready 阶段，并把共享 Playwright runner 接入该阶段。

## 3. 采用方案

采用“#318 环境生命周期 + #320 受控 Ready hook + 共享 Playwright 执行器”的单入口方案。

1. `npm run test:e2e:business:disposable` 仍是开发者和 CI 的唯一业务 E2E 命令。
2. 外层 runner 固定当前完整 Git SHA、生成本轮证据目录和随机 loopback 端口，然后调用 #318 环境入口。
3. #318 环境入口完成镜像、迁移、九 workload Ready 后，在环境尚未清理时调用一个参数数组形式的受控命令；不使用 `eval` 或拼接 shell 字符串。
4. 受控命令只访问本轮 Gateway 暴露的同源入口。Gateway 的 `/` 进入 Frontend，页面 `/api/**` 再由同一 Gateway 路由到 Identity、Course、Assessment 或 Grade，因此 `E2E_BASE_URL` 不继承调用者值。
5. Playwright 按现有八个目标、单 worker 顺序运行，精确输出 total/pass/fail/skipped；任一失败、跳过异常或 BLOCKED 均令总命令非零退出。
6. Playwright 完成或任一阶段失败后，#318 的既有 trap 删除且只删除本轮 project 的容器、网络、volume 和运行时 secret 文件；证据目录保留。

该方案保留 #318 的拓扑与清理正本，避免出现第二份 Compose 编排，也避免 `--keep` 后由另一个进程猜测 project/secret 进行清理。

## 4. 环境与数据隔离

每轮运行必须满足以下不变量：

- project 名包含 Issue、短 SHA 与本轮唯一 run ID；禁止固定 container name。
- Gateway 只绑定 `127.0.0.1` 随机端口；不继承外部 `E2E_BASE_URL`，也不连接 FAT/UAT/PRO。
- MySQL、RabbitMQ、Assessment 文件卷和 Compose 网络均由本轮 project 独占。
- MySQL root、四个运行账号、RabbitMQ 和 Identity 签名材料均为本轮随机值，文件权限保持最小化，证据中只记录已脱敏元数据。
- Identity 的课程测试账号仅在该 disposable 环境显式启用；课程、实验、作业、提交和成绩数据由现有场景通过公开 API 创建，不导入共享数据库快照。
- Assessment Worker 使用独立进程、持久任务和 fenced final write。代码评测所需的 Docker sandbox 边界使用仓库已有隔离代理契约；该代理属于测试执行依赖，不改变 `workloads.json` 的九工作负载平台正本。
- `--skip-build`、`--skip-tests` 或保留环境只允许诊断；最终验收命令不得使用这些开关。

## 5. 场景执行与异步收敛

保留以下 24 条现有场景和业务断言，不以新建“代表性 smoke”替代：

- AUTH：9 条。
- CRS：2 条。
- GRD：1 条完整 LAB/HWK → GRD → LRN 生命周期。
- HWK：2 条。
- LAB：4 条。
- LRN：4 条。
- shared：2 条。

场景继续使用 `workers=1`，避免共享课程测试账号的账号锁定、通知已读和动态业务数据互相污染。需要等待异步结果时使用现有或新增的有界轮询，不使用固定 sleep，也不通过 GET 结果接口触发评测、通知或成绩计算。

LAB/HWK 的证据必须从本轮 POST 响应记录 submission/task identity，再在 Assessment Worker 日志和最终结果中按相同 identity 关联。跨服务事件必须记录 eventId/correlationId，并通过 RabbitMQ、目标服务 inbox/outbox 或最终投影证明 at-least-once 消费后的幂等收敛。

## 6. 证据模型

每轮证据位于 `output/issue-320/<full-sha>/<run-id>/`，至少包含：

- `run-manifest.json`：base/head SHA、OS/工具版本、开始/结束时间、唯一 project、loopback endpoint、Compose 文件和证据相对路径。
- `workloads.json` 快照、渲染后的 Compose 库存、四个 migration 结果和九 workload 的最终状态。
- Gateway、Identity、Course、Assessment API、Assessment Worker、Grade、Frontend、RabbitMQ、MySQL 的脱敏日志；失败时同样收集。
- Playwright JUnit、HTML report、trace/screenshot/video（按既有失败策略）以及 `test-summary.json`，精确记录 24 条场景的 total/pass/fail/skipped。
- 至少三组代表场景证据：AUTH→CRS、LAB/HWK→Worker、GRD→LRN。每组记录请求/响应摘要、页面断言、taskId/eventId/correlationId、相关服务日志和最终数据断言。
- `cleanup-summary.json`：本轮 project 的容器、网络和 volumes 均不存在；不得枚举或删除其他 project。

证据禁止包含 access token、Cookie、明文密码、运行时 secret、私有主机凭据、数据库导出或存储内部键。日志收集后执行固定敏感字段扫描，命中即失败。

## 7. 错误处理

- 参数、完整 SHA、端口和工具链不满足要求时，在创建环境前退出 2。
- 镜像、迁移、readiness、种子登录、Playwright、统计、证据或清理任一阶段失败时退出非零，并保留已经产生的诊断。
- `total != 24`、`failed > 0` 或 `skipped > 0` 均不能输出 PASS。
- 无 Docker、镜像无法构建、浏览器缺失或 sandbox 不可用属于 BLOCKED，但仍必须非零退出并写明阶段；不得改为跳过或退回单体/H2。
- 清理失败不能覆盖原始失败；汇总同时记录 primary failure 与 cleanup failure，并保持非零。

## 8. 测试驱动与验收

实现按 Red–Green–Refactor 分批推进：

1. 先扩展 runner/平台契约测试，使其因缺少受控 Ready hook、随机 loopback 入口、24 场景硬门槛和清理证据而按预期失败。
2. 只实现足够通过契约测试的生命周期接口，复跑 #318 原有测试，确保不破坏环境构建、故障注入和清理。
3. 为三服务 proof、精确 Playwright 计数、日志关联和敏感字段扫描编写失败测试，再实现最小执行器。
4. 运行一次真实九工作负载 RED，记录当前 24 场景在服务契约或数据准备上的首个真实缺口；只在设计文档与公共 API 边界内修复测试基础设施。若发现业务服务偏离已提交公共契约，单独记录阻塞，不在 #320 静默修改跨模块接口。
5. 最终运行完整 24 场景，要求 24 passed / 0 failed / 0 skipped，并验证退出后的精确清理。

最低回归包括：平台 Python 契约测试、共享 E2E 契约、前端单测/typecheck/build、三服务 baseline/manifest 契约、runner 受控失败测试和完整九工作负载 Playwright 验收。

## 9. 非目标与公共边界

- 不新增业务需求、页面或公开 API。
- 不改变 AUTH/CRS/LAB/HWK/GRD/LRN DTO、错误码、状态枚举或数据所有权。
- 不恢复独立 Learning 服务、旧单体 backend workload、第五业务 schema 或共享数据库访问。
- 不把 #319 可观测性/HPA、#340 停机恢复、#307 性能对比或 #321 答辩材料并入本 Issue。
- 不降低现有断言、不以读取触发副作用、不把单体/H2 结果声明为三服务通过。

## 10. 完成定义

只有同时满足 AC-320-01 至 AC-320-06，完整命令返回 0，24 条场景全部通过，三组代表证据完整，九 workload/四 migration 可追溯，Assessment Worker 与跨服务事件 identity 可关联，并确认本轮资源精确清理后，Issue 才可进入非草稿 PR 的待审核状态。PR 目标为 `dev`，描述包含 `closes #320`。
