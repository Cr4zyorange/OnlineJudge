# OnlineJudgeForSE

## 本地启动

后端默认监听 `8080`，前端 Vite 默认监听 `5173`，前端已通过 `frontend/vite.config.ts` 将 `/api` 代理到 `http://127.0.0.1:8080`。

### 1. 一键启动

仓库提供本地开发一键启动脚本，会同时启动 Spring Boot 后端和 Vite 前端；首次运行时如果 `frontend/node_modules` 不存在，会先执行前端依赖安装。

```bash
./scripts/dev/start-dev.sh
```

启动完成后访问：

```text
http://127.0.0.1:5173/
```

在脚本所在终端按 `Ctrl+C` 会同时停止前端和后端子进程。

### 2. 手动启动后端

```bash
cd backend
mvn spring-boot:run
```

后端默认使用本地 H2 数据库文件 `backend/data/onlinejudge`，并执行 `backend/src/main/resources/application.properties` 中配置的初始化脚本。演示数据初始化默认开启。

### 3. 手动启动前端

另开一个终端：

```bash
cd frontend
npm install
npm run dev -- --host 127.0.0.1
```

启动完成后访问：

```text
http://127.0.0.1:5173/
```

### 4. 演示账号

| 角色 | 账号 | 密码 |
| --- | --- | --- |
| 学生 | `student001` | `Student001@pass` |
| 教师 | `teacher001` | `Teacher001@pass` |
| 管理员 | `admin001` | `Admin001@pass` |

### 5. 快速验证

```bash
curl -i http://127.0.0.1:5173/
curl -i -X POST http://127.0.0.1:5173/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  --data '{"account":"student001","password":"Student001@pass"}'
```

第一个请求应返回前端页面，第二个请求应返回 `code: "0"` 和登录 token。

## 代码目录组织结构

本目录结构基于 `docs/最终提交/软件详细设计说明书.md` 中的系统设计建立：系统采用前后端分离架构，前端为 Vue3 + TypeScript，后端为 Spring Boot，数据库为 MySQL；实现边界按 AUTH、CRS、LRN、LAB、HWK、GRD 六个子系统拆分，跨模块复用能力集中放在 `common` 和 `integration` 下。

正式开发前先阅读 `docs/开发/00-基础设施开发约定.md`。该文档说明了当前仓库已提供的统一登录态、课程权限客户端、评测/通知/来源成绩/文件存储契约，以及前端统一请求层；各模块不要重复定义这些基础对象。

