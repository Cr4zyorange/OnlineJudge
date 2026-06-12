# Docker Compose 部署手册

本文档对应 `DEP-01 Docker Compose 部署配置与部署手册`，目标是提供一套面向教学演示的可复现部署方案。它不是生产级高可用方案，但应满足一键启动、业务链路可验证、数据可持久化。

## 1. 部署拓扑

- `frontend`：Nginx 托管前端静态资源，并将 `/api/` 反向代理到后端。
- `backend`：Spring Boot 后端服务，容器内监听 `8080`。
- `mysql`：MySQL 8 数据库，容器内监听 `3306`，首次创建 `mysql-data` 卷时执行 `database/mysql/compose-schema.sql` 初始化表结构。

默认外部入口：

```text
http://127.0.0.1:8088
```

## 2. 环境准备

需要先安装：

- Docker Engine 24+
- Docker Compose v2

首次使用建议复制环境变量模板：

```bash
cd deploy/docker
cp .env.example .env
```

常用变量如下：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `OJ_HTTP_PORT` | `8088` | 前端统一入口暴露端口 |
| `MYSQL_DATABASE` | `onlinejudge` | 数据库名 |
| `MYSQL_USER` | `onlinejudge` | 业务用户 |
| `MYSQL_PASSWORD` | `onlinejudge` | 业务用户密码 |
| `MYSQL_ROOT_PASSWORD` | `root` | MySQL root 密码 |
| `ONLINEJUDGE_DEMO_DATA_ENABLED` | `true` | 是否写入演示账号和验收数据 |
| `ONLINEJUDGE_EVALUATION_SANDBOX_MODE` | `fake` | 默认评测模式，主线部署建议用 `fake` |

## 3. 一键启动

在仓库根目录执行：

```bash
docker compose -f deploy/docker/compose.yml up -d --build
```

查看运行状态：

```bash
docker compose -f deploy/docker/compose.yml ps
```

正常启动后应能看到 `mysql`、`backend`、`frontend` 三个服务均为运行或健康状态。Compose 项目名固定为 `onlinejudge`，默认持久化卷为：

```text
onlinejudge_mysql-data
onlinejudge_app-data
```

首次启动时，MySQL 会从 `database/mysql/compose-schema.sql` 初始化 schema。后端 Compose profile 不重复执行 `spring.sql.init`，因此保留数据卷重启时不会重复创建索引或覆盖已有数据。

查看日志：

```bash
docker compose -f deploy/docker/compose.yml logs -f
```

## 4. 验证步骤

### 4.1 基础探活

```bash
curl http://127.0.0.1:8088/
curl http://127.0.0.1:8088/api/v1/system/health
```

预期：

- 第一个命令返回前端 HTML。
- 第二个命令返回包含 `"status":"UP"` 的成功响应。

### 4.2 登录验收

默认演示账号：

| 角色 | 账号 | 密码 |
| --- | --- | --- |
| 学生 | `student001` | `Student001@pass` |
| 教师 | `teacher001` | `Teacher001@pass` |
| 管理员 | `admin001` | `Admin001@pass` |

登录接口验证：

```bash
curl -X POST http://127.0.0.1:8088/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"account\":\"student001\",\"password\":\"Student001@pass\"}"
```

预期返回：

- `code` 为 `"0"`
- `data.token` 非空

### 4.3 配置解析验证

如果当前机器暂时不能启动 Docker daemon，仍可先检查 Compose 文件是否能被 Docker Compose 正确解析：

```bash
docker compose -f deploy/docker/compose.yml config --services
```

当前配置预期输出：

```text
mysql
backend
frontend
```

```bash
docker compose -f deploy/docker/compose.yml config --volumes
```

当前配置预期输出：

```text
mysql-data
app-data
```

### 4.4 持久化验证

建议以教师身份创建一条课程公告或课程数据，然后执行：

```bash
docker compose -f deploy/docker/compose.yml restart mysql backend frontend
```

重启后重新访问业务接口，确认数据仍存在。主线要求验证“重启保留数据”，不要求 `down -v` 后保留。

## 5. 常用运维命令

启动：

```bash
docker compose -f deploy/docker/compose.yml up -d --build
```

停止但保留数据卷：

```bash
docker compose -f deploy/docker/compose.yml down
```

停止并删除数据卷：

```bash
docker compose -f deploy/docker/compose.yml down -v
```

说明：`down -v` 会重置 MySQL 和应用持久数据。

重启：

```bash
docker compose -f deploy/docker/compose.yml restart mysql backend frontend
```

## 6. 增强评测模式

主线 Compose 默认使用：

```text
ONLINEJUDGE_EVALUATION_SANDBOX_MODE=fake
```

这样可以确保不依赖宿主机 Docker Socket，也能完成部署验收。

如果需要验证 LAB/HWK 的真实 Docker 评测链路，再执行增强覆盖：

```bash
docker compose \
  -f deploy/docker/compose.yml \
  -f deploy/docker/compose.eval.yml \
  up -d --build
```

增强模式会额外挂载：

```text
/var/run/docker.sock
```

如果宿主环境不允许挂载 Docker Socket，这不应判定为主线部署失败，只表示增强评测能力未启用。

## 7. 与本地开发的区别

- 本地开发默认是 `Vite + Spring Boot + H2`。
- Docker 部署默认是 `Nginx + Spring Boot + MySQL`。
- Docker 部署的 MySQL schema 由数据库容器首次初始化；本地开发和自动化测试仍使用 Spring Boot/H2 的测试 schema。
- 自动化测试继续使用 H2；教学部署运行时改用 MySQL。

## 8. 常见问题

### 8.1 首次启动较慢

首次构建镜像和初始化数据库会耗时更长。优先看：

```bash
docker compose -f deploy/docker/compose.yml logs -f mysql backend frontend
```

### 8.2 端口被占用

如果 `8088` 被占用，修改 `deploy/docker/.env` 中的 `OJ_HTTP_PORT` 后重新启动。

### 8.3 Docker daemon 未启动

如果看到类似以下错误：

```text
failed to connect to the docker API
```

先启动 Docker Desktop 或 Docker Engine，再重新执行启动命令。该错误表示宿主机 Docker 服务不可用，不表示 Compose 文件语法错误。

### 8.4 MySQL 未就绪导致后端失败

Compose 已配置 `depends_on` 和 healthcheck。如果仍然失败，先看：

```bash
docker compose -f deploy/docker/compose.yml logs mysql
docker compose -f deploy/docker/compose.yml logs backend
```

### 8.5 增强评测模式不可用

如果增强模式报 Docker 权限或 Socket 挂载错误，请确认宿主机 Docker 已启动，且当前环境允许将 `/var/run/docker.sock` 挂入后端容器。
