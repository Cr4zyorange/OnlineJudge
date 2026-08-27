# LAB 实训实验模块开发流程

## 1. 开发定位

LAB 是复杂业务模块，负责实验创建、发布、提交、报告、自动评测、教师评分、反馈和统计。它依赖 AUTH 当前用户、CRS 课程成员关系，并向 LRN 触发通知，向 GRD 提供实验成绩来源。

LAB 与 HWK 必须提前统一评测状态枚举和评测结果 DTO，复用 `EvaluationTask`、`Evaluator`、`SandboxExecutor`、`EvaluationResult` 等抽象，不要各写一套不兼容的评测流程。

## 2. 详细设计阅读入口

开发前先阅读：

- `docs/最终提交/软件详细设计说明书.md` 的 `3.4 实训实验模块（LAB）`
- `docs/过程/详细设计/LAB-实训实验模块-详细设计提交稿.md`
- HWK 详细设计中的评测状态和 DTO，确认共享评测抽象
- CRS 成员权限校验接口和 LRN/GRD 事件契约

## 3. 统一开发顺序

```text
1. 读 LAB 详细设计章节，确认 UI-LAB / API-LAB / SVC-LAB / DB-LAB / TC-LAB 编号
2. 建实验、测试用例、提交、评测、报告、评分、评分日志表
3. 写实验创建、编辑、草稿、发布、状态流转 API
4. 写实验 Service、提交 Service、评测 Service、评分 Service
5. 写教师端实验管理和创建编辑前端页面
6. 写学生实验查看、提交、历史、反馈前端页面
7. 接入 AUTH 当前用户和 CRS 课程成员/教师权限校验
8. 补截止、归档、越权、重复提交、文件异常等处理
9. 准备实验、测试用例、提交、评分测试数据
10. 联调 LRN 通知和 GRD 成绩来源
```

## 4. 状态机先行

LAB 开发前必须先定好实验状态和提交/评测状态。持久化实验状态为：

```text
DRAFT → PUBLISHED → CLOSED → SCORE_PUBLISHED → ARCHIVED
```

状态用途：

- `DRAFT`：草稿，可编辑，不对学生可见
- `NOT_OPEN` / `OPEN`：由发布时间和截止时间推导的页面展示标签，不写入数据库
- `PUBLISHED`：学生可查看和提交
- `CLOSED`：截止后不可提交，可评测和评分
- `SCORE_PUBLISHED`：成绩已发布，可供学生查看并同步 GRD
- `ARCHIVED`：归档后不可修改

评测状态至少包含 `PENDING`、`RUNNING`、`ACCEPTED`、`WRONG_ANSWER`、`COMPILE_ERROR`、`RUNTIME_ERROR`、`TIME_LIMIT_EXCEEDED`、`SYSTEM_ERROR`。当前 Docker 沙箱负责编译、运行、IO 比对、网络隔离、CPU/内存/pid/tmpfs 限制和评测后容器清理；它不是分布式判题架构，也不得在业务服务中复制一套 HWK 不兼容的评测逻辑。

## 5. 数据库与实体

按 DSD 建立以下表：

| 表 | 用途 |
| --- | --- |
| 实验表 | 实验基本信息、课程、状态、开放/截止时间、发布信息 |
| 测试用例表 | 输入输出、权重、可见性、排序 |
| 实验提交表 | 学生提交代码或文件、提交时间、有效版本 |
| 提交源文件资产表（DB-LAB-09） | 按提交版本一对一保存 storage key、存储/入库前已清理的原始文件名、Content-Type、大小、上传人和可用状态；storage key 仅供服务端使用 |
| 实验评测记录表 | 评测状态、通过用例数、错误信息、资源消耗 |
| 实验报告表 | 报告文件、版本、上传时间 |
| 实验评分表 | 教师评分、评语、最终得分 |
| 评分变更日志表 | 分数修改前后值、原因、操作者 |

提交表和评分表要支持多次提交、当前有效提交和评分留痕。`DB-LAB-09 lab_submission_source_file` 以 `submission_id` 唯一约束绑定具体提交版本，至少包含 `lab_id`、`course_id`、`uploader_id`、`storage_key`、`original_filename`、`content_type`、`file_size`、`status`、`created_at`、`updated_at` 和 `deleted_at`。新提交以该资产记录为可信来源；`lab_submission.file_id` 仅保留为旧数据内部兼容字段，不得通过公共 DTO 返回，也不得解析 storage key 猜测业务文件名。

