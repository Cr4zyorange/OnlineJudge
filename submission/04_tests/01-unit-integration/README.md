# 01 单元 / 集成 / 仓库契约

## 内容

- `code/`：FINAL_SHA（`27eab66891bfbbc21cb39ec96dcbedd6be2fabe2`）树中测试代码副本，
  按仓库相对路径镜像：
  - `code/backend/src/test/`：后端单元/集成测试（`unit`、`integration` 分组）。
  - `code/services/{identity,course,assessment,grade}/src/test/`：四服务测试代码。
  - `code/frontend/tests/unit/`：前端 Vitest 单元测试。
- `ci/`：FINAL_SHA ci-quality-gate run 33712921299 的四个原样 artifact 解压结果：
  `ci-validate-workflows-33712921299`、`ci-backend-gate-33712921299`、
  `ci-contracts-gate-33712921299`、`ci-frontend-gate-33712921299`。zip 原件见
  `../actions/artifacts/`。

## 最终 CI 精确计数（run 33712921299，SHA 27eab668）

计数来源为 artifact 内 JUnit XML 与 `test-summary.txt`；passed = total - failed -
skipped。

| 套件 | files | total | failed/errors | skipped | passed | 原始 XML 目录 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| Workflow 静态校验 | — | 67 checks | 0 | 0 | 67 | `ci/check-result.txt` |
| 后端单元（backend module） | 78 | 474 | 0 | 13 | 461 | `ci/backend/target/surefire-reports/unit/` |
| 后端集成 | 9 | 23 | 0 | 1 | 22 | `ci/backend/target/surefire-reports/integration/` |
| Assessment 服务套件 | 35 | 119 | 0 | 8 | 111 | `ci/services/assessment/target/surefire-reports/` |
| Course 服务套件 | 12 | 70 | 0 | 10 | 60 | `ci/ci-artifacts/backend-gate/course/surefire-reports/` |
| Grade 服务套件 | 14 | 37 | 0 | 0 | 37 | `ci/services/grade/target/surefire-reports/` |
| 契约（consumer） | 6 | 25 | 0 | 0 | 25 | `ci/backend/target/surefire-reports/contract-consumer/` |
| 契约（producer） | 7 | 27 | 0 | 0 | 27 | `ci/backend/target/surefire-reports/contract-producer/` |
| 前端单元（Vitest JUnit） | 1 | 573 | 0 | 0 | 573 | `ci/ci-artifacts/frontend-gate/frontend-unit-junit.xml` |
| 前端 runner 契约（node --test） | — | 3 | 0 | 0 | 3 | `ci/ci-artifacts/frontend-gate/runner-contracts.txt` |

汇总：`unit=p`。执行环境为 GitHub Actions `ubuntu-latest`（JDK 21 / Maven、Node 22 /
npm 10.9.2），运行命令即 `.github/workflows/ci.yml` 中各 job：

```bash
bash scripts/ci/backend-verify.sh "$GITHUB_WORKSPACE"
bash scripts/ci/frontend-verify.sh "$GITHUB_WORKSPACE"
bash scripts/ci/contract-verify.sh "$GITHUB_WORKSPACE" consumer
bash scripts/ci/contract-verify.sh "$GITHUB_WORKSPACE" producer
bash scripts/ci/summarize-tests.sh "$GITHUB_WORKSPACE" ci-artifacts/backend-gate
```

浏览器 E2E 计数在 `../03-e2e/`（run 33712921299 的 Browser job）。

## 与 #367 API 测试的关系

公开接口映射的 API 覆盖测试类（`*ApiCoverageTest`、`GradeApiContractTest`、
`GradeApiAuthenticationTest`、`GatewayRoutingContractTest` 等）随上述套件在
FINAL_SHA 真实执行，JUnit XML 位于本目录 `ci/` 内；映射与覆盖率见 `../02-api/`。