```text
.
├── backend/                                      # Spring Boot 后端工程
│   └── src/
│       ├── main/
│       │   ├── java/com/onlinejudge/
│       │   │   ├── auth/                        # AUTH 用户权限与平台安全模块
│       │   │   │   ├── controller/              # 登录、注册、用户、角色、权限、审计等 REST API 入口
│       │   │   │   ├── service/                 # AuthService、UserService、RoleService、PermissionService 等业务服务
│       │   │   │   ├── mapper/                  # AUTH 模块数据访问接口
│       │   │   │   └── domain/                  # 用户、角色、权限、会话、审计日志等实体、DTO、VO、枚举
│       │   │   ├── crs/                         # CRS 课程与教学资源模块
│       │   │   │   ├── controller/              # 课程、章节、资源、成员、公告等 REST API 入口
│       │   │   │   ├── service/                 # CourseService、ChapterService、ResourceService、MemberService 等业务服务
│       │   │   │   ├── mapper/                  # CRS 模块数据访问接口
│       │   │   │   └── domain/                  # 课程、章节、资源、课程成员、公告等实体、DTO、VO、枚举
│       │   │   ├── lrn/                         # LRN 学习过程与通知提醒模块
│       │   │   │   ├── controller/              # 学习任务、学习进度、学习行为、通知、提醒规则 API 入口
│       │   │   │   ├── service/                 # LearningTaskService、NotificationService、ReminderRuleService 等业务服务
│       │   │   │   ├── mapper/                  # LRN 模块数据访问接口
│       │   │   │   └── domain/                  # 学习任务、进度、记录、通知、提醒规则等实体、DTO、VO、枚举
│       │   │   ├── lab/                         # LAB 实训实验模块
│       │   │   │   ├── controller/              # 实验、提交、评测、报告、评分、统计、测试用例 API 入口
│       │   │   │   ├── service/                 # LabExperimentService、LabEvaluationService、LabScoreService 等业务服务
│       │   │   │   ├── mapper/                  # LAB 模块数据访问接口
│       │   │   │   └── domain/                  # 实验、测试用例、提交、评测、报告、评分等实体、DTO、VO、枚举
│       │   │   ├── hwk/                         # HWK 作业与自动评测模块
│       │   │   │   ├── controller/              # 作业、题目、提交、评测、批阅、统计 API 入口
│       │   │   │   ├── service/                 # HomeworkService、HomeworkEvaluationService、HomeworkReviewService 等业务服务
│       │   │   │   ├── mapper/                  # HWK 模块数据访问接口
│       │   │   │   └── domain/                  # 作业、题目、测试用例、提交、评测、批阅日志等实体、DTO、VO、枚举
│       │   │   ├── grd/                         # GRD 成绩评价与教学分析模块
│       │   │   │   ├── controller/              # 成绩项、成绩同步、成绩发布、异议复核、教学分析 API 入口
│       │   │   │   ├── service/                 # GradeItemService、GradeCalculationService、GradeAnalysisService 等业务服务
│       │   │   │   ├── mapper/                  # GRD 模块数据访问接口
│       │   │   │   └── domain/                  # 成绩项、成绩记录、总评、发布记录、复核申请、分析快照等实体、DTO、VO、枚举
│       │   │   ├── common/                      # 全局复用基础设施
│       │   │   │   ├── config/                  # Spring、MyBatis、跨域、异步任务、对象存储等公共配置
│       │   │   │   ├── security/                # 认证上下文、JWT/会话解析、统一权限拦截和安全工具
│       │   │   │   ├── web/                     # 统一响应结构、分页参数、请求上下文和通用 Controller 支撑
│       │   │   │   ├── exception/               # 统一异常、错误码、异常处理器和错误响应转换
│       │   │   │   ├── storage/                 # FileStorageService 文件存储抽象及本地/对象存储实现
│       │   │   │   ├── event/                   # NotificationEventPublisher、SourceGradePublisher 等业务事件抽象
│       │   │   │   └── evaluation/              # EvaluationTask、Evaluator、SandboxExecutor、EvaluationResult 共享评测抽象
│       │   │   └── integration/                 # 跨模块代理与客户端
│       │   │       ├── auth/                    # 调用 AUTH 能力的认证、角色、权限客户端
│       │   │       ├── course/                  # CoursePermissionClient 及课程成员、课程权限查询客户端
│       │   │       ├── notification/            # 调用 LRN 通知事件接口的客户端
│       │   │       └── grade/                   # 调用 GRD 来源成绩同步接口的客户端
│       │   └── resources/
│       │       ├── mapper/
│       │       │   ├── auth/                    # AUTH MyBatis XML 映射
│       │       │   ├── crs/                     # CRS MyBatis XML 映射
│       │       │   ├── lrn/                     # LRN MyBatis XML 映射
│       │       │   ├── lab/                     # LAB MyBatis XML 映射
│       │       │   ├── hwk/                     # HWK MyBatis XML 映射
│       │       │   └── grd/                     # GRD MyBatis XML 映射
│       │       ├── static/                      # 后端静态资源占位
│       │       └── templates/                   # 后端模板资源占位
│       └── test/java/com/onlinejudge/
│           ├── auth/                            # AUTH 单元测试和接口测试
│           ├── crs/                             # CRS 单元测试和接口测试
│           ├── lrn/                             # LRN 单元测试和接口测试
│           ├── lab/                             # LAB 单元测试、评测与文件服务 Mock 测试
│           ├── hwk/                             # HWK 单元测试、自动评测与批阅测试
│           └── grd/                             # GRD 单元测试、成绩计算和发布复核测试
├── frontend/                                    # Vue3 + TypeScript 前端工程
│   ├── src/
│   │   ├── app/                                 # 应用入口、全局 Provider、根组件挂载
│   │   ├── assets/                              # 图片、样式、字体等静态资源
│   │   ├── components/
│   │   │   ├── common/                          # 表格、表单、上传、分页、状态标签等通用组件
│   │   │   └── layout/                          # 登录后框架、侧边栏、顶部导航、角色菜单布局
│   │   ├── router/                              # Vue Router 路由、鉴权守卫和模块页面注册
│   │   ├── stores/                              # Pinia 状态管理，保存用户、权限、课程上下文、通知状态等
│   │   ├── api/
│   │   │   ├── auth/                            # AUTH 接口封装
│   │   │   ├── crs/                             # CRS 接口封装
│   │   │   ├── lrn/                             # LRN 接口封装
│   │   │   ├── lab/                             # LAB 接口封装
│   │   │   ├── hwk/                             # HWK 接口封装
│   │   │   └── grd/                             # GRD 接口封装
│   │   ├── views/
│   │   │   ├── auth/                            # 登录、注册、个人资料、权限提示、用户角色管理页面
│   │   │   ├── crs/                             # 课程列表、课程详情、章节、资源、成员、公告管理页面
│   │   │   ├── lrn/                             # 学习任务、学习进度、行为仪表盘、通知中心、提醒规则页面
│   │   │   ├── lab/                             # 实验列表、实验详情、提交历史、评分、结果、统计页面
│   │   │   ├── hwk/                             # 作业中心、作业编辑、提交、评测结果、批阅、统计页面
│   │   │   └── grd/                             # 成绩项、成绩总表、成绩明细、发布、分析、异议复核页面
│   │   ├── types/                               # 前端共享 TypeScript 类型，保持 API、状态枚举与后端约定一致
│   │   └── utils/                               # 请求封装、权限判断、时间格式化、文件处理等工具函数
│   └── tests/
│       ├── unit/                                # 前端组件、工具函数和状态管理单元测试
│       └── e2e/                                 # 学生端、教师端、管理端关键流程端到端测试
├── database/
│   ├── migrations/                              # MySQL 表结构迁移脚本，按 DB-AUTH/CRS/LRN/LAB/HWK/GRD 编号组织
│   ├── seeds/                                   # 演示环境初始用户、课程、实验、作业、成绩等种子数据
│   └── fixtures/                                # 测试用数据集、评测用例、接口测试夹具
├── scripts/
│   ├── dev/                                     # 本地开发启动、环境检查、数据重置脚本
│   ├── test/                                    # 后端、前端、接口、集成测试辅助脚本
│   └── deploy/                                  # 构建、打包、发布辅助脚本
├── deploy/
│   ├── docker/                                  # Dockerfile、docker-compose 等容器化配置
│   ├── nginx/                                   # 前端静态资源代理和后端 API 反向代理配置
│   └── mysql/                                   # MySQL 初始化、权限和部署环境配置
├── tests/
│   ├── api/                                     # REST API 契约测试和接口回归用例
│   ├── integration/                             # AUTH、CRS、LRN、LAB、HWK、GRD 跨模块集成测试
│   └── system/                                  # 面向验收演示的学生端、教师端、管理端系统测试
└── docs/                                        # 需求、概要设计、详细设计、开发流程和项目协作文档
```

