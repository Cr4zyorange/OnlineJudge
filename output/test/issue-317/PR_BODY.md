## 目标

基于 #306 已合入 `dev` 的三业务服务架构，为 Identity、Course（CRS+LRN）、Assessment
（LAB+HWK）和 Grade 提供独立统一入口、零信任请求边界与单上游故障隔离。

closes #317

## 本次收敛

- Learning、通知与提醒全部路由至 Course，不保留独立 Learning upstream。
- LAB/HWK 路由至 Assessment，成绩路由至 Grade，身份路由至 Identity。
- renderer、Compose、Kind、切流/回滚和运行时 fixture 统一为四 upstream 模型。
- 保留伪造身份 Header 过滤、Bearer/request ID 透传、无代理重试和脱敏错误。
- 增加深链、查询串、Range 下载、流式响应及四上游逐一停机隔离验证。

## 已验证

- Gateway 静态、渲染、切流、部署与 Docker 运行时测试通过；运行时结果：
  `services=4 deep-link=pass stream=pass isolation=4/4 headers=request-allowlist status=401/403/404/413/429/502/503/504 retry=off`。
- 三服务共享契约通过：4 OpenAPI、10 AsyncAPI messages、4 正例、8 反例、18 mutations rejected。
- workload manifest：9 workloads、4 ordered migration jobs。
- 后端：503 tests、0 failures、0 errors、14 skipped；Gateway/Compose 定向 16/16 通过。
- 前端：54 test files、566 tests 通过；类型检查和生产构建通过。
- Compose、Kubernetes、Kind 与 `git diff --check` 通过。

## 当前验收状态

PR 保持 Draft。AC-317-01 已由自动化完整覆盖；AC-317-02 至 AC-317-06 的 Gateway disposable
部分已覆盖，但 #355、#357、#356、#339 尚未全部提供可部署 Head，#318 最终环境也未完成。
因此四类真实 upstream smoke、Course/Grade 独立验签、真实逐服务停机及浏览器主链尚未计为通过，
不会提前发布 `READY_FOR_INTEGRATION`。

详细证据见 `output/test/issue-317/README.md`。
