# CRS 课程与教学资源模块开发流程

## 1. 开发定位

CRS 是 AUTH 之后的第二个基础模块，负责课程、章节、资源、成员和公告。LAB、HWK、GRD、LRN 都依赖 CRS 的课程信息、章节信息、课程成员关系和课程教师权限判断。

本模块负责人必须完成 DB 表、后端 API、Service 逻辑、前端页面、权限异常、测试数据和跨模块校验接口。课程成员关系是 CRS 的核心，不允许只依赖前端传入的 `userId` 或 `courseId` 判断权限。

## 2. 详细设计阅读入口

开发前先阅读：

- `docs/最终提交/软件详细设计说明书.md` 的 `3.2 课程与教学资源模块（CRS）`
- `docs/过程/详细设计/CRS-课程与教学资源-详细设计提交稿.md`
- AUTH 的当前用户上下文说明，因为 CRS 所有业务操作都依赖登录用户

## 3. 统一开发顺序

```text
1. 读 CRS 详细设计章节，确认 UI-CRS / API-CRS / SVC-CRS / DB-CRS / TC-CR 编号
2. 建课程、章节、资源、课程成员、公告表和实体
3. 写课程 CRUD 和课程列表/详情接口
4. 写课程成员、章节、资源、公告核心 Service
5. 写课程列表、课程详情和课程管理前端页面
6. 接入 AUTH 当前用户和课程成员权限校验
7. 补非成员访问、学生编辑、教师越权等异常处理
8. 准备课程、成员、章节、资源、公告测试数据
9. 自测 CRS 模块闭环
10. 给 LAB/HWK/GRD/LRN 提供课程校验接口并参加联调
```

## 4. P0 最短交付

CRS 的最短可交付路径是：

```text
教师登录
→ 创建课程
→ 查看自己管理的课程
→ 学生登录
→ 查看可加入课程
→ 加入课程
→ 双方能看到课程详情和成员关系
```

P0 先交付课程表、课程成员表、课程列表接口、课程详情接口和加入课程主流程。章节、资源、公告可以随后补齐，但成员关系必须先准确。

## 5. 数据库与实体

按 DSD 建立以下核心表：

| 表 | 用途 |
| --- | --- |
| 课程表 | 保存课程名称、简介、创建教师、状态、归档信息 |
| 章节表 | 保存课程章节目录、父子关系、排序 |
| 资源表 | 保存课件、资料、文件路径、上传人、可见范围 |
| 课程成员表 | 保存教师、助教、学生等课程内角色和加入状态 |
| 公告表 | 保存课程公告、置顶状态、发布状态 |

课程成员表必须能表达课程内角色和状态，例如学生、教师、助教、待审核、已加入、已移除。后续 LAB/HWK/GRD/LRN 的课程权限都要通过 CRS 校验。

## 6. 后端 API 与 Service

先实现课程基础 API：

| 功能 | 方法与路径 | 权限 |
| --- | --- | --- |
| 创建课程 | `POST /api/v1/courses` | 已登录教师 |
| 查询课程列表 | `GET /api/v1/courses` | 已登录用户 |
| 查询课程详情 | `GET /api/v1/courses/{courseId}` | 课程成员 |
| 编辑课程 | `PUT /api/v1/courses/{courseId}` | 课程教师 |
| 删除或归档课程 | `DELETE /api/v1/courses/{courseId}` | 课程教师 |

随后补齐：

- 章节：创建、编辑、删除、排序、章节树查询
- 加入课程：公开加入、邀请码或审核主流程，首版可先做一种
- 成员管理：成员列表、课程内角色、移除成员
- 资源：上传、下载、删除、资源列表
- 公告：发布、编辑、置顶、删除、课程首页展示

CRS 必须向其他模块提供稳定校验能力：

- 查询课程是否存在
- 查询用户是否为课程成员
- 查询用户是否有课程教师权限
- 查询课程学生名单

这些能力可以通过 Service 客户端或内部接口暴露，但调用方不应直接读 CRS 内部表。

## 7. 前端页面与交互

CRS 前端必须包含：