### 模块目录和详细设计的对应关系

| 模块 | 后端目录 | 前端目录 | 数据库脚本 | 测试目录 | 主要职责 |
| --- | --- | --- | --- | --- | --- |
| AUTH | `backend/src/main/java/com/onlinejudge/auth` | `frontend/src/views/auth`、`frontend/src/api/auth` | `database/migrations` 中 `auth` 相关脚本 | `backend/src/test/java/com/onlinejudge/auth` | 用户注册登录、角色权限、会话管理、密码安全、审计日志 |
| CRS | `backend/src/main/java/com/onlinejudge/crs` | `frontend/src/views/crs`、`frontend/src/api/crs` | `database/migrations` 中 `crs` 相关脚本 | `backend/src/test/java/com/onlinejudge/crs` | 课程创建、章节管理、资源上传、课程成员、课程公告 |
| LRN | `backend/src/main/java/com/onlinejudge/lrn` | `frontend/src/views/lrn`、`frontend/src/api/lrn` | `database/migrations` 中 `lrn` 相关脚本 | `backend/src/test/java/com/onlinejudge/lrn` | 学习任务、学习进度、学习行为、通知中心、提醒规则 |
| LAB | `backend/src/main/java/com/onlinejudge/lab` | `frontend/src/views/lab`、`frontend/src/api/lab` | `database/migrations` 中 `lab` 相关脚本 | `backend/src/test/java/com/onlinejudge/lab` | 实验发布、实验提交、自动评测、实验报告、教师评分、统计反馈 |
| HWK | `backend/src/main/java/com/onlinejudge/hwk` | `frontend/src/views/hwk`、`frontend/src/api/hwk` | `database/migrations` 中 `hwk` 相关脚本 | `backend/src/test/java/com/onlinejudge/hwk` | 作业发布、作业提交、客观题与代码评测、教师批阅、重评和统计 |
| GRD | `backend/src/main/java/com/onlinejudge/grd` | `frontend/src/views/grd`、`frontend/src/api/grd` | `database/migrations` 中 `grd` 相关脚本 | `backend/src/test/java/com/onlinejudge/grd` | 成绩项配置、来源成绩同步、总评计算、成绩发布、异议复核、教学分析 |

