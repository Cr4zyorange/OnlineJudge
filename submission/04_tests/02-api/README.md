# 02 公开接口清单与 API 测试（#367）

## 内容

- `code/`：FINAL_SHA 树 `tests/api/` 原样副本——`inventory.json`（接口清单）、
  `mapping.json`（接口 → 测试文件 → @Test 方法）、`coverage-report.json`（覆盖率）、
  `api-coverage.mjs`（提取/校验工具）、`api-coverage.test.mjs`（回归自测）、
  `README.md`（#367 原始说明）。
- `scripts/run-api-coverage-367.sh`：#367 一键 runner（JDK 21/24；Windows 兼容）。

## 最终口径（AC-TESTS-02）

| 服务 | total | mapped | unmapped |
| --- | ---: | ---: | ---: |
| identity | 23 | 23 | 0 |
| course | 42 | 42 | 0 |
| assessment | 27 | 27 | 0 |
| grade | 22 | 22 | 0 |
| gateway | 10 | 10 | 0 |
| 合计 | 124 | 124 | 0 |

来源：#367 PR #373（head `97614ad7`，merge `bb4d83ee`），tested head `27bda936`；
`coverage-report.json` generatedAt `2026-09-02T07:40:10Z`。

## 执行证据

1. `node tests/api/api-coverage.mjs all`：exit 0，124/124 已映射、未映射 0。
2. `node tests/api/api-coverage.test.mjs`：共享路径不合并、Gateway 只映射真实执行
   请求的回归自测 PASS。
3. `node tests/api/api-coverage.mjs gateway-static`：39 条 location、114 个服务端点
   命中归属上游，exit 0。
4. `bash scripts/test/run-api-coverage-367.sh`（#367 tested head）：8 项检查 PASS；
   gateway runtime smoke：services=4 deep-link=pass stream=pass isolation=4/4、
   headers=request-allowlist、status=401/403/404/413/429/502/503/504 retry=off。
5. 服务测试最终口径（#367）：Identity 59/0/2、Course 63/0/10、Assessment 109/0/8、
   Grade 33/0/0 → 264 run / 0 fail / 20 skipped。
6. FINAL_SHA（27eab668）run 33712921299 中 API 覆盖测试类随 backend/服务套件真实
   执行（JUnit 见 `../01-unit-integration/ci/`），例如
   `AssessmentApiCoverageTest` 2、`CourseApiCoverageTest` 6、
   `GradeApiContractTest` 11、`GradeApiAuthenticationTest` 1、
   `GatewayRoutingContractTest` 3（均为 0 failures）。

环境：提取与 runner 在 Windows 10 + Docker Desktop 29.3.1（Linux engine）、JDK
24.0.2、Maven 3.9.16、Node v24.15.0 执行；CI 执行面为 GitHub Actions（JDK 21 /
Node 22）。

## 已知缺口

`output/issue-367/`（Git 忽略）中的原始 runner 控制台日志未入库、Actions 无对应
artifact（见 `../INDEX.md` GAP-01）。替代证据为上述 1–5 的产物与 FINAL CI JUnit。
