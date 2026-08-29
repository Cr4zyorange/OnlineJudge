# D7-GATEWAY 路由切流与回滚

## 目标

前端仍只访问既有 `/api` 基址。Nginx 按公开路径将流量路由至 AUTH、CRS、Assessment、Learning & Grade 四个逻辑服务；每项默认保持 `backend:8080`，因此服务尚未切换时原单体链路不变。

## 路由与安全边界

| 服务 | 路径 |
| --- | --- |
| AUTH | `/api/v1/auth/**`、`/api/v1/users/me/**`、`/api/v1/admin/**` |
| CRS | `/api/v1/courses/**`（实验、作业、成绩子路径除外）、`/api/v1/chapters/**` |
| Assessment | `/api/v1/labs/**`、`/api/v1/homeworks/**`、`/api/v1/submissions/**`、`/api/v1/evaluations/**`，以及课程下的实验/作业路径 |
| Learning & Grade | `/api/v1/learning/**`、`/api/v1/notifications/**`、`/api/v1/reminder-rules/**`，课程下全部成绩路径，以及 `/api/v1/grade-items/**`、`/api/v1/grade-records/**`、`/api/v1/course-grade-summaries/**`、`/api/v1/grade-review-requests/**` |

课程下的实验、作业和成绩规则优先于通用课程路由。网关仅转发浏览器携带的 `Authorization`；会在代理前清空 `X-User-*`、`X-Permissions`、`X-Course-Ids` 和 `X-Manageable-Course-Ids`，不伪造内部身份主体。未冻结的服务间主体契约仍由 #310 管理。

精确根路径和子路径均被覆盖，例如 `/api/v1/courses` 与 `/api/v1/courses/**`、`/api/v1/homeworks` 与 `/api/v1/homeworks/**`。请求体上限保持 55MB。普通代理的连接、读取、发送超时为 5s、60s、60s；Assessment 上传/提交读取与发送超时为 300s。代理重试显式关闭，避免非幂等上传或提交被重复发送。下游 401、403、404 保持业务响应；网关连接失败返回 `GATEWAY_502`，超时返回 `GATEWAY_504`，不暴露内部地址、堆栈或凭据。

## 切流与回滚

运行目录默认是 `tmp/gateway-runtime`，其中 `targets.env` 是当前已选择的四个上游，`targets.previous.env` 是本次切换前快照。每次只允许改变一个服务：

```powershell
./scripts/gateway/switch-gateway-target.sh --mode compose --service auth --target auth-service:8081
./scripts/gateway/switch-gateway-target.sh --mode compose --service auth --target backend:8080
```

可用服务名为 `auth`、`crs`、`assessment`、`learning-grade`，目标必须为小写 `host:port`。脚本会渲染 Nginx 配置、仅重建 Compose 的 `frontend` 或滚动重启 Kind 的 `frontend`、执行探针与受保护冒烟请求。任一后置检查失败时，脚本恢复本次前快照并再次验证；验证成功退出码为 1，恢复也无法验证时退出码为 2。

真实冒烟验证需要显式提供会话，且不会把 Bearer 输出到日志：

```powershell
$env:GATEWAY_BASE = 'http://127.0.0.1:8088'
$env:GATEWAY_BEARER_TOKEN = '<temporary-session-token>'
$env:GATEWAY_SMOKE_PATH = '/api/v1/auth/me'
./scripts/gateway/verify-gateway.sh
```

## Compose 与 Kind

`deploy/docker/compose.gateway.yml` 将生成的 `default.conf` 只读挂载到现有 frontend。Kind 在 `k8s-deploy.sh` 中从同一模板生成 `gateway-config` ConfigMap，并以 `subPath` 挂载到 frontend。默认配置也随 frontend 镜像交付，因此未启用覆盖文件时继续代理到单体后端。

## 验证

先执行可重复的静态与脚本验证：

```powershell
bash scripts/gateway/tests/render-gateway-config.test.sh
bash scripts/gateway/tests/gateway-default-config.test.sh
bash scripts/gateway/tests/gateway-routing-contract.test.sh
bash scripts/gateway/tests/switch-gateway-target.test.sh
bash scripts/gateway/tests/verify-gateway.test.sh
bash scripts/gateway/tests/kind-gateway-config.test.sh
bash scripts/gateway/tests/gateway-runtime.test.sh
```

随后在 Docker 引擎可用且各独立服务镜像/服务已交付时运行 Compose、Kind 和真实四服务切流冒烟。每次验证须在 `output/test/issue-317/README.md` 记录环境、基线 SHA、被测 SHA、命令、通过/失败/跳过计数、退出码与原始日志位置。