## GitHub 提交规范

为保证开发、测试、预发布和生产分支边界清晰，提交代码时统一遵守以下规范。本仓库实际使用 `dev` 作为开发集成分支，它等价于常见 Git Flow 中的 `develop`；不要另起一个平行的 `develop` 分支。

---

## 1. 分支规范

| 分支 | 用途 | 来源 | 合并去向 | 环境 | 是否可直接提交 |
| --- | --- | --- | --- | --- | --- |
| `main` | 主分支，稳定版本，生产发布基线 | `release/*` 或 `hotfix/*` | 无 | `PRO` | 否 |
| `dev` | 开发集成分支，保存最新完成和 bug 修复代码 | `main` 或已稳定的 `dev` | `test/*`、`release/*` | `DEV` | 否 |
| `feature/<issue-id>-<name>` | 功能开发分支，一个 issue 一个分支 | 最新 `dev` | PR -> `dev` | 无固定环境 | 是 |
| `fix/<issue-id>-<name>` | 开发期普通 bug 修复分支 | 最新 `dev` | PR -> `dev` | 无固定环境 | 是 |
| `test/<name>` | 功能验收测试分支，供 FAT 环境或测试人员使用 | `dev` 或指定功能集合 | `release/*` 或回到 `dev` | `FAT` | 限测试修复 |
| `release/<version>` | 预上线分支，用于 UAT、版本冻结和发布前修复 | `test/*` 或稳定 `dev` | `main` 和 `dev` | `UAT` | 限发布修复 |
| `hotfix/<issue-id>-<name>` | 线上紧急修复分支 | 最新 `main` | `main` 和 `dev` | 无固定环境 | 是 |

环境对应关系：

| 环境 | 对应分支 | 用途 |
| --- | --- | --- |
| `DEV` | `dev`、`feature/*` | 开发者自测 |
| `FAT` | `test/*` | 功能验收测试 |
| `UAT` | `release/*` | 用户验收测试、预发布 |
| `PRO` | `main` | 生产环境 |

分支命名示例：

```bash
feature/45-auth-login
feature/52-course-management
fix/61-submission-timeout
docs/70-readme-git-rule
test/module-fat
release/v1.0.0
hotfix/96-login-500
```

禁止直接在 `main`、`dev`、`release/*` 上写常规功能代码。`main` 只接受 `release/*` 或 `hotfix/*` 合并；`hotfix/*` 修复完成后必须同时回合到 `main` 和 `dev`，避免线上修复丢失。

---

## 2. 提交信息规范

提交信息统一格式：

```bash
type(scope): message
```

