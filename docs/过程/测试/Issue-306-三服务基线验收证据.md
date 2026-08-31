# Issue #306 三业务服务基线验收证据

基线内容 SHA：`921af331e785551107466c8267d5f988436e1d14`（`THREE_SERVICE_BASE_SHA`）。该提交从 `origin/dev` 的 `f2897815ee425cdf08a1ee506d88a6e63d66494e` 建立；本证据文档只发布 SHA 和验收结果，不改变基线内容。

## RED

实现前执行：

```text
node --test scripts/test/verify-three-service-baseline-306.test.mjs
```

结果为 `ERR_MODULE_NOT_FOUND`（`scripts/ci/verify-three-service-baseline-306.mjs` 尚不存在），计数 `tests 1 / pass 0 / fail 1`。该用例先证明三服务语义校验入口缺失。

## GREEN

- `node scripts/ci/verify-three-service-baseline-306.mjs`：`9 workloads, 4 migration jobs, 4 accounts`。
- `node --test scripts/test/verify-three-service-baseline-306.test.mjs`：`tests 5 / pass 5 / fail 0`；反例覆盖独立 Learning OpenAPI、Identity 的 retired audience、LRN 错 owner 和重复 AsyncAPI consumer。
- `node scripts/ci/verify-microservice-contract-v2.mjs`：`4 OpenAPI, 10 AsyncAPI messages, 4 valid fixture(s), 8 incompatible fixture(s), 18 rejecting mutation(s) rejected`。
- `PYTHONDONTWRITEBYTECODE=1 python3 scripts/platform/validate_workload_manifest.py --schema deploy/platform/workload-manifest.schema.json --manifest deploy/platform/workloads.json`：PASS；`python3 -m unittest -v scripts.platform.tests.test_validate_workload_manifest`：`Ran 20 / OK`。
- `bash database/tests/verify-four-domain-baseline.sh`：4 accounts、`12` local-DML allow、`12` foreign-schema deny、`4` DDL deny，并完成 migrate/rollback/repeat。
- Mermaid 正向渲染和破坏性门禁：`verify-three-service-baseline-306-render.test.mjs` 为 `1/1`，`verify-three-service-baseline-306-frontend-gate.test.mjs` 为 `1/1`。
- `OJ_CI_JAVA_MAJOR=25 bash scripts/ci/contract-verify.sh . producer`：生产者契约 `27/27`；同命令 consumer：消费者契约 `25/25`，均通过。运行主机使用 Java 25，因此显式覆盖仓库默认 Java 21 版本检查；实现本身以 release 21 编译。
- 受影响的 Java 边界测试：后端 `19/19`（Course LRN audience、Identity JWKS、Rabbit routing、文档契约）；`services/identity` 的 ServiceTokenController 为 `6/6`，运行 Java 25 时附加 `-DargLine=-Dnet.bytebuddy.experimental=true` 以兼容当前 Byte Buddy。

## 边界说明

没有新增业务服务实现。代码调整仅让既有 Course LRN adapter、Identity audience 和 Rabbit consumer routing 与冻结后的 OpenAPI/AsyncAPI 保持一致；公开 `/api/v1/learning/**` 与 `/api/v1/notifications/**` 继续属于 Course。

## 下游通知

`BASELINE_READY` 通知对象：#355、#357、#356、#339、#317、#318、#319、#320、#340、#307、#321、#304；通知载荷必须包含本文件和上述完整 SHA。
