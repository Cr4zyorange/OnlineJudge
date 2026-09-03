# 原始素材索引

## 候选版彩排母带

- 版本：`candidate-27eab668`
- Git SHA：`27eab66891bfbbc21cb39ec96dcbedd6be2fabe2`
- Kubernetes context：`kind-oj381-rehearsal`
- Namespace：`issue381-rehearsal`
- 性质：彩排原始素材，不是最终 Tag 录屏，不能直接作为正式成片。

| 段落 | 原始文件 | 时长 | 编码 / 尺寸 / 帧率 | SHA-256 | 录屏结果 |
| --- | --- | ---: | --- | --- | --- |
| 1. 核心场景用例 | `raw/candidate-27eab668/01-core-business.webm` | 36.480 s | VP8 / 1600×900 / 25 fps | `250bf8dbbdabbc6c79d730e4cb347e1aad31560fd6abd60ca88c47a06d2977ec` | 学生真实登录，展示课程、作业提交历史 `100` 分、已发布成绩和通知。 |
| 2. Push 触发流水线 | `raw/candidate-27eab668/02-push-pipeline.webm` | 25.800 s | VP8 / 1600×900 / 25 fps | `5bbaf031e7d261634114b58b4fef49ed46538ad54bffaae2dec734d53b3f1cdb` | 真实 Actions run `33712921299`，显示 `Triggered via push`、`dev`、`27eab66`、Success 和六个成功 Job；候选录屏为历史完成 run，正式版需录制本轮触发。 |
| 3. Kubernetes | `raw/candidate-27eab668/03-kubernetes.mov` | 100.015 s | H.264 / 3240×2022 / 120 fps | `0c6e8626717e198c6dac79e111d662780d1af86d76f4fc0b63dcd57d132491ac` | 九个逻辑工作负载、Pod Ready、镜像 SHA、Assessment 探针、资源和真实日志。 |
| 4. HPA 扩缩容 | `raw/candidate-27eab668/04-05-hpa-dependency-stop.mov` | 600.003 s | H.264 / 3156×1938 / 240 fps | `631323f3e47067bed3aaf839968306fbb49713128593f8c0c04bc4122c7790f8` | 同一连续母带：CPU 峰值 `241%/60%`，Assessment Pod `1→3→2→1`，12,384 请求、0 错误、P95 62.827 ms。 |
| 5. 停止依赖服务 | `raw/candidate-27eab668/04-05-hpa-dependency-stop.mov` | 包含于上述 600.003 s 母带 | H.264 / 3156×1938 / 240 fps | `631323f3e47067bed3aaf839968306fbb49713128593f8c0c04bc4122c7790f8` | RabbitMQ `1→0→1`，故障窗口中 Assessment `available=1, ready=1`，恢复后 Pod 与 Endpoint 重新就绪。 |

## HPA / 依赖停止同轮证据

- 原始实验开始：`2026-09-03T07:41:25Z`
- 原始实验结束：`2026-09-03T07:50:17Z`
- RabbitMQ 确认不可用：`2026-09-03T07:41:31Z`
- RabbitMQ 恢复：`2026-09-03T07:41:58Z`
- HPA：`scaled up replicas=3 baseline=1`，`scaled down replicas=1 baseline=1`
- 请求：`12384`，错误：`0`，平均延迟：`21.274 ms`，P95：`62.827 ms`
- 实验结论：`EXPERIMENT_READY`
- 证据目录：`work/candidate-27eab668/hpa-recorded/`

## 正式重录门禁

1. 最终 Tag 必须精确指向录屏 SHA。
2. 五项素材在最终 Tag/SHA 上全部重录，不直接复用本候选母带。
3. 核心场景重新生成唯一演示数据；不使用彩排期重复课程。
4. Push 段录制本轮真实触发到结束；不把历史完成 run 说成“刚刚触发”。
5. 后期对 HPA 稳定窗口进行倍速或跳切时，字幕明示标注，并保留本连续母带供核查。
