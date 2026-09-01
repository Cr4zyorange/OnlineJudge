---
name: onlinejudge-development-workflow
description: 在 OnlineJudge（OnlineJudgeForSE）仓库内做任何功能开发、缺陷修复、测试、文档编写、issue 规划、PR 评审或本地自检时使用。覆盖 AUTH/CRS/LAB/HWK/GRD/LRN 六模块的纵向全栈交付（数据库→后端→前端→权限→测试）、文档驱动与测试驱动纪律、GitHub 分支/issue/PR 协作规则、SRS/概要/详细设计文档评审，以及 WSL + Dev Container 环境入口与验证命令。用户提到模块开发、写测试、闭环 issue、提 PR、评审 PR、改设计文档，或说"按仓库规范做"时都应触发。
---

# OnlineJudge 开发工作流（整合版）

本 skill 整合自项目曾使用的 codex skill（`onlinejudge-hwk-development-workflow`）与仓库规范文档（`AGENTS.md`、`docs/提炼skills/`、`docs/开发/`、`docs/最终提交/`）。它是一个紧凑的操作循环：按当前 issue 或任务类型加载对应参考文件，不要一次读完所有文件。

## 参考文件路由

| 任务场景 | 先读 |
| --- | --- |
| 确定某个 issue 的最小纵向切片、模块交付顺序、跨模块契约 | `references/module-development.md` |
| 触碰 HWK 模块的 API/DTO/枚举/迁移/权限/事件/前端页面，或排查 HWK 已知回归 | `references/hwk-module.md` |
| 涉及 issue、分支、commit、PR、评审、合并、日报或共享文档碰撞 | `references/github-collaboration.md` |
| 用户要求审批/巡检 PR，或评审与 issue 绑定的 PR | `references/pr-review.md` |
| 修改或评审需求规格、概要设计、详细设计、测试文档、追溯矩阵、UML 图 | `references/document-review.md` |
| 开分支、规划文件、写测试、跑验证，或遇到本地工具异常 | `references/verification.md` |

读完所选参考后，再读其中点名的仓库在线文档。最终提交文档优先于过程稿；过程稿用于补充细节与追溯。

## 开发前必读（按序）

1. `AGENTS.md` —— 仓库协作总规范，本 skill 的上位文档。
2. `docs/开发/<模块>开发流程.md` —— 对应模块的执行指南（含"前端页面与交互"章节）。
3. `docs/最终提交/软件需求规格说明书.md`、`软件概要设计说明书.md`、`软件详细设计说明书.md` —— 需求、接口、数据结构、模块边界与验收行为的主要依据。
4. `docs/过程/` 下对应模块的源文档（需求/概要/详细设计/测试/项目管理）。
5. 涉及前端时：详细设计第 4 章对应 `UI-*` 编号，以及 `docs/过程/UI设计参考/` 的 `index.html`、`style.css`、`img/back.jpg`。

## 环境入口（WSL + Dev Container）

- 仓库位于 `/home/skk4784/repos/OnlineJudge`（WSL，行尾统一 LF）。宿主机只保证公共工具（`git` 最新版）；Java/Maven/Node 工具链全部在 Dev Container `onlinejudge-dev` 内，不在宿主机对齐。
- 固定入口（详细命令见 `references/verification.md`）：
  1. `scripts/dev/container.sh` —— 统一 CLI：`up`（启动容器，含 MySQL 用 `up`、仅 dev 用 `up dev`）、`exec "<命令>"`（容器内跑单条命令）、`shell`（交互进入）、`version`（工具链自检）、`status`、`down`。
  2. `.devcontainer/devcontainer.json` —— IDE “Reopen in Container”。
  3. `deploy/dev/docker-compose.yml` —— 机器级定义，容器名固定 `onlinejudge-dev`、工作目录固定 `/workspace`。
- 判断是否已在容器内：`test -f /.dockerenv`。容器内 `git` 凭据来自挂载的宿主机 `~/.git-credentials`。
- 需要 Java/Maven/Node 的命令必须进容器执行；只做 git/文档操作时可在宿主机直接执行。

## 硬性纪律（不可协商）

