# #319 当前 head 的 Kubernetes 配置兼容性补验收

- runtime snapshot SHA: `f11ad9a9aee3467af5100213f01901c3f608713b`
- final synchronized branch head: `2484e53213a9c91aae95bfe6ef8325fc95dd66fd`
- namespace: `issue319-final`（临时 Kind 集群 `oj319-merge`）
- result: PASS — 本轮只验证 #320 合入后、#340 同步前后 `enableServiceLinks` 配置对
  Worker/Grade 启动兼容性的影响；不重复此前完整 HPA/RabbitMQ 负载验收。

## 事实

- 新 namespace 由 `f11ad9` 的镜像部署；没有以 `kubectl set env` 或其它方式覆盖
  `RABBITMQ_PORT`。`raw/core-workloads.yaml` 记录三个核心 Pod template 都为
  `enableServiceLinks: false`。
- `raw/pods.txt` 记录 Assessment API、Assessment Worker、Grade Service 均为
  `1/1 Running`、`restartCount=0`；`raw/service-links.txt` 直接从 Worker/Grade
  容器读取，二者 `RABBITMQ_PORT=absent`，且启动日志中该端口绑定崩溃匹配数均为 0。
- `raw/assessment-api-hpa.yaml` 与 `raw/summary.txt` 记录 HPA 仍以
  `min/max=1/3`、CPU target `60` 生效，核心三 Pod Ready 计数为 3。
- 同步 `origin/dev`（含 #340）后，最终 head 运行
  `python3 -m unittest -v scripts.platform.tests.test_render_disposable_environment.DisposableEnvironmentRendererTest.test_kubernetes_workloads_disable_service_link_environment_injection`
  通过（1 test）；同一 head 渲染 12 个文件，其中 Kubernetes manifest 精确包含 9 个
  `enableServiceLinks: false`。这证明同步没有丢失配置契约，但并不把运行时快照误称为
  `2484e532` 的负载重跑。

## AC 的证据关系

- AC-319-01 至 AC-319-05 的正式 HPA/RabbitMQ 证据仍为
  `docs/过程/测试/Issue-319-HPA实验证据-20260902T161736Z/`：其中 runner、
  deployment 与 head 均为 `cf2979dc2fcfd1bc7e6640a71d6f6864e7de7f1b`，记录
  31,880 次真实业务请求、0 错误、HPA `1→3→1`、已确认 RabbitMQ 不可用窗口及
  Grade 水位追平。
- 本目录只补充 `f11ad9` 对当前 Kubernetes service-link 环境的兼容性：该修复消除了
  自动注入的 `RABBITMQ_PORT=tcp://...` 与 Spring 整数端口绑定之间的碰撞。它不是新的
  HPA/RabbitMQ 负载结果，也不替代上述正式实验。

所有入库原始文件经 Bearer、密码和私钥文本模式扫描，结果为 0。