| 页面 | 完成标准 |
| --- | --- |
| 课程列表页 | 学生看自己加入或可加入课程，教师看自己管理课程，管理员可看全部课程 |
| 课程详情页 | 展示课程信息、章节、资源、公告、成员入口 |
| 课程管理页 | 教师创建、编辑、归档课程 |
| 章节管理页 | 教师创建、编辑、删除、排序章节，前端展示章节树 |
| 资源管理页 | 教师上传、删除资源，学生下载可见资源 |
| 成员管理页 | 教师查看成员、调整课程内角色、移除成员 |
| 公告管理页 | 教师发布、编辑、置顶公告，学生在课程首页查看 |

每个页面必须接真实 API，至少展示加载、成功、失败、空状态。课程列表和课程详情不能只做静态页面。

## 8. 权限、异常与安全

必须覆盖以下边界：

- 未登录不能访问课程接口
- 非课程成员不能查看非公开课程详情
- 学生不能编辑课程、章节、资源和公告
- 教师只能管理自己课程或被授权课程
- 被移除成员不能继续访问课程资源
- 资源下载必须校验课程成员关系

涉及课程数据时，后端必须基于 AUTH 当前用户和 CRS 成员关系校验，不能信任前端传入的用户身份。

## 9. 测试与自测清单

| 测试点 | 验收标准 |
| --- | --- |
| 课程创建 | 教师可创建课程并在列表中看到 |
| 加入课程 | 学生可加入课程，成员表状态正确 |
| 成员权限 | 非成员不能查看课程，学生不能编辑课程 |
| 章节目录 | 章节树展示、排序和删除正确 |
| 资源管理 | 上传、下载、删除均校验课程权限 |
| 公告展示 | 教师发布后学生可在课程页看到 |
| 跨模块接口 | LAB/HWK/GRD/LRN 能查询课程存在、成员关系和教师权限 |

## 10. 对其他模块交付物

CRS 完成后需要交付：

- 课程 DTO 和课程成员 DTO
- 课程内角色枚举和成员状态枚举
- 课程权限校验接口或 `CoursePermissionClient` 使用方式
- 课程学生名单查询方式
- 课程、成员、章节、资源、公告测试数据

完成标准是 LAB、HWK、GRD、LRN 可以基于 CRS 判断课程归属和课程内权限，不再自己维护一套课程成员逻辑。

## 11. 独立服务部署验收

Course 服务使用 `services/course/Dockerfile` 构建为
`onlinejudge/course-service:${GIT_SHA}`，并由 `deploy/docker/compose.yml` 中的
`course-migrations` 先为独立 `oj_course` schema 和最小权限
`oj_course_rw` 账号执行版本化迁移；Course 仅在迁移 job 成功、MySQL 和 RabbitMQ
健康后启动。运行期禁止以 `spring.sql.init` 创建测试 schema，readiness 为
`/actuator/health/readiness`，进程用户固定为非 root 的 `10002:10002`。
Course 的 outbox 固定发布到 `onlinejudge.events.v2`，routing key 固定为
`onlinejudge.<eventType>`；Compose 的 Learning runtime 也必须使用同一 Rabbit
连接。独立 Course API 在 Learning 未启动时保留 `PENDING` 事实，Learning 绑定恢复后
以原 `eventId` 消费 member/snapshot 并推进课程级 watermark，不能用 H2 fixture 代替。

受外部 registry 或 BuildKit frontend 超时影响时，不能跳过镜像或 Compose 验收。
应先运行 `mvn -f services/course/pom.xml -DskipTests package`，再使用
`services/course/Dockerfile.cached-runtime` 和已在本机检查过的、不可变 Java 21
运行时镜像构建同一个 jar，例如：

```bash
GIT_SHA="$(git rev-parse HEAD)"
docker build \
  --build-arg "RUNTIME_BASE=onlinejudge/backend:<known-immutable-sha>" \
  --build-arg "GIT_SHA=$GIT_SHA" \
  -f services/course/Dockerfile.cached-runtime \
  -t "onlinejudge/course-service:$GIT_SHA" .
```

该 fallback 仍必须保留精确 SHA 的 OCI revision、`10002:10002` 非 root 用户和
主 Dockerfile 相同的 Course jar；之后必须运行真实 MySQL 8.4、RabbitMQ 4.1 的
Compose migrations、readiness 和 API 验收，并保留 primary Dockerfile 的原始失败
日志。它只替代不可取得的构建基础层，不替代生产路径。
