# 正式 Review 与返工链

仅记录 GitHub API 返回的正式 Review 状态。COMMENTED、Issue 评论、自动审查建议和口头说明均不等同于 CHANGES_REQUESTED。

## 已闭环返工

| 事项 | Request changes | 失败 AC 或事实 | 修复和复测 | 最终 Review 或当前状态 |
| --- | --- | --- | --- | --- |
| #307 / [PR #360](https://github.com/Cr4zyorange/OnlineJudge/pull/360) | [review 5095094290](https://github.com/Cr4zyorange/OnlineJudge/pull/360#pullrequestreview-5095094290)，2026-09-02 21:06:34Z，head 955f0384 | AC-307-01 FAIL：单体 API 可见课程 106、三服务 105；旧窗口无效 | 新 head 7b7aa8adbb5183e39bc9a1534151afd0e32db021；3 API × 2 架构 × 3 轮等于 18 原始样本，21,582/21,582 接受，错误率 0%；perf tests 39/39；321 文件扫描 0 命中 | [review 5096493684](https://github.com/Cr4zyorange/OnlineJudge/pull/360#pullrequestreview-5096493684) APPROVED；合并 c56b16f916b4a4c3d33915aa37beab6b05c72888。PASS。 |
| #340 / [PR #372](https://github.com/Cr4zyorange/OnlineJudge/pull/372) | [review 5091287330](https://github.com/Cr4zyorange/OnlineJudge/pull/372#pullrequestreview-5091287330)，2026-09-02 14:53:59Z，head 9cbd8cda | Token 上传；AC-340-02/04 无 Grade 恢复投影；AC-340-01 无缓存 JWT；AC-340-03 无 fencing 和原始租约信号 | 新 head 055d6e583bf88655201d6fc1d4dde285274809f6；run 33670623143、artifact 9862949952 为 7/7 PASS；50 文件敏感扫描 0 | [review 5094074447](https://github.com/Cr4zyorange/OnlineJudge/pull/372#pullrequestreview-5094074447) APPROVED。PR 未合并，等待 #320 共享修复后的当前 head 全绿：BLOCKED。 |
| #319 / [PR #374](https://github.com/Cr4zyorange/OnlineJudge/pull/374) | [5087374732](https://github.com/Cr4zyorange/OnlineJudge/pull/374#pullrequestreview-5087374732)、[5089048589](https://github.com/Cr4zyorange/OnlineJudge/pull/374#pullrequestreview-5089048589)、[5090675089](https://github.com/Cr4zyorange/OnlineJudge/pull/374#pullrequestreview-5090675089) | AC-319-03/04/05 依次暴露 tested SHA 不可复现、缺停机或投影或 lease 原始信号、runner 与证据 SHA 不一致 | 当前 head 02baf84e3b3565efae3b82ed728602337a0cd143 是 tested runner cf2979dc 的仅文档后继；31,880 HTTP 200、0 错误；HPA 1→3→1；平台测试 55/55 | [review 5093236184](https://github.com/Cr4zyorange/OnlineJudge/pull/374#pullrequestreview-5093236184) APPROVED。PR 未合并，等待 #320 共享质量门：BLOCKED。 |

## 仍未闭环的正式打回

| 事项 | 正式 Review | 当前阻塞 | 状态 |
| --- | --- | --- | --- |
| #320 / [PR #377](https://github.com/Cr4zyorange/OnlineJudge/pull/377) | [review 5097060716](https://github.com/Cr4zyorange/OnlineJudge/pull/377#pullrequestreview-5097060716)，2026-09-03 02:11:01Z，head 653116212e9dff630dfa1ef4f00bde0cda40ec63 | Browser business E2E 首个镜像后失败；Runner 依赖 Docker Scout SBOM，但 job 未安装并校验固定 Scout 版本 | BLOCKED：修复后重跑当前 head 完整 CI，尚无 APPROVED Review。 |

## API 采集计数

| PR | APPROVED | CHANGES_REQUESTED | COMMENTED | DISMISSED |
| --- | ---: | ---: | ---: | ---: |
| #360 | 1 | 1 | 3 | 0 |
| #372 | 1 | 1 | 0 | 0 |
| #374 | 1 | 3 | 4 | 2 |
| #377 | 0 | 1 | 1 | 0 |

每条 Review 的原始查询、命令与环境见 [采集快照](evidence/snapshot.md)。