1. 从 issue 出发，不凭想象做功能；一个 issue 对应一个 PR，PR 目标分支为远程 `dev`。
2. 从仓库当前状态开始：`git status --short --branch`、`git fetch origin`，涉及 GitHub 时加 issue/PR 元数据。
3. 一切行为变更走 Red-Green-Refactor：亲眼看到目标测试失败后，才写生产代码。
4. 交付纵向切片：数据库/迁移 → 后端 API → Service 业务规则 → 前端类型/API/页面 → AUTH/CRS 权限 → 成功/失败/空/异常状态 → 测试。不允许只交付孤立的一层。
5. 复用既有模块契约：不在业务模块内复制 LAB 评测器、CRS 权限解析、AUTH 用户逻辑、LRN 通知存储、GRD 成绩聚合或文件存储内部实现。
6. 公共契约显式化：API、DTO、数据库、枚举、权限、事件或跨模块变更必须同步更新代码、类型、测试和文档，否则必须声明为设计调整。
7. 除非用户在当前任务中明确指示送审/评审/提交/合并，不执行任何 GitHub 变更操作（push、PR 创建/编辑/审批/打回/合并、Project 状态变更）。本地准备好并报告就绪状态。
8. 尊重阶段与模块所有权：共享文档中只编辑 issue 明确分配的本模块章节和全局行；不重排格式、不重编号、不改写其他模块或整合者拥有的章节。
9. UML 变更复用仓库既定的源格式、渲染器、资产类型与相邻图表风格；每图一个可编辑源文件，提交渲染后的静态资产，并验证渲染与视觉质量。不为单一模块引入另一套图表工具或视觉体系，除非有仓库级决策。
10. 只报告观察到的证据：区分 `PASS`、`FAIL`、`BLOCKED`、进行中、风险、返工；绝不把没跑过的检查或计划中的结果说成已完成。
11. 每次成功 push 更新 PR 分支后，必须向用户做一次简要汇报，至少包含 PR 编号或链接、本次推送的 commit、本次 PR 完成的内容和实际验证结果；不得把尚未推送的改动计入本次汇报。

## 快速循环

1. **识别 issue**。用 `references/module-development.md`（及 HWK 场景下的 `references/hwk-module.md`）把在线 issue 映射到 FR/UI/API/DB/TC 编号；把旧编号分解视为历史记录而非当前授权。读关联 issue 与对应文档章节，给自己写 5-10 行实现笔记：追溯编号、第一个红测试、可能改动的文件、权限分支、验证命令。
2. **先搜索再编辑**。优先 `rg`；查看 LAB、GRD、CRS、AUTH 与前端测试中最近的既有模式。Windows 下 `rg`/`git` 缺失时按 `references/verification.md` 处理。
3. **写红测试**。后端优先复用 `@SpringBootTest` + `MockMvc` 或 H2 迁移测试；前端用既有 Vue/Vitest 模式并有意识地 mock `frontend/src/api/http.ts` 或浏览器存储。测试名描述业务行为，不写 `test1`、`should work` 这类空泛名称。
4. **实现最小通过切片**。Service 负责权限校验、状态流转、校验、事务、事件发布和仓储调用；Controller 不得短路属于 Service/权限代码的课程管理检查；前端调用真实 API 包装并渲染加载、空、失败、无权限、成功状态。
5. **先窄后宽验证**。跑到目标测试变绿 → 跑相邻后端/前端测试 → 跑 `references/verification.md` 中 issue 级命令。
6. **对照自检**。按 `references/github-collaboration.md` 与 `references/pr-review.md` 的门禁自查：分支名、issue 关联、范围、文档、测试、密钥/本地文件、公共契约漂移、已知回归。若触及评测、发布、重评或来源成绩，额外执行“评测完成 → 发布 → 重评 → 重评终态”时间线、审计历史、学生发布前可见性和真实前端 wrapper 对照；本地绿灯不替代 CI 运行时证据。修复阻塞项后报告"就绪，等待用户送审指示"。

## 实现偏好

- 优先沿用仓库既有结构、命名与测试方式，不引入新抽象。
- 后端 DTO 与前端类型保持同步；状态用显式枚举/联合类型，不用松散字符串。
- 学生身份从 `CurrentUser` 派生，绝不信任前端传来的 `studentId`/`userId`。
- 教师与助教只有在 CRS 授予该课程管理权限时才是课程管理者。
- 对学生隐藏答案、隐藏测试用例、他人提交、未发布的最终成绩和私有日志。
- LRN/GRD 事件失败不得破坏本模块主数据，除非设计明确要求回滚。

## 完成输出格式

使用本 skill 收尾时，报告：

- 覆盖的 issue 编号与追溯编号（FR/UI/API/DB/TC）；
- 观察到失败的红测试与变绿的验证命令；
- 变更的后端/前端/迁移文件；
- 覆盖的权限与可见性分支；
- 触碰的跨模块契约；
- 触碰的共享文档/图表及适用的所有权边界；
- 若本次执行了 push：PR 编号或链接、本次推送的 commit、本次 PR 完成内容与实际验证结果；
- 残余风险或阻塞点，尤其是 GitHub Project 权限、CI 缺口、有意推迟的设计工作，以及因用户未下达送审指示而有意未执行的 PR 操作。
