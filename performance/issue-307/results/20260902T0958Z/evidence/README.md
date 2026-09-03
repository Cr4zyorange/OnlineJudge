# Issue #307 2026-09-02 正式窗口证据

- 单体基线：`78715f21288782a2c7ef1d9c23f933c46569b108`
- 三服务基线：`bb4d83ee7a0891490869960370670a2dd03e9962`
- 机器指纹：`033a722a0f09f91f2525c397c31fa628faa841eed7c8a223751e09a6520a6616`
- 数据集：`issue-307-v1`，SHA-256 `733338e1ba51a64b693b60678eeacaa78a0597f7e2034bba6dc2b09e067885c6`
- 负载：每轮 30 秒预热、120 秒测量、并发 10、请求超时 10 秒。

`../formal/` 保存每个架构、接口和轮次独立生成的窗口声明与数据恢复日志。它们锁定了 #318 的 `ENVIRONMENT_READY` 信号、Docker 就绪、独占窗口、无 HPA/E2E/故障注入/其它压力，以及本轮数据恢复与资源策略证据。

资源与运行时材料：

- `monolith-hard-limits.txt`、`monolith-resource-policy.yml`：单体三个运行容器的实际硬限制和生成策略。
- `three-service-hard-limits.txt`、`three-service-resource-policy.yml`：九个三服务运行工作负载的实际硬限制和生成策略。
- `three-service-image-revisions.txt`：三个服务运行时使用的全 40 位 SHA 与镜像 ID。
- `three-service-gateway-readiness.json`、`docker-daemon.txt`：网关就绪和 Docker daemon 版本/硬件容量。
- `../raw/raw-manifest.json`：18 个压缩原始请求样本的无损归档清单；每项都有压缩前/后 SHA-256 和字节数。

报告的总请求吞吐与 P95 包含失败响应。请以 `report/comparison.md` 中的成功请求吞吐与错误率作为业务容量判断的最低依据。