源文件内部资产状态至少区分 `AVAILABLE` 和 `DELETED`。只有旧 `file_id` 而没有可信资产记录的数据，详情响应以顶层 `hasFile=true`、`sourceFile=null` 表达“本版本含文件但可信元数据不可用”，不得把内部状态枚举暴露到公共 DTO。文件存储已成功但数据库事务回滚时会调用物理删除，且仅在该删除成功时可认定不留孤儿文件。`DELETED/deleted_at` 是冻结的生命周期语义，#222 未实现资产删除或失效的状态转移/物理清理流程，本期只拒绝人工构造或历史已有的 `DELETED` 记录。底层复制中途失败的部分文件、删除失败后的孤儿扫描、业务级审计和重试队列均未实现。

## 6. 后端 API 与 Service

先实现教师端实验管理：

- 创建实验
- 编辑实验
- 保存草稿
- 发布实验
- 配置测试用例
- 修改实验状态

再实现学生端：

- 查询实验列表和详情
- 提交代码或文件
- 查询提交历史
- 查询最新评测和反馈
- 上传或下载实验报告

然后实现教师评分与统计：

- 查看学生提交
- 通过 `API-LAB-10` 查看安全 `sourceFile` 元数据，不返回 `fileId`、storageKey 或本地路径
- 通过 `API-LAB-19 GET /api/v1/labs/{labId}/submissions/{submissionId}/source/download` 下载指定提交版本的源文件
- 查看评测结果
- 填写分数和评语
- 修改分数并记录日志
- 查询已提交人数、未提交名单、平均分、逾期数、分数段

`API-LAB-19` 只允许当前课程的可管理教师或管理员访问。服务端每次按“认证/教师或管理员角色 → 读取实验及其课程 → CRS `canManageCourse` → 提交与实验绑定 → 资产绑定/状态 → 物理文件”的顺序复核。学生本人下载不在本期范围内，不能通过匿名 URL 绕过。成功响应设置可信 `Content-Type`、`Content-Length` 和 UTF-8 `Content-Disposition`；入库前已清理的文件名在下载前仍要重验路径分隔符、控制字符和 CR/LF。

`API-LAB-10` 的公共响应只用顶层 `hasFile` 表示该提交版本是否原本包含源文件；`sourceFile` 为 nullable 对象，且只包含 `originalFilename`、`contentType`、`fileSize`、`downloadAvailable`。无文件时为 `hasFile=false, sourceFile=null`；旧元数据缺失或内部资产不可用时为 `hasFile=true, sourceFile=null`；可用但调用者无下载权限时返回元数据且 `downloadAvailable=false`。响应绝不返回任何下载 URL，教师端按已知 `labId/submissionId` 调用固定 API-LAB-19 路径。

当前实现由 `LabExperimentController` 暴露 LAB 接口，`LabSubmissionService` 负责提交与源文件业务，并通过 `LabSubmissionSourceFileRepository` / `JdbcLabSubmissionSourceFileRepository` 访问 DB-LAB-09；没有拆分独立源文件 service/controller/mapper。源文件与实验报告是两个业务资产，分别走 source download 与 report download；二者只共享底层 `FileStorageService`，不复用 CRS 资源记录或下载接口。评测执行逻辑必须通过共享评测抽象调用，便于 HWK 复用。

## 7. 前端页面与交互

LAB 前端必须包含：

| 页面 | 完成标准 |
| --- | --- |
| 实验列表页 | 学生看可参与实验，教师看课程实验管理列表 |
| 实验详情页 | 展示实验说明、状态、截止时间、提交入口 |
| 实验创建/编辑页 | 教师配置基本信息、测试用例、开放截止时间 |
| 学生提交页 | 支持代码或文件上传，展示提交校验和成功反馈 |
| 提交历史页 | 展示多次提交、最新版本、有效版本、评测状态 |
| 教师评分页（UI-LAB-06） | 查看提交内容、评测结果和安全源文件元数据；“下载源文件”与“下载实验报告”为两个独立入口；填写分数和评语 |
| 实验反馈页 | 学生查看评测状态、通过用例、得分、错误提示、教师评语 |
| 实验统计页 | 展示提交人数、未提交名单、平均分、逾期数、分数段 |

每个页面要接真实接口，至少处理加载、失败、空状态、不可提交、无权限、已截止等状态。UI-LAB-06 还必须处理源文件下载中、成功、失败重试、重复点击去重、会话失效、权限不足、无文件、旧数据元数据缺失和文件已删除；页面不得显示 `fileId`、storageKey 或本地路径。

## 8. 权限、异常与跨模块事件

必须覆盖：

