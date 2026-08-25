# Issue #269 修复验证报告

## 结论

**PASS**。点击属于当前用户且目标有效的未读通知时，前端先调用统一已读接口，接口成功后再执行站内路由跳转；已读接口失败时留在通知中心并展示错误；同一通知连续点击或再次打开不会重复提交已读变更。

## 基线与环境

- 基线：`origin/dev@758afd98ba2caad5a00fb6e12413c48f0156b2fb`
- 分支：`fix/269-notification-read-on-open`
- 日期：2026-08-25（Asia/Shanghai）
- OS：Microsoft Windows 11 家庭版 中文版 10.0.26200，AMD64
- Java：21.0.11 LTS
- Maven：3.9.16
- Node.js：24.16.0
- npm：11.13.0
- Playwright：1.62.1，Chrome 151.0.7922.138
- 页面入口：本地 `http://127.0.0.1:5173`，Vite 代理 Spring Boot `127.0.0.1:8080`

## RED → GREEN

### RED

命令：

```text
npm run test:unit -- tests/unit/lrn/NotificationCenterView.spec.ts
```

结果：10 总数 / 7 通过 / 3 失败 / 0 错误 / 0 跳过。失败项分别证明当前页面未调用已读接口、已读失败没有反馈、连续点击未受幂等保护。

### GREEN

同一命令结果：10 总数 / 10 通过 / 0 失败 / 0 错误 / 0 跳过。

## 验证统计

| 范围 | 结果 | 总数 | 通过 | 失败 | 错误 | 跳过 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| `NotificationCenterView.spec.ts` | PASS | 10 | 10 | 0 | 0 | 0 |
| 前端 LRN 单元测试 | PASS | 37 | 37 | 0 | 0 | 0 |
| #269 真实 LAB→LRN Playwright | PASS | 1 | 1 | 0 | 0 | 0 |
| 共享 E2E 入口契约 | PASS | 3 | 3 | 0 | 0 | 0 |
| 后端通知归属与 `MARK_READ` 日志 | PASS | 1 | 1 | 0 | 0 | 0 |
| `npm run typecheck` | PASS | 1 | 1 | 0 | 0 | 0 |
| `npm run build` | PASS | 189 modules | 189 | 0 | 0 | 0 |

后端验证命令：

```text
mvn -Dtest=NotificationControllerTest#readAndDeleteActionsAreScopedToCurrentUserAndLogged test
```

真实页面验证完成以下断言：

1. 教师通过真实 LAB 创建和发布接口生成学生通知。
2. 学生点击未读通知后才进入实验详情页。
3. 通知 API 中 `isRead` 由 `false` 变为 `true`，未读数减少 1。
4. 返回通知中心后卡片不再显示“未读”。
5. 再次打开同一通知不重复调用 `PUT /api/v1/notifications/read`。
6. 后端既有控制器测试确认当前用户隔离和 `lrn_notification_status_log.MARK_READ` 留痕。

## 页面证据

- `evidence/notification-read-state.png`：返回通知中心后，目标 Issue 269 LAB 通知已无“未读”标识。
- `evidence/notification-read-on-open.png`：通知已读提交成功后进入真实实验详情页。

Playwright 的临时报告、trace、video、浏览器会话和本地数据库均未提交；证据中不包含密码、Token、Cookie 或真实个人数据。
