# OnlineJudgeForSE

## GitHub 提交规范

为保证项目协作清晰、高效，提交代码时统一遵守以下规范。

---

## 1. 分支规范

* `main`：主分支，存放稳定版本，**不要直接提交**
* `dev`：开发分支，日常开发基于该分支进行
* 功能开发使用个人/功能分支，例如：

```bash
feature/login
feature/course-management
fix/user-bug
docs/readme
```

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
* `refactor`：重构
* `test`：测试
* `chore`：杂项修改

示例：

```bash
feat(user): add login function
fix(course): fix course list bug
docs(readme): update project intro
refactor(auth): simplify token check
```

---

## 3. 提交要求

* **一次 commit 只做一件事**
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

---

## 4. 开发流程

推荐流程：

```bash
git checkout dev
git pull origin dev
git checkout -b feature/xxx

git add .
git commit -m "feat(module): xxx"
git push origin feature/xxx
```

开发完成后再发起合并。

---

## 5. 禁止事项

* **禁止直接提交到 `main`**
* **禁止一个 commit 混入多个无关功能**
* **禁止使用无意义提交说明**
* **禁止提交临时代码、调试代码、敏感信息**

---

## 6. 建议

* 小步提交，方便回溯
* 经常同步 `dev`，减少冲突
* 提交信息写清楚，方便组员查看历史

---

## 7. 示例

```bash
feat(user): add user login API
fix(judge): fix submission timeout
docs(readme): add setup guide
```