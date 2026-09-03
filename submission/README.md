# 小学期最终提交目录

本目录是正式任务书六类交付物的聚合层，不是新的开发根目录。开发期继续在现有 `backend/`、`frontend/`、`docs/`、`deploy/`、`tests/` 等目录中维护唯一正本；版本冻结后，再按本页映射复制可追溯的交付物。

## 目录映射

| 正式目录 | 内容边界 | 当前正本/来源 | 冻结前重点 |
| --- | --- | --- | --- |
| [`01_source/`](01_source/README.md) | 可构建、可运行的前后端、数据库与必要开发脚本 | `backend/`、`frontend/`、`database/`、`scripts/dev/` | 排除依赖、编译产物、本地数据和密钥 |
| [`02_docs/`](02_docs/README.md) | 需求、系统/构件/对象设计、实现、用户和部署文档 | `docs/最终提交/`、`docs/过程/` | 按用例补齐三层设计追溯关系 |
| [`03_devops/`](03_devops/README.md) | 容器、CI/CD、Kubernetes、健康检查和 HPA | `deploy/`、`scripts/deploy/`、`.github/workflows/` | issue #379 已归档配置与 Actions 证据；#319 已提供 HPA 交叉输入，但 final SHA 的质量门禁/D3 仍 BLOCKED，见 `03_devops/ACCEPTANCE.md` |
| [`04_tests/`](04_tests/README.md) | 单元、API、端到端、回归、故障和性能测试及结果 | 各子工程测试目录、`tests/`、`scripts/test/`、`docs/最终提交/测试文档.md` | 补可执行 E2E、故障注入及 2–3 个接口的同环境多轮性能结果 |
| [`05_management/`](05_management/README.md) | 计划、分工、看板、日报、会议、风险与评审证据 | Notion、GitHub Project/Issue/PR、`docs/最终提交/软件开发计划书.md` | 每日导出或固化关键证据，保留人员、时间和任务关联 |
| [`06_defense/`](06_defense/README.md) | 答辩 PPT/PDF、演示脚本、演示数据、备份与问答 | 答辩阶段产物 | 按 4 分钟项目/架构、7 分钟演示、4 分钟问答定稿 |

## 归档规则

1. 六个目录中的材料必须能追溯到仓库提交、GitHub Issue/PR、Notion 记录或可重复执行脚本。
2. 源码、文档、部署配置和测试脚本在开发期只保留一份正本；`submission/` 内不手工维护副本。
3. 日报、截图、性能结果等证据使用 `YYYY-MM-DD-主题` 命名，并在对应文档中记录产生方法。
4. 不归档 `node_modules/`、`target/`、`dist/`、本地数据库、IDE 缓存、token、密码、真实环境变量或其他敏感数据。
5. 最终压缩包应直接以 `01_source`–`06_defense` 为顶层目录；外层 `submission/` 仅用于仓库内聚合，不得多包一层。

## 冻结检查

- 六个目录均有实际交付物，且名称与任务书一致。
- 文档内的链接、命令、版本号、截图和证据与最终提交版本一致。
- 从空环境按部署文档完成构建、部署、健康检查和核心业务回归。
- 性能、故障处理、HPA 和最终演示均使用可重复脚本和固定数据验证。
- 扫描压缩包，确认不含密钥、token、真实密码或无关大文件。
