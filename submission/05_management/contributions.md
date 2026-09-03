# 六人贡献、建议权重与确认

本表以 2026-08-25 至 2026-09-03 GitHub PR 搜索、Issue/PR 元数据、正式 Review 和当日日报可复核的事项为准。它区分 PR Owner、共同提交者、Reviewer 与集成者；不能由提交数推断最终贡献比例，也不能代替成员确认。

| 成员 | GitHub 标识 | 课程期 PR 索引，数量 | 代表 Issue 到 PR | 角色和 commit 事实 | 可执行或评审证据 | 建议权重 | 本人确认 |
| --- | --- | --- | --- | --- | --- | ---: | --- |
| Ricardo Lee | linkverb0510 | #252/#256/#275/#280/#300/#361/#372/#382，8 | #340 → [PR #372](https://github.com/Cr4zyorange/OnlineJudge/pull/372) | PR Owner；本人 11、Cr4zyorange 8 个提交 | 复审记录 live matrix 7/7 PASS；PR 未合并 | 1.0 | BLOCKED：未取得本人明确确认。 |
| 罗子慧 | luoZiHui-maker | #257/#272/#278/#301/#335/#354/#360，7 | #339 → [PR #354](https://github.com/Cr4zyorange/OnlineJudge/pull/354)，#307 → [PR #360](https://github.com/Cr4zyorange/OnlineJudge/pull/360) | #354 本人 11；#360 本人 15 个提交，与负责人共同修复或集成 | #354 已合并；#360 最终审查通过并合入 dev | 1.0 | BLOCKED：未取得本人明确确认。 |
| wyx | wyx-1236 | #249/#273/#274/#282/#302/#328/#333/#377，8 | #320 → [PR #377](https://github.com/Cr4zyorange/OnlineJudge/pull/377) | PR Owner；本人 13、Cr4zyorange 61 个提交 | 当前为正式 CHANGES_REQUESTED；Browser E2E 缺 Docker Scout | 1.0 | BLOCKED：未取得本人明确确认。 |
| terrana | terrana37 | #276/#285/#299/#303/#331/#352/#359/#374/#376，9 | #319 → [PR #374](https://github.com/Cr4zyorange/OnlineJudge/pull/374) | PR Owner；本人 33、Cr4zyorange 14 个提交 | 当前审查 APPROVED；31,880 个 HTTP 200、0 错误在正式 Review 中；PR 未合并 | 1.0 | BLOCKED：未取得本人明确确认。 |
| lyc | MontesquieuE | #251/#277/#298/#332/#362/#373，6 | #367 → [PR #373](https://github.com/Cr4zyorange/OnlineJudge/pull/373) | PR Owner；本人 10 个提交 | APPROVED，已合并 dev；日报记录接口映射 124/124、未覆盖 0 | 1.0 | BLOCKED：未取得本人明确确认。 |
| 赵贵彬，项目负责人 | Cr4zyorange | #258/#259/#260/#268/#270/#284/#294/#297/#329/#334/#343/#344/#345/#346/#347/#348/#349/#350/#351/#353/#358/#363/#365/#371，24 | #307 → [PR #360](https://github.com/Cr4zyorange/OnlineJudge/pull/360)，#318 → [PR #363](https://github.com/Cr4zyorange/OnlineJudge/pull/363)，#369 进行中 | #360 共同提交 18；#363/#365/#371 分别 3/2/3 个本人提交；统一正式 Review 与集成者 | #360 最终 APPROVED；#363/#365/#371 已合并；#369 尚无 PR 或成员确认 | 1.0 | BLOCKED：Owner 身份不能替代本人确认。 |

## 权重约束检查

- 六项建议值均为 1.0，总和 6.0：PASS。
- 全部处于通常区间 0.9 至 1.1：PASS。
- 没有低于 0.9 的建议值，因而不需要教师或助教特殊权重依据：PASS。
- 建议值不是最终确认或签名：全员确认 0/6，确认门槛为 BLOCKED。

## 角色边界

1. PR Owner 来自 GitHub PR author，不把共同提交者改写为 Owner。
2. CHANGES_REQUESTED 和 APPROVED 只取 GitHub Review 状态；普通 Issue 或 PR 评论不计作正式打回。
3. 可执行证据保留运行范围。例如 #377 处于正式打回，不能因日报中的本地 24/24 直接记入验收完成。
4. 每名成员确认必须包含真实时间、方式、当事人和可访问原件；缺任一项均保留 BLOCKED。