常用 `type`：

* `feat`：新功能
* `fix`：修复问题
* `docs`：文档修改
* `style`：不影响代码语义的格式、空白、缺失分号等
* `refactor`：重构
* `perf`：性能优化
* `test`：添加、修正或删除测试
* `chore`：构建过程、辅助工具、依赖、仓库维护等杂项

示例：

```bash
feat(user): add login function
fix(course): fix course list bug
docs(readme): update project intro
refactor(auth): simplify token check
test(homework): add submission status test
```

---

## 3. 提交要求

* **一次 commit 只处理同一类别的问题**
* **一个 commit 不超过 3 个紧密相关的问题**
* **不要把功能、修复、格式化、重构、测试、文档混在同一个 commit**
* 提交信息要清楚，不要写：

  * `update`
  * `test`
  * `改了一下`
  * `提交代码`
* 提交前先确认：

  * 代码能运行
  * 没有明显 bug
  * 没有无关文件
  * 没有密钥、密码等敏感信息
* 提交信息不合规且尚未推送时，使用：

```bash
git commit --amend
```

* 需要拆分或重做提交时，优先使用 `git reset --soft` 或 `git reset --mixed`；不要把 `git reset --hard` 当作常规修正手段，除非已经明确确认不会丢失他人改动。

---

## 4. 开发流程

### 4.1 功能开发

一个 issue 对应一个功能分支和一个 PR，目标分支统一为 `dev`。

```bash
git status --short --branch
git fetch origin
git switch dev
git pull --ff-only origin dev
git switch -c feature/<issue-id>-<short-name>

git add .
git commit -m "feat(module): xxx"
git push -u origin feature/<issue-id>-<short-name>
```

开发完成后发起 PR 到 `dev`，PR 描述必须包含：

```text
Closes #issue_id
```

### 4.2 功能验收测试

需要集中测试时，从 `dev` 或指定功能集合创建 `test/*` 分支：

```bash
git switch dev
git pull --ff-only origin dev
git switch -c test/<name>
git push -u origin test/<name>
```

`test/*` 对应 `FAT` 环境，主要给测试人员做功能验收。测试中发现的问题应回到对应 feature/fix 分支处理；确需在 `test/*` 上修补时，只允许测试阻断问题。

### 4.3 预发布

准备上线时，从稳定的 `test/*` 或 `dev` 创建 `release/*`：

```bash
git switch dev
git pull --ff-only origin dev
git switch -c release/vX.Y.Z
git push -u origin release/vX.Y.Z
```

`release/*` 对应 `UAT` 环境，只做发布前修复、版本号、配置和文档收尾。发布完成后合并到 `main`，并回合到 `dev`。

### 4.4 线上紧急修复

线上问题从最新 `main` 创建 `hotfix/*`：

```bash
git switch main
git pull --ff-only origin main
git switch -c hotfix/<issue-id>-<short-name>

git add .
git commit -m "fix(module): xxx"
git push -u origin hotfix/<issue-id>-<short-name>
```

`hotfix/*` 修复完成后必须同时合并到 `main` 和 `dev`。

---

## 5. 禁止事项

* **禁止直接提交到 `main`**
* **禁止直接在 `dev` 上写功能代码**
* **禁止在 `release/*` 上开发新功能**
* **禁止一个 commit 混入多个无关功能**
* **禁止使用无意义提交说明**
* **禁止提交临时代码、调试代码、敏感信息**
* **禁止静默修改公共接口、数据库结构、状态枚举或跨模块 DTO**

---

## 6. 建议

* 小步提交，方便回溯
* 经常同步 `dev`，减少冲突
* 提交信息写清楚，方便组员查看历史
* 发 PR 前先跑测试和 `git diff --check`
* PR 保持小而完整：能对应一个 issue，能独立评审，能独立回滚

---

## 7. 示例

```bash
feat(user): add user login API
fix(judge): fix submission timeout
docs(readme): add setup guide
style(frontend): format course list view
perf(grade): optimize score statistics query
test(auth): add permission denied case
chore(deps): update frontend lockfile
```
