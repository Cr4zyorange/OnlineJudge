# 本地执行与验证（Windows 工作区）

来源：codex skill 的 `workflow.md` 与 `review-checklist.md`。适用于开分支、规划文件、写测试、跑验证，以及处理本机工具异常。

## 在线预检

任何实质性工作前：

```powershell
git status --short --branch
git fetch origin
gh --version
gh auth status
git remote -v
```

读 `AGENTS.md`、在线 issue、其 Project 状态与相邻阶段 issue 再选文件。工作区脏或涉及共享文档时，加 `git diff --name-only` 与精确文件 diff；保护无关工作并应用协作参考中的所有权规则。

## Windows 工具路径

`git`/`rg` 不在 PATH 时，先查已知安装位置再用完整路径，不静默改机器 PATH、不装工具：

```powershell
Get-Command git -ErrorAction SilentlyContinue
Get-Command rg -ErrorAction SilentlyContinue
& 'D:\software\Git\cmd\git.exe' status --short --branch
& 'C:\Program Files\GitHub CLI\gh.exe' auth status
```

`rg` 不可用或 WindowsApps 报 `Access is denied` 时用 PowerShell 替代：

```powershell
Get-ChildItem -Recurse -File backend,frontend,database |
  Select-String -Pattern 'TODO','FIXME','console\.log','debugger','<<<<<<<','=======','>>>>>>>'
```

## gh CLI 可靠性

`gh` 元数据命令用 8-15 秒短超时，大查询拆成小调用串行执行；不合并 `body`、`files`、`statusCheckRollup` 到一次 `gh pr view --json`：

```powershell
gh pr view <number> --json number,title,url
gh pr view <number> --json baseRefName,headRefName,isDraft,state,mergeStateStatus,closingIssuesReferences
gh pr view <number> --json files
gh pr view <number> --json statusCheckRollup
gh pr view <number> --json body
```

不并行跑多个 `gh` 命令——被中断或并行的 `gh` 会留下僵尸 `gh.exe` 让后续命令假挂起。超时/中断/卡住时先清理再重试：

```powershell
Get-Process gh -ErrorAction SilentlyContinue | Select-Object Id,StartTime,CPU,Path
Get-Process gh -ErrorAction SilentlyContinue | Stop-Process -Force
```

GitHub 访问不稳或用户要求代理时，为本命令会话设置本地代理端口 `7897`：

```powershell
$env:HTTPS_PROXY='http://127.0.0.1:7897'
$env:HTTP_PROXY='http://127.0.0.1:7897'
$env:ALL_PROXY='http://127.0.0.1:7897'
$env:NO_PROXY='localhost,127.0.0.1'
```

`gh issue view --json projectItems` 或 `gh project ...` 因缺 project scope 失败时尝试 `gh auth refresh --hostname github.com -s read:project`；设备码或网络失败则记录确切错误，可见 Project 状态无法核验时不批准。

## 规划模板

编辑前写一份私有小计划：

```text
Issue: #<id> / <标题>
Trace: FR-.., UI-.., API-.., DB-.., TC-..
First red test: <测试名与命令>
Slice: DB -> backend -> service -> frontend -> permission -> states
Files likely touched: <路径>
Verification: <targeted>, mvn test, 前端 unit/typecheck/build
Ownership: <仅本模块章节/行/资产；共享文件碰撞检查>
Risk: <跨模块、环境或设计不确定性>
```

## Red-Green-Refactor 细则

1. 写/改最小测试证明缺失行为；2. 运行并按预期原因观察到失败；3. 写刚好通过的生产代码；4. 重跑目标测试；5. 绿后才重构，重构后重跑；6. 扩大到相邻模块/全量命令。

一次只写一个 tracer-bullet 测试，不要先写全部测试再写全部实现——那会产出针对想象结构的测试。测试走公开行为：

- 优先 HTTP/API/Service 可见行为，而非私有方法断言；优先路由/API/视图结果而非组件内部。
- 只 mock 真实边界：当前用户、CRS 权限、事件发布器、评测器/沙箱、必要时的网络/存储/时间。
- 避免在无害重构上失败、却漏掉权限/可见性/状态流转破损的测试。

红测试落点：后端 `backend/src/test/java/com/onlinejudge/<mod>/...`；迁移约束用 H2 MySQL 模式迁移测试；前端 API 包装 `frontend/tests/unit/<mod>/*Api.spec.ts`；页面行为用带真实路由/查询条件的 Vue/Vitest 组件测试；评审回归用恰好在评审者报告的行/行为上失败的测试。

## 实现顺序

除非既有代码已有更早层次，按：

```text
database/migration/test data -> domain enum/entity/command -> repository query
-> service rule/transaction/permission -> controller DTO/API response
-> frontend type/API wrapper -> frontend view/state -> tests and verification
-> PR/review checklist
```

## 必测权限分支

- 非成员不能查看/提交。
- 学生不能看草稿、他人提交、标准答案、隐藏用例、隐藏日志、未发布最终成绩。
- 教师/助教仅在 CRS 授予课程管理权时可管理。
- Controller 平台角色检查不得在 CRS 课程权限评估前拒绝助教。
- `allow_resubmit=false` 时重复提交失败；迟交按 `allow_late_submit` 拒绝或标 `LATE`。
- 评审、改分、复评、发分按 issue 范围写日志。

## 验证命令

先目标后宽泛：

```powershell
# 后端定点
mvn -Dtest=HomeworkControllerTest test
mvn -Dtest=HomeworkMigrationTest test

# 后端全量
mvn test

# 前端定点
& 'D:\Program Files\nodejs\node.exe' node_modules/vitest/vitest.mjs run tests/unit/hwk/homeworksApi.spec.ts --pool=threads

# 前端宽泛
& 'D:\Program Files\nodejs\node.exe' node_modules/vitest/vitest.mjs run --pool=threads
& 'D:\Program Files\nodejs\node.exe' node_modules/vue-tsc/bin/vue-tsc.js --noEmit
& 'D:\Program Files\nodejs\node.exe' node_modules/vite/bin/vite.js build --debug

# 仓库
git diff --check
git status --short --branch
```

本机上 `npm run build` 可能卡在输出管道而直接 Vite debug 能完成；使用直接 Vite 命令时要记录。非 HWK 模块把 `Homework*` 换成对应模块测试类/目录。

## 前端测试陷阱

- 测试覆盖或运行于 jsdom 时，有意识地 mock `window.localStorage`，不要假设部分 mock 后 `setItem` 仍存在。
- 断言优先用稳定 `data-testid`、API 调用、路由/查询行为与可见状态文本。
- 改 `App.vue` 路由时同时测学生与教师两种 query role，防止 GRD/LAB/HWK 入口回归。

## 数据库迁移陷阱

- 测唯一与外键约束，不只测建表。
- 迁移写 MySQL 8.0 兼容 DDL，不只 H2 兼容。禁用 `ALTER TABLE ... ADD CONSTRAINT IF NOT EXISTS`——MySQL 8.0 不允许 `ADD CONSTRAINT` 后跟 `IF NOT EXISTS`；用表排序 + `CREATE TABLE IF NOT EXISTS` 内联 `CONSTRAINT ... FOREIGN KEY ...`，或显式 MySQL 兼容的幂等策略。
- H2 通过不足以证明生产迁移兼容。
- 合并 `dev` 后保持 `application.properties`/`application.yml` 与测试资源的迁移注册与其他模块对齐。