- 非课程成员不能查看或提交实验
- 学生不能查看他人提交
- 学生不能发布、编辑、评分实验
- 教师只能管理自己课程内实验
- 教师/管理员仅能下载其 `canManageCourse` 课程内、当前实验和当前提交版本对应的源文件；学生本人下载本期排除
- 跨课程、跨实验、跨提交猜测统一在读取物理文件前拒绝，且不泄漏文件是否存在
- 实验截止后按规则禁止提交
- 归档后不能修改
- 测试用例、报告文件和提交源文件访问必须校验课程、实验与提交归属

下载错误语义冻结为：未登录使用 `ERR-AUTH-04`，全局权限拒绝使用 `ERR-AUTH-05`，LAB 课程管理权限不足使用 `LAB-403-01`，目标或归属错配使用既有 `LAB-404-01`，授权后确认提交无源文件使用 `LAB-404-03`，旧元数据缺失/资产删除/元数据不可信使用 `LAB-409-03`，物理文件缺失或存储读取、完整性异常使用 `LAB-500-05`。源文件上传类型和大小继续使用既有 `LAB-400-06`。

跨模块事件：

- 实验发布 → LRN 通知
- 评测完成 → LRN 通知
- 评分完成 → LRN 通知，并向 GRD 提供实验成绩来源

向 GRD 提供成绩时必须包含来源类型、来源编号、课程编号、学生编号、分数、满分、状态和更新时间。

## 9. 测试与自测清单

| 测试点 | 验收标准 |
| --- | --- |
| 实验发布 | 教师可创建、编辑、发布实验 |
| 学生提交 | 学生可在开放期提交并得到反馈 |
| 提交历史 | 多次提交记录和有效版本正确 |
| 自动评测 | Docker 沙箱能完成 IO 比对，且 AC、编译错误、运行错误、超时、内存限制和容器清理均可重复验证 |
| 教师评分 | 教师可评分，修改分数有日志 |
| 学生反馈 | 学生只能看自己的评测和评分结果 |
| 统计页面 | 已提交、未提交、平均分等指标正确 |
| 权限边界 | 非成员、越权教师、截止后提交均被拒绝 |
| 安全元数据 | `API-LAB-10` 返回文件名、类型、大小和受控能力，不返回任何内部存储标识 |
| 教师源文件下载 | 可管理课程教师下载指定版本，Unicode 文件名、MIME、长度和内容一致 |
| 下载越权 | 匿名、学生本人、非成员、其他课程教师及跨 lab/submission 猜测均失败且不泄漏 |
| 生命周期与异常 | 无文件、旧元数据缺失、已删除、物理文件缺失和存储异常返回冻结错误语义 |
| 前端下载状态 | 独立源文件入口覆盖 pending 去重、失败重试、401/403 和 blob 下载 |
| 迁移与补偿 | H2/MySQL/compose schema 一致，提交版本与资产一对一；物理存储已成功且事务回滚时，补偿删除成功后不留孤儿文件，partial copy/delete 失败仍为运维残余 |
| UC-LAB-01 浏览器链路 | 教师创建草稿、配置公开/隐藏测试用例并发布；发布事件由 LRN 消费，失败分支保留字段并返回受控提示 |
| UC-LAB-02 浏览器链路 | 学生提交、Docker 评测、报告上传、教师受控下载/评分/反馈、成绩发布、学生查看结果；覆盖学生越权、截止、隐藏用例与统计 |

## 10. 联调顺序

1. AUTH → LAB：当前用户和角色判断
2. CRS → LAB：课程成员和教师权限校验
3. LAB ↔ HWK：评测状态和 `EvaluationResult` 一致；#214 的 HWK FILE 上传/所有权链路与 LAB 源文件资产保持独立，若抽取公共文件契约必须显式同步双方
4. LAB → LRN：`LAB_EXPERIMENT_PUBLISHED`、`LAB_SUBMISSION_SCORED`、`EXPERIMENT_SCORE_PUBLISHED` 由 `NotificationEventPublisher` 发布，LRN 负责通知落库
5. LAB → GRD：`LabSourceGradeService` 仅在 `SCORE_PUBLISHED`/`ARCHIVED` 时提供 `SourceGradeDTO`，GRD 不读取 LAB 内部表

完成标准是教师发布实验、学生提交、系统评测、教师评分、学生查看反馈、成绩进入 GRD 的路径可演示。Issue #265 的可重复入口为 `scripts/test/verify-issue-265.ps1`；其在真实 Docker 评测前预拉取评测镜像，避免首次镜像拉取被误归类为编译超时。
