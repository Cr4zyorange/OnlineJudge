# Issue #367 后端公开接口清单与 API 测试映射

本目录是 #367 的可审计交付物：以**当前代码**为事实来源（不用旧文档），把
Identity、Course、Assessment、Grade 四个服务与 Gateway 暴露的全部公开 HTTP
接口提取为机器可读清单，并把每个接口映射到至少一条**实际执行**的 API 测试。

## 事实来源

1. Spring Controller 路由：
   - Identity：`services/identity/src/main/java/com/onlinejudge/auth/controller`、
     `com/onlinejudge/authservice/controller`
   - Course：`services/course/src/main/java/com/onlinejudge/courseservice/controller`
   - Assessment：`services/assessment/src/main/java/com/onlinejudge/assessmentservice/controller`
   - Grade：`backend/src/main/java/com/onlinejudge/grd/controller`（Grade 服务 pom 通过
     build-helper add-source 复用该评审过的正本源码）+ `services/grade/.../controller`
2. Gateway 路由：`deploy/gateway/gateway.conf.template`（render 后为 nginx 配置）
3. 公开健康/版本端点：Identity `/api/v1/system/{health,readiness,version}`、
   Course `/version`、Assessment `/health/ready`、Grade `/health/ready`、
   Gateway `/health/{startup,live,ready}`

## 产物

| 文件 | 内容 |
| --- | --- |
| `inventory.json` | 122 个接口：HTTP 方法、路径、服务归属、鉴权分类（PUBLIC/USER/SERVICE）、Controller 文件、Gateway 上游、是否经 Gateway 暴露 |
| `mapping.json` | `方法 + 路径 -> 测试文件 -> @Test 方法名`（只统计 `@Test` 方法体，避免工具方法误映射；按请求动词匹配） |
| `coverage-report.json` | 精确总数、已映射数、未映射数（122 / 122 / 0）与按服务分布 |
| `api-coverage.mjs` | 提取/映射/覆盖率/静态 Gateway 路由校验工具 |
| `README.md` | 本说明 |

## 复现命令

```bash
node tests/api/api-coverage.mjs all            # 重建 inventory/mapping/coverage
node tests/api/api-coverage.mjs gateway-static # 静态 Gateway 路由归属校验
bash scripts/test/run-api-coverage-367.sh      # 一键 runner（JDK 21/24 + 四服务套件 + Gateway smoke）
```

## 接口总数（2026-09-02 执行）

| 服务 | 接口数 | 已映射 | 未映射 |
| --- | ---: | ---: | ---: |
| identity | 23 | 23 | 0 |
| course | 42 | 42 | 0 |
| assessment | 27 | 27 | 0 |
| grade | 21 | 21 | 0 |
| gateway | 9 | 9 | 0 |
| **合计** | **122** | **122** | **0** |

> 说明：`/internal/v2/*` 服务契约与 `/version`、`/health/ready` 等服务端口探针属于
> 服务直连面；Gateway 对 `/internal/v2/*` 统一返回 `GATEWAY_404`，静态校验将其
> 单独建模，不与公开 Gateway 路由混淆。

## 测试映射原则

- 每个接口至少一条成功或契约测试；测试名描述业务行为。
- 负向覆盖：鉴权缺失/拒绝（401/403 + 错误码）、参数校验（400 + 错误码）、
  404/409/冲突等公共边界，禁止只断言 200。
- 新增测试所在位置：
  - `services/identity/src/test/java/com/onlinejudge/auth/AdminPermissionsApiTest.java`
  - `services/course/src/test/java/com/onlinejudge/courseservice/CourseApiCoverageTest.java`
  - `services/assessment/src/test/java/com/onlinejudge/assessmentservice/AssessmentApiCoverageTest.java`
  - `services/grade/src/test/java/com/onlinejudge/gradeservice/GradeApiContractTest.java`
- 本次补齐过程中发现并修复的缺口：
  1. Grade 服务缺少错误码 `@RestControllerAdvice`（业务异常返回 Spring 默认 500），
     新增 `GradeApiExceptionHandler` 恢复冻结契约 `ERR-AUTH-05` / `ERR-GRD-*`。
  2. Gateway 模板未暴露 `POST /api/v1/submissions`（会被 `GATEWAY_404` 拦截），
     模板补充 `location = /api/v1/submissions` → Assessment。
